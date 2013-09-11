package edu.utsa.sifter;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexReader;
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
  private IndexSearcher Searcher;
  private Query SearchQuery;

  final static private double[] UnallocatedModel = {  0.0, // 1
                                                      0.0, // 2
                                                      0.0, // 3
                                                      0.0, // 4
                                                      0.0, // 5
                                                      0.0, // 6
                                                      0.0, // 7
                                                      0.055913734757307, // 8 - high priority type
                                                      0.040695166180732, // 9 - med priority type
                                                      0.081251582099134, // 10 - low priority type (why is low > high?)
                                                      2.01221514596937,  // 11 - TFIDF
                                                      0.439385989746279, // 12 - cosine similarity
                                                      -1.77680229429835, // 13 - hit frequency
                                                      -0.586369942419647, // 14 - proximity of hit
                                                      -0.674144862376975, // 15 - number of different terms
                                                      -1.98629990367724, // 16 - length of term
                                                      2.69216949926662, // 17 - priority of term
                                                      0.0, // 18
                                                      0.464603571157124 // 19 - hit offset
                                                    };

  final static private double[] AllocatedModel =    { 0.155562207085254, // 1 - created
                                                      0.157008570061012, // 2 - modified
                                                      0.155404798838456, // 3 - accessed
                                                      0.155996846755043, // 4 - timestamp average
                                                      -0.015430930733401, // 5 - filename direct
                                                      -0.006741700387286, // 6 - filename indirect
                                                      0.034232004582317, // 7 - user directory
                                                      -0.010504017330849, // 8 - high priority type
                                                      0.016594087347521, // 9 - med priority type
                                                      -0.00730772685965, // 10 - low priority type
                                                      0.037223869430851, // 11 - TFIDF
                                                      0.154404619706316, // 12 - cosine similarity
                                                      -0.010164370609412, // 13 - hith frequency
                                                      5.70E-05, // 14 - proximity of hits
                                                      -0.019343642162804, // 15 number of different terms
                                                      0.02349350806737, // 16 - length of term
                                                      0.153739544790095, // 17 - priority of term
                                                      0.0, // 18
                                                      -0.000532004650253 // 19 - hit offset
                                                    };

  public HitsGetter(final Date refDate, final List<SearchHit> hits) {
    RefDate = refDate;
    Hits = hits;
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

    private double[] Features = new double[19];

    public HitScore(final Date refDate, final List<SearchHit> hits, final IndexSearcher searcher, final Query sq) throws IOException {
      RefDate = refDate;
      Hits = hits;
      Searcher = searcher;
      SearchQuery = sq;
      SearchQuery.extractTerms(TermSet);
      final IndexReader rdr = searcher.getIndexReader();
      final int numDocs = rdr.numDocs();
      for (Term t: TermSet) {
        MaxTermLen = Math.max(MaxTermLen, t.bytes().length);
        final long tf = rdr.totalTermFreq(t);
        final int df = rdr.docFreq(t);
        final double tfidf = tf * Math.log((double)numDocs / (1 + df));
        TFIDFs.put(BytesRef.deepCopyOf(t.bytes()), tfidf);
        MaxTFIDF = Math.max(MaxTFIDF, tfidf);
      }
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
    public String format(Passage[] passages, String content, int docID) {
      try {
        reset();
        final Document doc = Searcher.doc(docID);
        final Result r = new Result(doc, docID, 0.0f);
        final double[] weights = r.isUnallocated() ? UnallocatedModel: AllocatedModel;

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
            hit.calculateScore(Features, weights, MaxTermLen, distance, maxHitTF / dtf.MaxTermFreq, maxDocTFIDF / MaxTFIDF);
            Hits.add(hit);
          }
        }
        else {
          System.err.println(r.Path + r.Name + " had zero hits");
        }
      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }
      return "";
    }

    @Override
    public String format(Passage[] passages, String content) {
      throw new RuntimeException("This should not be called!");
//      return "";
    }
  }

  @Override
  protected PassageFormatter getFormatter(String field) {
    try {
      return new HitScore(RefDate, Hits, Searcher, SearchQuery);
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public Map<String,String[]> highlightFields(String[] fieldsIn,
                                            Query query,
                                            IndexSearcher searcher,
                                            int[] docidsIn,
                                            int[] maxPassagesIn)
                                     throws IOException
  {

    Searcher = searcher;
    SearchQuery = query;
    return super.highlightFields(fieldsIn, query, searcher, docidsIn, maxPassagesIn);
  }
}
