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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import java.util.concurrent.Callable;

import java.io.IOException;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;

public class HitRanker implements Callable< ArrayList<SearchHit> > {

  final static public int FCREATED = 0;
  final static public int FMODIFIED = 1;
  final static public int FACCESSED = 2;
  final static public int FAVG_RECENCY = 3;
  final static public int FFILENAME_DIRECT = 4;
  final static public int FFILENAME_INDIRECT = 5;
  final static public int FUSER_DIRECTORY = 6;
  final static public int FHIGH_PRIORITY_TYPE = 7;
  final static public int FMED_PRIORITY_TYPE = 8;
  final static public int FLOW_PRIORITY_TYPE = 9;
  final static public int FTERM_TFIDF = 10;
  final static public int FCOSINE_SIMILARITY = 11;
  final static public int FHIT_FREQUENCY = 12;
  final static public int FHIT_PROXIMITY = 13;
  final static public int FTERM_CARDINALITY = 14;
  final static public int FTERM_LENGTH = 15;
  final static public int FTERM_PRIORITY = 16;
  final static public int FUNUSED = 17;
  final static public int FHIT_OFFSET = 18;

  final private IndexSearcher Searcher;
  final private Query         SearchQuery;
  final private Date          RefDate;
  final int TotalHits;
  final private ArrayList<SearchHit> Hits;

  private TopDocs       Results;

  public HitRanker(final IndexSearcher s, final Query q, final Date refDate, final int totalHits) {
    Searcher = s;
    SearchQuery = q;
    RefDate = refDate;
    TotalHits = totalHits;
    Hits = new ArrayList<SearchHit>(Math.max(TotalHits, 1000));
  }

  public ArrayList<SearchHit> call() throws IOException {
    System.err.println("Ranking individual search hits...");
    Results = Searcher.search(SearchQuery, TotalHits);
    final HitsGetter getter = new HitsGetter(RefDate, Hits, Searcher, SearchQuery);
    final int totalHits = Results.totalHits;
    int pos = 0;
    while (pos < totalHits) {
      getter.highlight("body", SearchQuery, Searcher, Results, 1000000);
      pos += Results.scoreDocs.length;
      Results = Searcher.searchAfter(Results.scoreDocs[Results.scoreDocs.length - 1], SearchQuery, 100);
    }
    getter.normalize();
    Collections.sort(Hits);
    System.err.println("Done ranking individual search hits...");
    return Hits;
  }
}
