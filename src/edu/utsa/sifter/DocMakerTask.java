/**
 *
 * SIFTER
 * Copyright (C) 2013, University of Texas-San Antonio
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
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
