package com.github.bihealth.varhab.init_db;

import com.github.bihealth.varhab.VarhabException;
import com.google.common.collect.ImmutableList;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Implementation of importing ClinVar to database.
 *
 * <p>ClinVar is imported from the MacArthur TSV files which we assume to be already properly
 * normalized.
 */
public class ClinvarImporter {

  /** The name of the table in the database. */
  public static final String TABLE_NAME = "clinvar_var";

  /** The expected TSV header. */
  public static ImmutableList<String> EXPECTED_HEADER =
      ImmutableList.of(
          "chrom",
          "pos",
          "ref",
          "alt",
          "start",
          "stop",
          "strand",
          "variation_type",
          "variation_id",
          "rcv",
          "scv",
          "allele_id",
          "symbol",
          "hgvs_c",
          "hgvs_p",
          "molecular_consequence",
          "clinical_significance",
          "clinical_significance_ordered",
          "pathogenic",
          "likely_pathogenic",
          "uncertain_significance",
          "likely_benign",
          "benign",
          "review_status",
          "review_status_ordered",
          "last_evaluated",
          "all_submitters",
          "submitters_ordered",
          "all_traits",
          "all_pmids",
          "inheritance_modes",
          "age_of_onset",
          "prevalence",
          "disease_mechanism",
          "origin",
          "xrefs",
          "dates_ordered");

  /** The JDBC connection. */
  private final Connection conn;

  /** Path to ClinVar TSV files */
  private final ImmutableList<String> clinvarTsvFiles;

  /**
   * Construct the <tt>ClinvarImporter</tt> object.
   *
   * @param conn Connection to database
   * @param clinvarTsvFiles Paths to ClinVar TSV files from MacArthur repository
   */
  public ClinvarImporter(Connection conn, List<String> clinvarTsvFiles) {
    this.conn = conn;
    this.clinvarTsvFiles = ImmutableList.copyOf(clinvarTsvFiles);
  }

  /** Execute Clinvar import. */
  public void run() throws VarhabException {
    System.err.println("Re-creating table in database...");
    recreateTable();

    System.err.println("Importing ClinVar...");

    for (String path : clinvarTsvFiles) {
      importTsvFile(path);
    }

    System.err.println("Done with importing ClinVar...");
  }

  /**
   * Re-create the ClinVar table in the database.
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
            + "chrom VARCHAR(20) NOT NULL, "
            + "pos INTEGER NOT NULL, "
            + "pos_end INTEGER NOT NULL, "
            + "ref VARCHAR(500) NOT NULL, "
            + "alt VARCHAR(500) NOT NULL, "
            + ")";
    try (PreparedStatement stmt = conn.prepareStatement(createQuery)) {
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new VarhabException("Problem with CREATE TABLE statement", e);
    }

    final ImmutableList<String> indexQueries =
        ImmutableList.of(
            "CREATE INDEX ON " + TABLE_NAME + "(chrom, pos, ref, alt)",
            "CREATE INDEX ON " + TABLE_NAME + "(chrom, pos, pos_end)");
    for (String query : indexQueries) {
      try (PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.executeUpdate();
      } catch (SQLException e) {
        throw new VarhabException("Problem with CREATE INDEX statement", e);
      }
    }
  }

  /** Import a ClinVar TSV file from the MacArthur repository. */
  private void importTsvFile(String pathTsvFile) throws VarhabException {
    System.err.println("Importing TSV: " + pathTsvFile);

    final String insertQuery =
        "INSERT INTO "
            + TABLE_NAME
            + " (chrom, pos, pos_end, ref, alt)"
            + " VALUES (?, ?, ?, ?, ?)";

    String line = null;
    String headerLine = null;
    try (InputStream fileStream = new FileInputStream(pathTsvFile);
        InputStream gzipStream =
            pathTsvFile.endsWith(".gz") ? new GZIPInputStream(fileStream) : fileStream;
        Reader decoder = new InputStreamReader(gzipStream, StandardCharsets.UTF_8);
        BufferedReader buffered = new BufferedReader(decoder)) {
      while ((line = buffered.readLine()) != null) {
        final ImmutableList<String> arr = ImmutableList.copyOf(line.split("\t"));
        if (headerLine == null) {
          headerLine = line;
          if (!arr.equals(EXPECTED_HEADER)) {
            throw new VarhabException(
                "Unexpected header records: " + arr + ", expected: " + EXPECTED_HEADER);
          }
        } else {
          final PreparedStatement stmt = conn.prepareStatement(insertQuery);
          stmt.setString(1, arr.get(0));
          stmt.setInt(2, Integer.parseInt(arr.get(4)) - 1);
          stmt.setInt(3, Integer.parseInt(arr.get(5)));
          stmt.setString(4, arr.get(2));
          stmt.setString(5, arr.get(3));
          stmt.executeUpdate();
          stmt.close();
        }
      }
    } catch (IOException e) {
      throw new VarhabException("Problem reading gziped TSV file", e);
    } catch (SQLException e) {
      throw new VarhabException("Problem updating database", e);
    }
  }
}
