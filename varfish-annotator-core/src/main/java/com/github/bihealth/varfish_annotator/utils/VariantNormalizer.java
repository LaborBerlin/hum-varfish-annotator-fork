package com.github.bihealth.varfish_annotator.utils;

import com.github.bihealth.varfish_annotator.VarfishAnnotatorException;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * Helper class for normalizing two variants
 *
 * <p>This is necessary for indel realignment. More information can be found in the <a
 * href="http://genome.sph.umich.edu/wiki/Variant_Normalization">vt documentation</a> and in the
 * following paper:
 *
 * <p>Tan, Adrian, Gonçalo R. Abecasis, and Hyun Min Kang. "Unified representation of genetic
 * variants." Bioinformatics (2015): btv112.
 *
 * @author <a href="mailto:manuel.holtgrewe@bihealth.de">Manuel Holtgrewe</a>
 */
public final class VariantNormalizer {

  /** Path to indexed FASTA path to use */
  final String fastaPath;
  /** Random access in FASTA files using FAI */
  final IndexedFastaSequenceFile fai;

  /**
   * Construct new variant normalizer object
   *
   * @param fastaPath Path to indexed FASTA file
   * @throws VarfishAnnotatorException On problems with opening the FASTA/FAI file
   */
  public VariantNormalizer(String fastaPath) throws VarfishAnnotatorException {
    this.fastaPath = fastaPath;
    try {
      this.fai = new IndexedFastaSequenceFile(new File(fastaPath));
    } catch (FileNotFoundException e) {
      throw new VarfishAnnotatorException("Could not find FASTA/FAI file", e);
    }
  }

  /**
   * Normalize a variant given as a start coordinate, reference, and variant sequence
   *
   * <p>The chromosome is given by its name, position is an 0-based integer, reference and variant
   * are given as sequence.
   */
  public VariantDescription normalizeVariant(VariantDescription desc) {
    final VariantDescription shifted = shiftLeft(desc);
    return trimBasesLeft(shifted, 0);
  }

  /**
   * Normalize a variant given as a start coordinate, reference, and variant sequence
   *
   * <p>However, leave the leftmost base intact in the case so insertions have a REF base.
   *
   * <p>The chromosome is given by its name, position is an 0-based integer, reference and variant
   * are given as sequence.
   */
  public VariantDescription normalizeInsertion(VariantDescription desc) {
    final VariantDescription shifted = shiftLeft(desc);
    return trimBasesLeft(shifted, 1);
  }

  private VariantDescription shiftLeft(VariantDescription desc) {
    int start = desc.getPos();
    String ref = desc.getRef();
    String alt = desc.getAlt();

    boolean anyChange = true;
    while (anyChange) {
      anyChange = false;

      // Trim right-most nucletoide
      if (ref.length() > 0
          && alt.length() > 0
          && ref.charAt(ref.length() - 1) == alt.charAt(alt.length() - 1)) {
        ref = ref.substring(0, ref.length() - 1);
        alt = alt.substring(0, alt.length() - 1);
        anyChange = true;
      }
      // Extend alleles to the left if there is an empty allele
      if (ref.length() == 0 || alt.length() == 0) {
        char extension = (char) fai.getSubsequenceAt(desc.getChrom(), start, start).getBases()[0];
        ref = extension + ref;
        alt = extension + alt;
        start -= 1;
        anyChange = true;
      }
    }

    return new VariantDescription(desc.getChrom(), start, ref, alt);
  }

  private VariantDescription trimBasesLeft(VariantDescription desc, int minSize) {
    int start = desc.getPos();
    String ref = desc.getRef();
    String alt = desc.getAlt();

    // Trim left bases
    while (true) {
      if (ref.length() > minSize && alt.length() > minSize && ref.charAt(0) == alt.charAt(0)) {
        ref = ref.substring(1);
        alt = alt.substring(1);
        start += 1;
      } else {
        break;
      }
    }

    return new VariantDescription(desc.getChrom(), start, ref, alt);
  }
}
