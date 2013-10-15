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

import org.codehaus.jackson.annotate.JsonProperty;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;

import org.apache.lucene.search.postingshighlight.Passage;
import org.apache.lucene.util.BytesRef;

import org.apache.commons.lang3.StringEscapeUtils;

public class SearchHit implements Comparable<SearchHit> {
  private final Result DocData;

  @JsonProperty
  public float Score;

  @JsonProperty
  public String Passage;

  @JsonProperty
  public int Start;

  @JsonProperty
  public int End;

  @JsonProperty
  public String ID() { return DocData.ID; }

  @JsonProperty
  public String Name() { return DocData.Name; }

  @JsonProperty
  public String Path() { return DocData.Path; }

  @JsonProperty
  public String Extension() { return DocData.Extension; }

  @JsonProperty
  public long Size() { return DocData.Size; }

  @JsonProperty
  public long Modified() { return DocData.Modified; }

  @JsonProperty
  public long Accessed() { return DocData.Accessed; }

  @JsonProperty
  public long Created() { return DocData.Created; }

  @JsonProperty
  public String Body() { return DocData.Body; }

  @JsonProperty
  public String Cell() { return DocData.Cell; }

  @JsonProperty
  public double CellDistance() { return DocData.CellDistance; }

  private int MaxTermLen = 0;

  public SearchHit(final Result doc, final Passage p, final String body) {
    DocData = doc;
    Start = p.getStartOffset();
    End = p.getEndOffset();

    final int n = p.getNumMatches();
    if (n > 0) {
      final StringBuilder sb = new StringBuilder();
      final int[] matchStarts = p.getMatchStarts();
      final int[] matchEnds = p.getMatchEnds();
      final BytesRef[] terms = p.getMatchTerms();
      int curPos = Math.min(Start, matchStarts[0]);
      for (int i = 0; i < n; ++i) {
        sb.append(StringEscapeUtils.escapeHtml4(body.substring(curPos, matchStarts[i])));
        sb.append("<span class=\"secondarycolorbg\">");
        sb.append(StringEscapeUtils.escapeHtml4(body.substring(matchStarts[i], matchEnds[i])));
        sb.append("</span>");
        curPos = matchEnds[i];
        MaxTermLen = Math.max(MaxTermLen, terms[i].length);
      }
      sb.append(StringEscapeUtils.escapeHtml4(body.substring(curPos, End)));
      Passage = sb.toString();
    }
    else {
      Passage = body.substring(Start, End);
    }
  }

  public boolean isUnallocated() {
    return DocData.isUnallocated();
  }

  public int compareTo(final SearchHit o) {
    final float diff = o.Score - Score;
    if (diff > 0.0f) {
      return 1;
    }
    else if (diff < 0.0f) {
      return -1;
    }
    else {
      return 0;
    }
  }

  public float calculateScore(final double[] features, final double[] weights, final int maxTermLen, final int distance, final double hitFreq, final double tfidf) {
    if (features.length != weights.length) {
      throw new RuntimeException("lengths of features and weights arrays differed");
    }
    features[HitRanker.FTERM_TFIDF] = tfidf;
    features[HitRanker.FHIT_FREQUENCY] = hitFreq;
    features[HitRanker.FHIT_PROXIMITY] = (double)distance / (DocData.BodyLen == 0 ? 1: DocData.BodyLen);
    features[HitRanker.FTERM_LENGTH] = (double)MaxTermLen / maxTermLen;
    features[HitRanker.FTERM_PRIORITY] = 0.0;
    features[HitRanker.FUNUSED] = 0.0;
    features[HitRanker.FHIT_OFFSET] = (double)Start / (DocData.BodyLen == 0 ? 1: DocData.BodyLen);

    double sum = 0.0f;
    for (int i = 0; i < features.length; ++i) {
      sum += features[i] * weights[i];
    }
    Score = (float)sum;
    return Score;
  }

  public void normalize(final double min, final double range) {
    final double newScore = 10 * ((Score - min) / (range == 0 ? 1: range));
    if (newScore < 0) {
      System.err.println("Negative score on " + DocData.fullpath() + "! newScore = " + newScore + ", old score = " + DocData.Score + 
        ", min = " + min + ", range = " + range);
    }
    Score = (float)newScore;
  }
}
