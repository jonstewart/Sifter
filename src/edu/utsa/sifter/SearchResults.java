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

import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;

import org.apache.lucene.document.Document;

import org.codehaus.jackson.annotate.JsonProperty;

import java.io.IOException;

import java.util.UUID;
import java.util.ArrayList;
import java.util.HashSet;

public class SearchResults {
  final private IndexSearcher Searcher; 
  final private Query SearchQuery;

  private TopDocs Docs;

  @JsonProperty
  final public String Id;

  @JsonProperty
  final public int    TotalHits;

  private int StartDocIndex;

  final private int TOP_DOCS_WINDOW = 500;

  final private HashSet<String> FieldsToLoad = new HashSet<String>();

  SearchResults(final IndexSearcher s, final Query q, final boolean bodyField) throws IOException {
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
    if (bodyField) {
      FieldsToLoad.add("body");
    }
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
      return new Result(doc, score);
    }
    return null;
  }
}
