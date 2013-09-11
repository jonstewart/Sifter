package edu.utsa.sifter;

import de.bwaldvogel.liblinear.*;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;

public class Sceadan {

  final static private String[] FileTypes = {
    "UNKNOWN",
    "txt",
    "csv",
    "log",
    "html",
    "xml",
    "json",
    "js",
    "java",
    "css",
    "b64",
    "a85",
    "b16",
    "url",
    "ps",
    "rtf",
    "tbird",
    "pst",
    "png",
    "gif",
    "tif",
    "jb2",
    "gz",
    "zip",
    "bz2",
    "pdf",
    "docx",
    "xlsx",
    "pptx",
    "jpg",
    "mp3",
    "m4a",
    "mp4",
    "avi",
    "wmv",
    "flv",
    "swf",
    "wav",
    "mov",
    "doc",
    "xls",
    "ppt",
    "fat",
    "ntfs",
    "ext3",
    "exe",
    "dll",
    "elf",
    "bmp"
  };

  final Model FileClassifier;
  final FeatureNode[] FeatureCounts  = new FeatureNode[256 + (256 * 256)];

  // FeatureNode indices seem to start at 1, not 0

  public Sceadan(final String modelPath) throws IOException {
    FileClassifier = Linear.loadModel(new File(modelPath));

    for (int i = 0; i < FeatureCounts.length; ++i) {
      FeatureCounts[i] = new FeatureNode(i + 1, 0.0);
    }
  }

  void reset() {
    // zero the counts
    for (int i = 0; i < FeatureCounts.length; ++i) {
      FeatureCounts[i].setValue(0.0);
    }
  }

  public int classify(final InputStream input) throws IOException {
    try {
      reset();
      input.mark(128 * 1024);

      int unigram,
          prevUnigram = 0;
      int bigram;
      int numBytes = 0;
      int curByte = input.read();
      while (curByte != -1) {
        unigram = curByte;
        ++FeatureCounts[unigram].value;

        if (numBytes > 0) {
          bigram = (prevUnigram * 256) + unigram;
          ++FeatureCounts[256 + bigram].value;
        }
        prevUnigram = unigram;

        curByte = input.read();
        ++numBytes;
      }
      input.reset();
      if (numBytes == 0) {
        return 0;
      }
      double result = Linear.predict(FileClassifier, FeatureCounts);
      return (int)Math.round(result);
    }
    catch (ArrayIndexOutOfBoundsException ex) {
      ex.printStackTrace(System.err);
      throw ex;
    }
  }

  public String fileExt(final int classification) {
    return (0 < classification && classification < FileTypes.length) ? FileTypes[classification]: "";
  }

  public static void main(String[] args) throws IOException {
    final Sceadan scea = new Sceadan(args[0]);
    scea.reset();
    System.err.println("Loaded model successfully");
  }
}
