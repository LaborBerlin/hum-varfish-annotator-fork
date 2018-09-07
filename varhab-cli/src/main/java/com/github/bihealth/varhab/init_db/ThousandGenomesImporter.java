package com.github.bihealth.varhab.init_db;

import com.github.bihealth.varhab.VarhabException;
import com.github.bihealth.varhab.utils.VariantDescription;
import com.github.bihealth.varhab.utils.VariantNormalizer;
import com.google.common.collect.ImmutableList;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of Thousand Genomes import.
 *
 * <p>The code will also normalize the thousand genomes data per-variant.
 */
public final class ThousandGenomesImporter {

  /** The name of the table in the database. */
  public static final String TABLE_NAME = "thousand_genomes_var";

  /** The population names. */
  public static final ImmutableList<String> popNames = ImmutableList.of("AFR", "AMR", "ASN", "EUR");

  /** The JDBC connection. */
  private final Connection conn;

  /** Path to Thousand Genomes VCF path. */
  private final List<String> vcfPaths;

  /** Helper to use for variant normalization. */
  private final String refFastaPath;

  /**
   * Construct the <tt>ThousandGenomesImporter</tt> object.
   *
   * @param conn Connection to database
   * @param vcfPaths Path to Thousand Genomes VCF path.
   */
  public ThousandGenomesImporter(Connection conn, List<String> vcfPaths, String refFastaPath) {
    this.conn = conn;
    this.vcfPaths = ImmutableList.copyOf(vcfPaths);
    this.refFastaPath = refFastaPath;
  }

  /** Execute Thousand Genomes import. */
  public void run() throws VarhabException {
    System.err.println("Re-creating table in database...");
    recreateTable();

    System.err.println("Importing Thousand Genomes...");
    final VariantNormalizer normalizer = new VariantNormalizer(refFastaPath);
    String prevChr = null;
    for (String vcfPath : vcfPaths) {
      try (VCFFileReader reader = new VCFFileReader(new File(vcfPath), true)) {
        for (VariantContext ctx : reader) {
          if (!ctx.getContig().equals(prevChr)) {
            System.err.println("Now on chrom " + ctx.getContig());
          }
          importVariantContext(normalizer, ctx);
          prevChr = ctx.getContig();
        }
      } catch (SQLException e) {
        throw new VarhabException("Problem with inserting into " + TABLE_NAME + " table", e);
      }
    }

    System.err.println("Done with importing Thousand Genomes...");
  }

  /**
   * Re-create the Thousand Genomes table in the database.
   *
   * <p>After calling this method, the table has been created and is empty.
   */
  private void recreateTable() throws VarhabException {
    final String dropQuery = "DROP TABLE IF EXISTS " + TABLE_NAME;
    try (PreparedStatement stmt = conn.prepareStatement(dropQuery)) {
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new VarhabException("Problem with DROP TABLE statement", e);
    }

    final String createQuery =
        "CREATE TABLE "
            + TABLE_NAME
            + "("
            + "release VARCHAR(10) NOT NULL, "
            + "chrom VARCHAR(20) NOT NULL, "
            + "pos INTEGER NOT NULL, "
            + "pos_end INTEGER NOT NULL, "
            + "ref VARCHAR("
            + InitDb.VARCHAR_LEN
            + ") NOT NULL, "
            + "alt VARCHAR("
            + InitDb.VARCHAR_LEN
            + ") NOT NULL, "
            + "thousand_genomes_hom INTEGER NOT NULL, "
            + "thousand_genomes_het INTEGER NOT NULL, "
            + "thousand_genomes_hemi INTEGER NOT NULL, "
            + "thousand_genomes_af_popmax DOUBLE NOT NULL, "
            + ")";
    try (PreparedStatement stmt = conn.prepareStatement(createQuery)) {
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new VarhabException("Problem with CREATE TABLE statement", e);
    }

    final ImmutableList<String> indexQueries =
        ImmutableList.of(
            "CREATE PRIMARY KEY ON " + TABLE_NAME + " (release, chrom, pos, ref, alt)",
            "CREATE INDEX ON " + TABLE_NAME + " (release, chrom, pos, pos_end)");
    for (String query : indexQueries) {
      try (PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.executeUpdate();
      } catch (SQLException e) {
        throw new VarhabException("Problem with CREATE INDEX statement", e);
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
            + " (release, chrom, pos, pos_end, ref, alt, thousand_genomes_het, "
            + "thousand_genomes_hom, thousand_genomes_hemi, thousand_genomes_af_popmax)"
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

      final PreparedStatement stmt = conn.prepareStatement(insertQuery);
      stmt.setString(1, finalVariant.getChrom());
      stmt.setInt(2, finalVariant.getPos() + 1);
      stmt.setInt(3, finalVariant.getPos() + finalVariant.getRef().length());
      stmt.setString(4, finalVariant.getRef());
      stmt.setString(5, finalVariant.getAlt());

      int het = 0;
      final List<Integer> hets;
      if (numAlleles == 2) {
        hets = ImmutableList.of(ctx.getCommonInfo().getAttributeAsInt("Het", 0));
      } else {
        hets = new ArrayList<>();
        for (String s :
            (List<String>) ctx.getCommonInfo().getAttribute("Het", ImmutableList.<String>of())) {
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
        homs = ImmutableList.of(ctx.getCommonInfo().getAttributeAsInt("Hom", 0));
      } else {
        homs = new ArrayList<>();
        for (String s :
            (List<String>) ctx.getCommonInfo().getAttribute("Hom", ImmutableList.<String>of())) {
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
        hemis = ImmutableList.of(ctx.getCommonInfo().getAttributeAsInt("Hemi", 0));
      } else {
        hemis = new ArrayList<>();
        for (String s :
            (List<String>) ctx.getCommonInfo().getAttribute("Hemi", ImmutableList.<String>of())) {
          hemis.add(Integer.parseInt(s));
        }
      }
      if (hemis.size() >= i) {
        hemi = hemis.get(i - 1);
      }
      stmt.setInt(8, hemi);

      double a = 0.0;
      for (String pop : popNames) {
        final List<Integer> acs;
        if (numAlleles == 2) {
          acs = ImmutableList.of(ctx.getCommonInfo().getAttributeAsInt("AC_" + pop, 0));
        } else {
          acs = new ArrayList<>();
          for (String s :
              (List<String>)
                  ctx.getCommonInfo().getAttribute("AC_" + pop, ImmutableList.<String>of())) {
            acs.add(Integer.parseInt(s));
          }
        }
        final int an = ctx.getCommonInfo().getAttributeAsInt("AN_" + pop, 0);
        if (an > 0 && acs.size() >= i) {
          a = Math.max(a, ((double) acs.get(i - 1)) / ((double) an));
        } else if (an > 0) {
          System.err.println("Warning, could not update AF_POPMAX (" + pop + ") for " + ctx);
        }
      }
      stmt.setDouble(9, a);

      stmt.executeUpdate();
      stmt.close();
    }
  }
}
