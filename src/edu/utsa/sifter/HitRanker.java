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
  final private TopDocs       Results;
  final private Date          RefDate;
  final private ArrayList<SearchHit> Hits;

  public HitRanker(final IndexSearcher s, final Query q, final TopDocs results, final Date refDate, final int minHits) {
    Searcher = s;
    SearchQuery = q;
    Results = results;
    RefDate = refDate;
    Hits = new ArrayList<SearchHit>(minHits);
  }

  public ArrayList<SearchHit> call() throws IOException {
    System.err.println("Ranking individual search hits...");
    final HitsGetter getter = new HitsGetter(RefDate, Hits);
    getter.highlight("body", SearchQuery, Searcher, Results, 1000000);
    Collections.sort(Hits);
    System.err.println("Done ranking individual search hits...");
    return Hits;
  }
}
