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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;

import java.util.Date;
import java.util.Set;
import java.util.HashMap;

public class Result {
  @JsonProperty
  public String ID;

  @JsonProperty
  public float Score;

  @JsonProperty
  public String Name;

  @JsonProperty
  public String Path;

  @JsonProperty
  public String Extension;

  @JsonProperty
  public long Size;

  @JsonProperty
  public long Modified;

  @JsonProperty
  public long Accessed;

  @JsonProperty
  public long Created;

  @JsonProperty
  public String Body;

  @JsonProperty
  public String Cell;

  @JsonProperty
  public double CellDistance;

  public int BodyLen;

  private int LuceneID;

  public Result(final Document doc, final int lucID, final float score) {
    LuceneID = lucID;
    Score = score;
    ID = emptyIfNull(doc.get("ID"));
    Name = emptyIfNull(doc.get("name"));
    Extension = emptyIfNull(doc.get("extension"));
    // System.out.println("result name = " + (Name == null ? "null": Name));
    Path = emptyIfNull(doc.get("path"));
    Size = DocUtil.getLongField(doc, "size", 0);

    Modified = DocUtil.getLongField(doc, "modified", 0) * 1000;
    Accessed = DocUtil.getLongField(doc, "accessed", 0) * 1000;
    Created  = DocUtil.getLongField(doc, "created", 0) * 1000;

    Body = doc.get("body");
    BodyLen = (int)DocUtil.getLongField(doc, "body-len", 0);

    Cell = doc.get("cell");
    CellDistance = (float)DocUtil.getDoubleField(doc, "som-cell-distance", 0);
  }

  public boolean isUnallocated() {
    return Path.startsWith("$Unallocated/");
  }

  String emptyIfNull(final String s) {
    return s == null ? "": s;
  }

  public static class DocTermInfo {
    public HashMap<BytesRef, Long> TermFreqs = new HashMap<BytesRef, Long>();
    public long MaxTermFreq = 0;

  }

  public DocTermInfo docRankFactors(final double[] features, 
                             final Date refDate,
                             final IndexReader rdr,
                             final Set<Term> termSet) throws IOException
  {
    final DocTermInfo ret = new DocTermInfo();
    final String lowerExt = Extension.toLowerCase();
    if (!isUnallocated()) {
      features[HitRanker.FCREATED]  = dateDiff(Created, refDate);
      features[HitRanker.FMODIFIED] = dateDiff(Modified, refDate);
      features[HitRanker.FACCESSED] = dateDiff(Accessed, refDate);
      features[HitRanker.FAVG_RECENCY] = (features[HitRanker.FCREATED] +
                                          features[HitRanker.FMODIFIED] +
                                          features[HitRanker.FACCESSED]) / 3;
      features[HitRanker.FFILENAME_DIRECT] = 0;
      features[HitRanker.FFILENAME_INDIRECT] = 0;
      final String fullPath = Path + Name;
      for (Term t: termSet) {
        if (fullPath.indexOf(t.text()) > 0) {
          features[HitRanker.FFILENAME_INDIRECT] = 1;
          break;
        }
      }
      features[HitRanker.FUSER_DIRECTORY] = (Path.indexOf("WINDOWS") > -1 ||
                                             Path.indexOf("System Volume Information") > -1 ||
                                             Path.indexOf("RECYCLER") > -1 ||
                                             Path.indexOf("Program Files") > -1) ? 0: 1;

    }
    features[HitRanker.FHIGH_PRIORITY_TYPE] = DocMaker.HighPriorityTypes.contains(lowerExt) ? 1: 0;
    features[HitRanker.FMED_PRIORITY_TYPE] = DocMaker.MedPriorityTypes.contains(lowerExt) ? 1: 0;
    features[HitRanker.FLOW_PRIORITY_TYPE] = features[HitRanker.FHIGH_PRIORITY_TYPE] + features[HitRanker.FMED_PRIORITY_TYPE] > 0 ? 0: 1;

    final Terms terms = rdr.getTermVector(LuceneID, "body");
    final TermsEnum term = terms.iterator(null);

    double  dotSum = 0,
            docVecSumSqrs = 0,
            numDims = 0,
            queryVecSumSqrs = 0;

    long termCount = 0;

    while (term.next() != null) {
      ++numDims;
      termCount = term.totalTermFreq();
      docVecSumSqrs += termCount * termCount;
      if (termSet.contains(new Term("body", term.term()))) {
        dotSum += termCount;
        ++queryVecSumSqrs;
        ret.TermFreqs.put(BytesRef.deepCopyOf(term.term()), termCount);
        ret.MaxTermFreq = Math.max(ret.MaxTermFreq, termCount);
        // System.err.println(Path + Name + " contains term " + term.term().utf8ToString() + ", with freq " + termCount);
      }
    }
    features[HitRanker.FCOSINE_SIMILARITY] = dotSum / (Math.sqrt(docVecSumSqrs) + Math.sqrt(queryVecSumSqrs));
    features[HitRanker.FTERM_CARDINALITY]  = queryVecSumSqrs / termSet.size();


    // features[HitRanker.FTERM_LENGTH] 

    // features[HitRanker.FTERM_PRIORITY] = 0.0;
    return ret;
  }

  double dateDiff(final long d, final Date refDate) {
    return ((double)Math.abs(d - refDate.getTime())) / refDate.getTime();
  }
}
