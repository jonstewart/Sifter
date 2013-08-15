package edu.utsa.sifter;

import de.bwaldvogel.liblinear.*;

import java.io.File;
import java.io.IOException;

public class Sceadan {

  final Model FileClassifier;
  final int[] FeatureCounts  = new int[65792];

  public Sceadan(final String modelPath) throws IOException {
    FileClassifier = Linear.loadModel(new File(modelPath));
  }

  void reset() {
    // zero the counts
    for (int i = 0; i < FeatureCounts.length; ++i) {
      FeatureCounts[i] = 0;
    }
  }

  public double classifyBlock(byte[] block) {
    reset();

    int unigram,
        prevUnigram = 0;
    int bigram;

    for (int i = 0; i < block.length; ++i) {
      // all integer types in Java are signed, so we do a little math
      // to convert bytes into their unsigned integer values

      unigram = block[i] < 0 ? (-1 * block[i]) + 127: block[i];

      bigram = (prevUnigram * 256) + unigram;

      ++FeatureCounts[bigram];
      ++FeatureCounts[unigram + 65536];

      prevUnigram = unigram;
    }
    return 0.0;
//    return Linear.predict(Model, FeatureCounts);
  }

  public static void main(String[] args) throws IOException {
    final Sceadan scea = new Sceadan(args[0]);
    scea.reset();
    System.err.println("Loaded model successfully");
  }
}
