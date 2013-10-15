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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

import java.io.IOException;

import org.apache.lucene.index.IndexWriter;

import org.apache.lucene.document.Document;

import org.apache.lucene.analysis.Analyzer;

import org.apache.tika.parser.AutoDetectParser;

public class DocMakerTask implements Runnable {
  private final FileInfo Cur;
  private final IndexWriter Index;
  private final AutoDetectParser Tika;
  private final Sceadan Classifier;
  private final ForRealBlockingQueue<byte[]> BufferQueue;

  public DocMakerTask(final FileInfo curFile, final IndexWriter index,
                      final AutoDetectParser tika, final Sceadan sc, final ForRealBlockingQueue<byte[]> bufQueue)
  {
    Cur = curFile;
    Index = index;
    Tika = tika;
    Classifier = sc;
    BufferQueue = bufQueue;
  }

  public void run() {
    try {
      Cur.init(); // read JSON
      if (Cur.isUnallocated()) {
        final int typeResult = Classifier.classify(Cur.Data);
        Cur.setExtension(Classifier.fileExt(typeResult));
      }
      final Analyzer a = Index.getAnalyzer();
      // System.out.println("Processing " + Cur.fullPath());
      Document doc = Cur.generateDoc(Tika, a);
      if (doc != null) {
        Index.addDocument(doc);
      }
      if (Cur.hasSlack()) {
        doc = Cur.generateSlackDoc(Tika, a);
        if (doc != null) {
          Index.addDocument(doc);
        }
      }
    }
    catch (org.codehaus.jackson.JsonParseException e) {
      System.err.println("Exception parsing json: " + e.toString());
      e.printStackTrace(System.err);
    }
    catch (Exception ex) {
      ex.printStackTrace(System.err);
      throw new RuntimeException(ex);
    }
    finally {
      try {
        if (Cur.Data instanceof PublicByteArrayInputStream) {
          final PublicByteArrayInputStream byteStream = (PublicByteArrayInputStream)Cur.Data;
          BufferQueue.put(byteStream.getArray()); // reduce, reuse, recycle
        }
        Cur.close();
      }
      catch (InterruptedException ex) {
        System.err.println("Exception returning byte array to pool, could cause starvation: " + ex.toString());
        ex.printStackTrace(System.err);
      }
      catch (IOException ex) {
        System.err.println("IOException closing stream: " + ex.toString());
        ex.printStackTrace(System.err);
      }
    }
  }
}
