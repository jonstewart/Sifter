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

import java.io.InputStreamReader;
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.File;

import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.util.StopwordAnalyzerBase;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import edu.utsa.sifter.som.SifterConfig;

public class Indexer {

  static CharArraySet getStopList(final String path) {
    final CharArraySet list = new CharArraySet(Version.LUCENE_44, 100, true);
    for (Object stop: StandardAnalyzer.STOP_WORDS_SET) {
      list.add(stop);
    }
    final String stopChars = "abcdefghijklmnopqrstuvwxyz0123456789`~!@#$%^&*()-_=+[{]}\\|;:'\",<.>/?";
    for (int i = 0; i < stopChars.length(); ++i) {
      list.add(stopChars.subSequence(i, i + 1));
    }
    if (path != null) {
      try {
        final BufferedReader rdr = new BufferedReader(new FileReader(path));
        String line = rdr.readLine();
        while (line != null) {
          list.add(line);
          line = rdr.readLine();
        }
      }
      catch (IOException ex) {
        System.err.println("Stopwords file " + path + " could not be read");
      }
    }
    System.out.println("Number of stop words = " + list.size());
    return list;
  }

  static IndexWriter getIndexWriter(final String path, final String stopwords, final SifterConfig conf) throws IOException {
    Directory dir = FSDirectory.open(new File(path));

    StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_44, getStopList(stopwords));
    IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_44, analyzer);
    iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
    iwc.setRAMBufferSizeMB(conf.INDEXING_BUFFER_SIZE);
    iwc.setMaxThreadStates(conf.THREAD_POOL_SIZE);
    IndexWriter writer = new IndexWriter(dir, iwc);
    return writer;
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length != 1 && args.length != 2) {
      System.err.println("Wrong number of args supplied. Takes path to index directory and optional stop words file path.");
      return;
    }
    final long begin = System.currentTimeMillis();

    final File evPath     = new File(args[0]);
    final File indexPath  = new File(evPath, "primary-idx");
    if (!evPath.mkdir()) {
      System.err.println("Could not create directory " + evPath.toString());
      return;
    }

    final SifterConfig conf = new SifterConfig();
    conf.loadFromXMLFile("sifter_props.xml");

    FSRipReader ripper = new FSRipReader(conf.THREAD_POOL_SIZE, conf.LARGE_FILE_THRESHOLD, conf.TEMP_DIR, conf.FILETYPE_MODEL_FILE);
    try {
      final IndexWriter index = getIndexWriter(indexPath.toString(), args.length == 2 ? args[1]: null, conf);
      boolean ret = ripper.readData(System.in, index);
      if (ret) {
        System.out.println("Optimizing index");
        index.forceMerge(1);
        System.out.println("Successful finish");
      }
      index.close();
    }
    finally {
      System.out.println("FilesRead: " + ripper.FilesRead);
      System.out.println("BytesRead: " + ripper.BytesRead);
      System.out.println("FileBytesRead: " + ripper.FileBytesRead);
      System.out.println("Duration: " + ((System.currentTimeMillis() - begin) / 1000) + " seconds");
    }
  }
}
