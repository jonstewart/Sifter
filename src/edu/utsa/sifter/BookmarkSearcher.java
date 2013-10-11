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

import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;

import org.apache.lucene.document.Document;

import java.io.IOException;

import java.util.ArrayList;
import java.util.HashSet;

public class BookmarkSearcher {
  final private IndexSearcher Searcher; 
  final private HashSet<String> FieldsToLoad = new HashSet<String>();

  final private static int MAX_RESULTS = 1000;

  private Query SearchQuery;
  private TopDocs Docs;


  BookmarkSearcher(final IndexSearcher s, final Query q) throws IOException {
    Searcher = s;
    if (q != null) {
      executeQuery(q);
    }
    FieldsToLoad.add("Comment");
    FieldsToLoad.add("Created");
    FieldsToLoad.add("Docs");
  }

  public void executeQuery(final Query q) throws IOException {
    SearchQuery = q;
    Docs = Searcher.search(SearchQuery, MAX_RESULTS);
  }

  public ArrayList<Bookmark> retrieve() throws IOException {
    if (Docs.totalHits > 0) {
      final ArrayList<Bookmark> list = new ArrayList();
      for (int i = 0; i < Docs.totalHits; ++i) {
        list.add(getResult(Docs.scoreDocs[i].doc));
      }
      return list;
    }
    else {
      return null;
    }
  }

  public Bookmark getResult(final int id) throws IOException {
    final Document doc = Searcher.document(id, FieldsToLoad);
    if (doc != null) {
      return new Bookmark(doc);
    }
    return null;
  }
}
