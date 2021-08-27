package com.github.bihealth.varfish_annotator.init_db;

import com.github.bihealth.varfish_annotator.VarfishAnnotatorException;
import com.github.bihealth.varfish_annotator.utils.VariantDescription;
import com.github.bihealth.varfish_annotator.utils.VariantNormalizer;
import com.google.common.collect.ImmutableList;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of ExAC import.
 *
 * <p>The code will also normalize the ExAC data per-variant.
 */
public final class ExacImporter {

  /** The name of the table in the database. */
  public static final String TABLE_NAME = "exac_var";

  /** The population names. */
  public static final ImmutableList<String> popNames =
      ImmutableList.of("AFR", "AMR", "EAS", "FIN", "NFE", "OTH", "SAS");

  /** The JDBC connection. */
  private final Connection conn;

  /** Path to ExAC VCF path. */
  private final String vcfPath;

  /** Helper to use for variant normalization. */
  private final String refFastaPath;

  /** Chromosome of selected region. */
  private final String chrom;

  /** 1-based start position of selected region. */
  private final int start;

  /** 1-based end position of selected region. */
  private final int end;

  /**
   * Construct the <tt>ExacImporter</tt> object.
   *
   * @param conn Connection to database
   * @param vcfPath Path to ExAC VCF path.
   * @param genomicRegion Genomic region {@code CHR:START-END} to process.
   */
  public ExacImporter(Connection conn, String vcfPath, String refFastaPath, String genomicRegion) {
    this.conn = conn;
    this.vcfPath = vcfPath;
    this.refFastaPath = refFastaPath;

    if (genomicRegion == null) {
      this.chrom = null;
      this.start = -1;
      this.end = -1;
    } else {
      this.chrom = genomicRegion.split(":", 2)[0];
      this.start = Integer.parseInt(genomicRegion.split(":", 2)[1].split("-")[0].replace(",", ""));
      this.end = Integer.parseInt(genomicRegion.split(":", 2)[1].split("-")[1].replace(",", ""));
    }
  }

  /** Execute ExAC import. */
  public void run() throws VarfishAnnotatorException {
    System.err.println("Re-creating table in database...");
    recreateTable();

    System.err.println("Importing ExAC...");
    final VariantNormalizer normalizer = new VariantNormalizer(refFastaPath);
    String prevChr = null;
    try (VCFFileReader reader = new VCFFileReader(new File(vcfPath), true)) {
      final CloseableIterator<VariantContext> it;
      if (this.chrom != null) {
        it = reader.query(this.chrom, this.start, this.end);
      } else {
        it = reader.iterator();
      }

      while (it.hasNext()) {
        final VariantContext ctx = it.next();
        if (!ctx.getContig().equals(prevChr)) {
          System.err.println("Now on chrom " + ctx.getContig());
        }
        // System.err.println(ctx.toString());
        try {
          importVariantContext(normalizer, ctx);
        } catch (SQLException e) {
          it.close();
          throw e;
        }
        prevChr = ctx.getContig();
      }
      it.close();
    } catch (SQLException e) {
      throw new VarfishAnnotatorException("Problem with inserting into exac_vars table", e);
    }

    System.err.println("Done with importing ExAC...");
  }

  /**
   * Re-create the ExAC table in the database.
   *
   * <p>After calling this method, the table has been created and is empty.
   */
  private void recreateTable() throws VarfishAnnotatorException {
    final String dropQuery = "DROP TABLE IF EXISTS " + TABLE_NAME;
    try (PreparedStatement stmt = conn.prepareStatement(dropQuery)) {
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new VarfishAnnotatorException("Problem with DROP TABLE statement", e);
    }

    final String createQuery =
        "CREATE TABLE "
            + TABLE_NAME
            + "("
            + "release VARCHAR(10) NOT NULL, "
            + "chrom VARCHAR(20) NOT NULL, "
            + "start INTEGER NOT NULL, "
            + "end INTEGER NOT NULL, "
            + "ref VARCHAR("
            + InitDb.VARCHAR_LEN
            + ") NOT NULL, "
            + "alt VARCHAR("
            + InitDb.VARCHAR_LEN
            + ") NOT NULL, "
            + "exac_het INTEGER NOT NULL, "
            + "exac_hom INTEGER NOT NULL, "
            + "exac_hemi INTEGER NOT NULL, "
            + "exac_af DOUBLE NOT NULL, "
            + ")";
    try (PreparedStatement stmt = conn.prepareStatement(createQuery)) {
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new VarfishAnnotatorException("Problem with CREATE TABLE statement", e);
    }

    final ImmutableList<String> indexQueries =
        ImmutableList.of(
            "CREATE PRIMARY KEY ON " + TABLE_NAME + " (release, chrom, start, ref, alt)",
            "CREATE INDEX ON " + TABLE_NAME + " (release, chrom, start, end)");
    for (String query : indexQueries) {
      try (PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.executeUpdate();
      } catch (SQLException e) {
        throw new VarfishAnnotatorException("Problem with CREATE INDEX statement", e);
      }
    }
  }

