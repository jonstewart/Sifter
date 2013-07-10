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

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;

public class DocIndexer implements Runnable {

  final private Document Doc;
  final private IndexWriter Index;

  public DocIndexer(Document doc, IndexWriter index) {
    Doc = doc;
    Index = index;
  }
  
  public void run() {
    String name = Doc.get("name");
    try {
      // System.out.println("Indexing " + name);
      Index.addDocument(Doc);
      // System.out.println("Done indexing " + name);
    }
    catch (IOException ex) {
      System.err.println("IOException when indexing " + name);
      throw new RuntimeException(ex);
    }
  }
}
