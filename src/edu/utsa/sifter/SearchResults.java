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

import java.util.UUID;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class SearchResults {
  final private IndexSearcher Searcher; 
  final private Query SearchQuery;

  private TopDocs Docs;

  final public String IndexID;
  final public String Id;
  final public int    TotalHits;

  private int StartDocIndex;

  final private int TOP_DOCS_WINDOW = 500;

  final private HashSet<String> FieldsToLoad = new HashSet<String>();

  final private ExecutorService   Worker;
  final private Future< ArrayList<SearchHit> > SearchHitsFuture;

  private ArrayList<SearchHit> SearchHits = null;

  SearchResults(final String indexID, final IndexSearcher s, final Query q, final Date refDate, final boolean bodyField, final boolean hits) throws IOException {
    IndexID = indexID;

    Searcher = s;
    SearchQuery = q;
    Id = UUID.randomUUID().toString();
    Docs = Searcher.search(SearchQuery, 100);
    TotalHits = Docs.totalHits;

    FieldsToLoad.add("ID");
    FieldsToLoad.add("name");
    FieldsToLoad.add("extension");
    FieldsToLoad.add("path");
    FieldsToLoad.add("size");
    FieldsToLoad.add("modified");
    FieldsToLoad.add("accessed");
    FieldsToLoad.add("created");
    FieldsToLoad.add("cell");
    FieldsToLoad.add("som-cell-distance");
    FieldsToLoad.add("body-len");
    if (bodyField) {
      FieldsToLoad.add("body");
    }
    if (hits) {
      Worker = Executors.newCachedThreadPool();
      System.err.println("Submitting task for search hit ranking");

      SearchHitsFuture = Worker.submit(new HitRanker(s, q, Docs, refDate, TotalHits));
      System.err.println("Submitted task for search hit ranking");
    }
    else {
      Worker = null;
      SearchHitsFuture = null;
    }
  }

  public SearchInfo getInfo() {
    return new SearchInfo(Id, TotalHits);
  }

  void ensureTopDocs(final int rank) throws IOException {
    if (StartDocIndex > rank) {
      Docs = Searcher.search(SearchQuery, TOP_DOCS_WINDOW);
      StartDocIndex = 0;
    }
    int len = Docs.scoreDocs.length;
    while (StartDocIndex + len <= rank) {
      StartDocIndex += len;
      Docs = Searcher.searchAfter(Docs.scoreDocs[len - 1], SearchQuery, TOP_DOCS_WINDOW);
      len = Docs.scoreDocs.length;
    }
  }

  public Result retrieve(final int rank) throws IOException {
    if (0 <= rank && rank < TotalHits) {
      ensureTopDocs(rank);
      final ScoreDoc sdoc = Docs.scoreDocs[rank - StartDocIndex];
      return getResult(sdoc.doc, sdoc.score);
    }
    else {
      System.err.println("request for result " + rank + " out of bounds, TotalHits = " + TotalHits);
      return null;
    }
  }

  public ArrayList<Result> retrieve(final int start, int end) throws IOException {
    if (0 <= start && start <= end && start <= TotalHits) {
      end = Math.min(end, TotalHits);
      final ArrayList<Result> list = new ArrayList();
      for (int i = start; i < end; ++i) {
        list.add(retrieve(i));
      }
      return list;
    }
    else {
      System.err.println("request for result (" + start + ", " + end + ") out of bounds, TotalHits = " + TotalHits);
      return null;
    }
  }

  public Result getResult(final int id, final float score) throws IOException {
    // System.out.println("asking searcher for id " + id);
    final Document doc = Searcher.document(id, FieldsToLoad);
    if (doc != null) {
      return new Result(doc, id, score);
    }
    return null;
  }

  public ArrayList<SearchHit> getSearchHits() throws InterruptedException, ExecutionException {
    if (SearchHits == null) {
      SearchHits = SearchHitsFuture.get();
    }
    return SearchHits;
  }
}