  /** Insert the data from <tt>ctx</tt> into the database. */
  @SuppressWarnings("unchecked")
  private void importVariantContext(VariantNormalizer normalizer, VariantContext ctx)
      throws SQLException {
    final String insertQuery =
        "MERGE INTO "
            + TABLE_NAME
            + " (release, chrom, start, end, ref, alt, exac_het, exac_hom, exac_hemi, exac_af)"
            + " VALUES ('GRCh37', ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    final int numAlleles = ctx.getAlleles().size();
    for (int i = 1; i < numAlleles; ++i) {
      final VariantDescription rawVariant =
          new VariantDescription(
              ctx.getContig(),
              ctx.getStart() - 1,
              ctx.getReference().getBaseString(),
              ctx.getAlleles().get(i).getBaseString());
      final VariantDescription finalVariant = normalizer.normalizeInsertion(rawVariant);

      // Skip if too long REF or ALT allele.
      if (finalVariant.getRef().length() > InitDb.VARCHAR_LEN) {
        System.err.println(
            "Skipping variant at "
                + ctx.getContig()
                + ":"
                + ctx.getStart()
                + " length = "
                + finalVariant.getRef().length());
        continue;
      }
      if (finalVariant.getAlt().length() > InitDb.VARCHAR_LEN) {
        System.err.println(
            "Skipping variant at "
                + ctx.getContig()
                + ":"
                + ctx.getStart()
                + " length = "
                + finalVariant.getAlt().length());
        continue;
      }

      final PreparedStatement stmt = conn.prepareStatement(insertQuery);
      stmt.setString(1, finalVariant.getChrom());
      stmt.setInt(2, finalVariant.getPos() + 1);
      stmt.setInt(3, finalVariant.getPos() + finalVariant.getRef().length());
      stmt.setString(4, finalVariant.getRef());
      stmt.setString(5, finalVariant.getAlt());

      int het = 0;
      final List<Integer> hets;
      if (numAlleles == 2) {
        hets = ImmutableList.of(ctx.getCommonInfo().getAttributeAsInt("AC_Het", 0));
      } else {
        hets = new ArrayList<>();
        for (String s :
            (List<String>) ctx.getCommonInfo().getAttribute("AC_Het", ImmutableList.<String>of())) {
          hets.add(Integer.parseInt(s));
        }
      }
      if (hets.size() >= i) {
        het = hets.get(i - 1);
      }
      stmt.setInt(6, het);

      int hom = 0;
      final List<Integer> homs;
      if (numAlleles == 2) {
        homs = ImmutableList.of(ctx.getCommonInfo().getAttributeAsInt("AC_Hom", 0));
      } else {
        homs = new ArrayList<>();
        for (String s :
            (List<String>) ctx.getCommonInfo().getAttribute("AC_Hom", ImmutableList.<String>of())) {
          homs.add(Integer.parseInt(s));
        }
      }
      if (homs.size() >= i) {
        hom = homs.get(i - 1);
      }
      stmt.setInt(7, hom);

      int hemi = 0;
      final List<Integer> hemis;
      if (numAlleles == 2) {
        hemis = ImmutableList.of(ctx.getCommonInfo().getAttributeAsInt("AC_Hemi", 0));
      } else {
        hemis = new ArrayList<>();
        for (String s :
            (List<String>)
                ctx.getCommonInfo().getAttribute("AC_Hemi", ImmutableList.<String>of())) {
          hemis.add(Integer.parseInt(s));
        }
      }
      if (hemis.size() >= i) {
        hemi = hemis.get(i - 1);
      }
      stmt.setInt(8, hemi);

      double af = 0.0;
      final List<Double> afs;
      if (numAlleles == 2) {
        afs = ImmutableList.of(ctx.getCommonInfo().getAttributeAsDouble("AF", 0.0));
      } else {
        afs = new ArrayList<>();
        for (String s :
            (List<String>) ctx.getCommonInfo().getAttribute("AF", ImmutableList.<String>of())) {
          afs.add(Double.parseDouble(s));
        }
      }
      if (afs.size() >= i) {
        af = afs.get(i - 1);
      }
      stmt.setDouble(9, af);

      stmt.executeUpdate();
      stmt.close();
    }
  }
}
