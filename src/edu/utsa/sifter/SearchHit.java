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

import org.codehaus.jackson.annotate.JsonProperty;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;

import org.apache.lucene.search.postingshighlight.Passage;
import org.apache.lucene.util.BytesRef;

import org.apache.commons.lang.StringEscapeUtils;

public class SearchHit implements Comparable<SearchHit> {
  private final Result DocData;

  @JsonProperty
  public String Passage;

  @JsonProperty
  public int Start;

  @JsonProperty
  public int End;

  @JsonProperty
  public String ID() { return DocData.ID; }

  @JsonProperty
  public float Score() { return DocData.Score; }

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

  private int MinTermLen = Integer.MAX_VALUE;

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
      int curPos = 0;
      for (int i = 0; i < n; ++i) {
        sb.append(StringEscapeUtils.escapeHtml(body.substring(curPos, matchStarts[i])));
        sb.append("<span class=\"secondarycolorbg\">");
        sb.append(StringEscapeUtils.escapeHtml(body.substring(matchStarts[i], matchEnds[i])));
        sb.append("</span>");
        curPos = matchEnds[i];
        MinTermLen = Math.min(MinTermLen, terms[i].length);
      }
      sb.append(StringEscapeUtils.escapeHtml(body.substring(curPos, End)));
      Passage = sb.toString();
    }
    else {
      Passage = body.substring(Start, End);
    }
  }

  public int compareTo(SearchHit o) {
    final float diff = o.DocData.Score - DocData.Score;
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
    features[HitRanker.FHIT_PROXIMITY] = (double)distance / DocData.BodyLen;
    features[HitRanker.FTERM_LENGTH] = (double)MinTermLen / maxTermLen;
    features[HitRanker.FTERM_PRIORITY] = 0.0;
    features[HitRanker.FUNUSED] = 0.0;
    features[HitRanker.FHIT_OFFSET] = (double)Start / DocData.BodyLen;

    float sum = 0.0f;
    for (int i = 0; i < features.length; ++i) {
      sum += features[i] * weights[i];
    }
    DocData.Score = sum;
    return sum;
  }
}
