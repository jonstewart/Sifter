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

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Terms;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.postingshighlight.*;

import org.apache.lucene.util.BytesRef;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class HitsGetter extends PostingsHighlighter {

  private final Date RefDate;
  private final List<SearchHit> Hits;
  private final IndexSearcher Searcher;
  private final Query SearchQuery;
  private final HitScore Formatter;

  final static private double[] UnallocatedModel = {  0.0, // 1
                                                      0.0, // 2
                                                      0.0, // 3
                                                      0.0, // 4
                                                      0.0, // 5
                                                      0.0, // 6
                                                      0.0, // 7
                                                      -0.4618430079555811, // 8 - high priority type
                                                      -1.256104553199527, // 9 - med priority type
                                                      -1.195438852921516, // 10 - low priority type (why is low > high?)
                                                      -14.16968643915636,  // 11 - TFIDF
                                                      -1.45820701479218, // 12 - cosine similarity
                                                      2.184967851601264, // 13 - hit frequency
                                                      0.6592857648113551, // 14 - proximity of hit
                                                      1.208506246240294, // 15 - number of different terms
                                                      5.784062616707228, // 16 - length of term
                                                      0.0, // 17 - priority of term
                                                      0.0, // 18
                                                      -0.7175860086678946 // 19 - hit offset
                                                    };

  final static private double[] AllocatedModel =    { -28.19132113248016, // 1 - created
                                                      -3.493369555294129, // 2 - modified
                                                      30.12105381917877, // 3 - accessed
                                                      0.002986570737990329, // 4 - timestamp average
                                                      0.7827037719379998, // 5 - filename direct
                                                      1.929623512361485, // 6 - filename indirect
                                                      -0.9514731461745805, // 7 - user directory
                                                      0.306156721829065, // 8 - high priority type
                                                      0.9537708670308881, // 9 - med priority type
                                                      0.9162126105417191, // 10 - low priority type
                                                      2.421379822295228, // 11 - TFIDF
                                                      -0.8792006472897821, // 12 - cosine similarity
                                                      -0.185233539168734, // 13 - hit frequency
                                                      -1.012669050673611, // 14 - proximity of hits
                                                      1.164585079023337, // 15 number of different terms
                                                      4.914033085150077, // 16 - length of term
                                                      0.0, // 17 - priority of term
                                                      0.0, // 18
                                                      -0.6741162464751493 // 19 - hit offset
                                                    };


  public HitsGetter(final Date refDate, final List<SearchHit> hits, final IndexSearcher s, final Query q) throws IOException {
    super(16 * 1024 * 1024);
    RefDate = refDate;
    Hits = hits;
    Searcher = s;
    SearchQuery = q;
    Formatter = new HitScore(RefDate, Hits, Searcher, SearchQuery);
  }

  public static class HitScore extends PassageFormatter {
    private final Date RefDate;
    private final List<SearchHit> Hits;
    private IndexSearcher Searcher;
    private Query SearchQuery;
    private HashSet<Term> TermSet = new HashSet<Term>();
    private HashMap<BytesRef, Double> TFIDFs = new HashMap<BytesRef, Double>();
    private double MaxTFIDF = 0.0;
    private int MaxTermLen = 0;

    public double MinAllocScore, MaxAllocScore, MinUCScore, MaxUCScore;
    public double UCRange, AllocRange;

    private double[] Features = new double[19];

    private final long TokenCount;

    public int NumDocs = 0;

    public HitScore(final Date refDate, final List<SearchHit> hits, final IndexSearcher searcher, final Query sq) throws IOException {
      RefDate = refDate;
      Hits = hits;
      Searcher = searcher;
      SearchQuery = sq;
      SearchQuery.extractTerms(TermSet);
      final IndexReader rdr = searcher.getIndexReader();
      final double numDocs = rdr.numDocs();

      final Terms  terms  = MultiFields.getTerms(rdr, "body");
      TokenCount = terms.getSumTotalTermFreq();

      for (Term t: TermSet) {
        MaxTermLen = Math.max(MaxTermLen, t.bytes().length);
        final double tf = rdr.totalTermFreq(t);
        final double tfnorm = -1 * Math.log(tf / TokenCount);

        final int df = rdr.docFreq(t);
        final double idf = Math.log(numDocs / (1 + df));
        final double tfidf = tfnorm * idf;
        TFIDFs.put(BytesRef.deepCopyOf(t.bytes()), tfidf);
        MaxTFIDF = Math.max(MaxTFIDF, tfidf);
      }
      MinUCScore = MinAllocScore = Double.MAX_VALUE;
      MaxUCScore = MaxAllocScore = -Double.MAX_VALUE; // MIN_VALUE is NOT what you want
    }

    void reset() {
      for (int i = 0; i < Features.length; ++i) {
        Features[i] = 0.0;
      }
    }

    Set<BytesRef> setify(final BytesRef[] array, final int num) {
      final Set<BytesRef> ret = new HashSet<BytesRef>();
      for (int i = 0; i < num; ++i) {
        ret.add(BytesRef.deepCopyOf(array[i]));
      }
      return ret;
    }

    @Override
    public String format(final Passage[] passages, final String content) {
      ++NumDocs;
      try {
        reset();
        if (passages.length < 0) {
          return "";
        }
        final int docID = passages[0].getDocID();
        final Document doc = Searcher.doc(docID);
        final Result r = new Result(doc, docID, 0.0f);
        final boolean isUC = r.isUnallocated();
        final double[] weights = isUC ? UnallocatedModel: AllocatedModel;

        // System.err.println(r.fullpath() + " had " + passages.length + " passages");

        final Result.DocTermInfo dtf = r.docRankFactors(Features, RefDate, Searcher.getIndexReader(), TermSet);

        // first just restrict ourselves to passages that have hits
        final ArrayList<Integer> hitIndices = new ArrayList<Integer>();
        for (int i = 0; i < passages.length; ++i) {
          if (passages[i].getNumMatches() > 0) {
            hitIndices.add(i);
          }
        }
        if (hitIndices.size() > 0) {
          // iterate backwards over matching passages to find differences in their matching terms
          final int[] nextTermBoundaries = new int[hitIndices.size()];
          nextTermBoundaries[nextTermBoundaries.length - 1] = -1;
          for (int i = hitIndices.size() - 1; i > 0; --i) {
            final int next  = hitIndices.get(i),
                      cur = hitIndices.get(i - 1);
            final Set<BytesRef> nextSet = setify(passages[next].getMatchTerms(), passages[next].getNumMatches()),
                                curSet  = setify(passages[cur].getMatchTerms(), passages[cur].getNumMatches());
            // if a passage has a hit on more than one term or if the terms in each passage are different, it's a boundary
            // else, the next boundary for cur will be whatever the next boundary for next is
            nextTermBoundaries[cur] = (nextSet.size() > 1 || curSet.size() > 1 || !curSet.equals(nextSet)) ? next: nextTermBoundaries[next];
          }
          int prevTermBoundary = -1;
          Set<BytesRef> prevTermSet = new HashSet<BytesRef>();
          for (Integer i: hitIndices) {
            final Passage p = passages[i];
            final SearchHit hit = new SearchHit(r, p, content);

            final int curStart = p.getMatchStarts()[0];

            prevTermBoundary = (i > 0 && nextTermBoundaries[i - 1] != nextTermBoundaries[i]) ? i - 1: prevTermBoundary;
            int prevDistance = r.BodyLen;
            if (prevTermBoundary > -1) {
              final Passage prevPassage = passages[prevTermBoundary];
              prevDistance = curStart - prevPassage.getMatchStarts()[prevPassage.getNumMatches() - 1];
            }
            final int nextDistance = nextTermBoundaries[i] == -1 ? r.BodyLen: passages[nextTermBoundaries[i]].getMatchStarts()[0] - curStart;
            final int distance = Math.min(prevDistance, nextDistance);

            double maxHitTF = 0.0;
            double maxDocTFIDF = 0.0;
            final BytesRef[] termArray = p.getMatchTerms();
            for (int j = 0; j < p.getNumMatches(); ++j) {
              BytesRef term = termArray[j];
              final Long tf = dtf.TermFreqs.get(term);
              if (tf != null) {
                maxHitTF = Math.max(maxHitTF, tf);
              }
              else {
                final String utf8 = term == null ? "": term.utf8ToString();
                System.err.println(r.Path + r.Name + " didn't have a term freq for matching term " + utf8);
              }
              final Double tfidf = TFIDFs.get(term);
              if (tfidf != null) {
                maxDocTFIDF = Math.max(maxDocTFIDF, tfidf);
              }
            }
            final float score = hit.calculateScore(Features, weights, MaxTermLen, distance, maxHitTF / dtf.MaxTermFreq, maxDocTFIDF / MaxTFIDF);
            if (isUC) {
              MaxUCScore = Math.max(MaxUCScore, score);
              MinUCScore = Math.min(MinUCScore, score);
            }
            else {
              MaxAllocScore = Math.max(MaxAllocScore, score);
              MinAllocScore = Math.min(MinAllocScore, score);
            }
            Hits.add(hit);
          }
        }
        else {
          System.err.println(r.Path + r.Name + " had zero hits, and " + passages.length + " passages");
        }
      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }
      return "";
    }

    public void initNormalize() {
      UCRange = MaxUCScore - MinUCScore;
      AllocRange = MaxAllocScore - MinAllocScore;
      System.err.println("UC scores:    " + MinUCScore + ", " + MaxUCScore);
      System.err.println("Alloc scores: " + MinAllocScore + ", " + MaxAllocScore);
      System.err.println("UCRange = " + UCRange);
      System.err.println("AllocRange = " + AllocRange);
    }

    public void normalize(SearchHit hit) {
      if (hit.isUnallocated()) {
        hit.normalize(MinUCScore, UCRange);
      }
      else {
        hit.normalize(MinAllocScore, AllocRange);
      }
    }
  }

  @Override
  protected PassageFormatter getFormatter(String field) {
    return Formatter;
  }

  @Override
  public Map<String,String[]> highlightFields(String[] fieldsIn,
                                            Query query,
                                            IndexSearcher searcher,
                                            int[] docidsIn,
                                            int[] maxPassagesIn)
                                     throws IOException
  {
    return super.highlightFields(fieldsIn, SearchQuery, Searcher, docidsIn, maxPassagesIn);
  }

  void normalize() {
    Formatter.initNormalize();
    for (SearchHit hit: Hits) {
      Formatter.normalize(hit);
    }
  }

  public int numDocs() {
    return Formatter.NumDocs;
  }
}
