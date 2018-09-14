package com.github.bihealth.varhab.init_db;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import java.util.List;

/**
 * JCommander command for <tt>varhab init-db</tt>.
 *
 * @author <a href="mailto:manuel.holtgrewe@bihealth.de">Manuel Holtgrewe</a>
 */
@Parameters(commandDescription = "Initialize or update DB")
public final class InitDbArgs {

  @Parameter(names = "--help", help = true)
  private boolean help = false;

  @Parameter(
      names = "--db-path",
      description = "Path to H2 file to initialize/update",
      required = true)
  private String dbPath;

  @Parameter(
      names = "--ref-path",
      description = "Path to reference FASTA file, used for variant normalization",
      required = true)
  private String refPath;

  @Parameter(
      names = "--exac-path",
      description =
          "Path to ExAC VCF file to use for import, see documentation for more information")
  private String exacPath;

  @Parameter(
      names = "--gnomad-exomes-path",
      description =
          "Path to gnomAD exomes VCF file to use for import, see documentation for more information")
  private String gnomadExomesPath;

  @Parameter(
      names = "--gnomad-genomes-path",
      description =
          "Path to gnomAD genomes VCF file to use for import, see documentation for more information")
  private String gnomadGenomesPath;

  @Parameter(
      names = "--thousand-genomes-path",
      description =
          "Path to 1000 genomes VCF file to use for import, see documentation for more information")
  private List<String> thousandGenomesPaths;

  @Parameter(
      names = "--clinvar-path",
      description =
          "Path to Clinvar TSV file(s) to use for import, see documentation for more information")
  private List<String> clinvarPaths;

  public boolean isHelp() {
    return help;
  }

  public String getRefPath() {
    return refPath;
  }

  public String getDbPath() {
    return dbPath;
  }

  public String getExacPath() {
    return exacPath;
  }

  public String getGnomadExomesPath() {
    return gnomadExomesPath;
  }

  public String getGnomadGenomesPath() {
    return gnomadGenomesPath;
  }

  public List<String> getThousandGenomesPaths() {
    return thousandGenomesPaths;
  }

  public List<String> getClinvarPaths() {
    return clinvarPaths;
  }

  @Override
  public String toString() {
    return "InitDbArgs{"
        + "help="
        + help
        + ", dbPath='"
        + dbPath
        + '\''
        + ", refPath='"
        + refPath
        + '\''
        + ", exacPath='"
        + exacPath
        + '\''
        + ", gnomadExomesPath='"
        + gnomadExomesPath
        + '\''
        + ", gnomadGenomesPath='"
        + gnomadGenomesPath
        + '\''
        + ", thousandGenomesPaths="
        + thousandGenomesPaths
        + ", clinvarPaths="
        + clinvarPaths
        + '}';
  }
}
