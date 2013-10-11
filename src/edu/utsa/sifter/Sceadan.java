/**
 *
 * Sifter - Search Indexes for Text Evidence Relevantly
 *
 * Copyright (C) 2013, University of Texas at San Antonio (UTSA)
 *
 * Sifter is a digital forensics and e-discovery tool for conducting
 * text based string searches.  It clusters and ranks search hits
 * to improve investigative efficiency. Hit-level ranking uses a 
 * patent-pending ranking algorithm invented by Dr. Nicole Beebe at UTSA.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Jon Stewart, Lightbox Technologies
**/

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
