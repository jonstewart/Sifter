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


package edu.utsa.sifter.som;

import org.apache.mahout.math.DenseMatrix;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.function.TimesFunction;
import org.apache.mahout.math.jet.random.AbstractDistribution;

import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.HashMap;

public class SelfOrganizingMap {

  public static class CellStats {
    private int     Num;
    private int     Region;
    private double  SumSquaredDistance;

    private java.util.Vector<String> TopTerms;

    public CellStats() {
      Num = 0;
      SumSquaredDistance = 0;
    }

    public int getNum() {
      return Num;
    }

    public double stdDev() {
      return Math.sqrt(Num > 2 ? SumSquaredDistance / (Num - 1): SumSquaredDistance);
    }

    public double sumSqrDistance() {
      return SumSquaredDistance;
    }

    public void accumulate(final double dist) {
      ++Num;
      SumSquaredDistance += dist * dist;
    }

    public void setRegion(final int r) {
      Region = r;
    }

    public int getRegion() {
      return Region;
    }

    public java.util.Vector<String> getTopTerms() {
      return TopTerms;
    }

    public void setTopTerms(final java.util.Vector<String> t) {
      TopTerms = t;
    }
  }

  private final DenseMatrix Cells;
  private final double  CellFactors[];
  private final double  S2[];
  private final int Height, Width, Dims, NumCells;
  private final CellStats Stats[];

  private int NumRegions;
  private int[] RegionColors;
  private ArrayList<Set<Integer>> RegionMap;

  private ArrayList<HashMap<Integer, Integer>> CellTermDifferences;

  private final int NUM_POSSIBLE_REGION_COLORS = 9;

  public SelfOrganizingMap(int height, int width, int dimensions) {
    Height = height;
    Width = width;
    NumCells = Height * Width;
    Dims = dimensions;
    Cells = new DenseMatrix(NumCells, Dims);
    CellFactors = new double[NumCells];
    S2 = new double[NumCells];
    Stats = new CellStats[NumCells];
    NumRegions = 0;
  }

  public void init(final AbstractDistribution prng) {
    Cells.assign(prng);
    for (int i = 0; i < NumCells; ++i) {
      CellFactors[i] = 1.0;
      Stats[i] = new CellStats();
    }
    recalculateS2();
  }

  public void rescale() { // call this after the SOM has converged
    final TimesFunction multiplier = new TimesFunction();
    for (int i = 0; i < NumCells; ++i) {
      Cells.viewRow(i).assign(multiplier, CellFactors[i]);
      CellFactors[i] = 1.0;
    }
  }

  public void assignCell(final int id, final double dist) {
    Stats[id].accumulate(dist);
  }

  public CellStats getStats(final int id) {
    return Stats[id];
  }

  public void recalculateS2() {
    double s2;
    Vector cellWeights;
    double f;
    for (int id = 0; id < NumCells; ++id) {
      cellWeights = getCell(id);
      s2 = 0;
      f  = CellFactors[id];
      for (Vector.Element w: cellWeights) {
        s2 += (f * w.get()) * (f * w.get());
      }
      S2[id] = s2;
    }
  }

  public final int getX(final int id) {
    return id % Width;
  }

  public final int getY(final int id) {
    return id / Width;
  }

  public final int getID(final int x, final int y) {
    return y * Width + x;
  }

  public final Vector getCell(final int x, final int y) {
    return Cells.viewRow(getID(x, y));
  }

  public final Vector getCell(final int id) {
    return Cells.viewRow(id);
  }

  public final double getFactor(final int id) {
    return CellFactors[id];
  }

  public final double getS2(final int id) {
    return S2[id];
  }

  public final int width() {
    return Width;
  }

  public final int height() {
    return Height;
  }

  public final int numCells() {
    return NumCells;
  }

  public final int dimensions() {
    return Dims;
  }

  public int getNumRegions() {
    return NumRegions;
  }

  public void setNumRegions(final int n) {
    NumRegions = n;
  }

  public int getRegionColor(final int region) {
    return RegionColors[region];
  }

  public ArrayList<Set<Integer>> getRegionMap() {
    return RegionMap;
  }

  public void getNeighborhood(final int x, final int y, final int radius, final IntArrayWritable neighbors) {
    final int minX = Math.max(x - radius, 0);
    final int minY = Math.max(y - radius, 0);
    final int maxX = Math.min(x + radius, Width - 1);
    final int maxY = Math.min(y + radius, Height - 1);

    neighbors.clear();

    for (int curY = minY; curY <= maxY; ++curY) {
      for (int curX = minX; curX <= maxX ; ++curX) {
        neighbors.add(getID(curX, curY));
      }
    }
  }

  public double computeDistance(final int id, final IntArrayWritable doc) {
    final Vector cellWeights = getCell(id);
    final int    cardinality = doc.getLength();
    final double s1 = cardinality;
    final double s2 = getS2(id);

    double s3 = 0.0;
    double c = 0.0, // Kahan summation algorithm to account for error, c.f. http://en.wikipedia.org/wiki/Kahan_summation_algorithm
           w,
           t;

    final double f  = getFactor(id);
    final int[] terms = doc.getInts();
    for (int i = 0; i < cardinality; ++i) {
      w  = (f * cellWeights.getQuick(terms[i])) - c;
      t  = s3 + w;
      c  = (t - s3) - w;
      s3 = t;
      // s3 += f * cellWeights.getQuick(itr.next().index());
    }
    s3 *= -2;

    final double d = s1 + s2 + s3;
    if (d < 0) {
      System.out.println("Negative distance on " + id + " - d = " + d + ", s1 = " + s1 + ", s2 = " + s2 + ", s3 = " + s3 + ", f = " + f);
    }
    return d;
  }

  public void updateCell(final int id, final double alpha, final IntArrayWritable doc) {
    // Scalable SOM updating, per Roussinov

    final double rate  = 1 - alpha;
    final double f     = CellFactors[id];
    final double nextF = rate * f; // Rule 5

    final double adjustment = alpha / (rate * CellFactors[id]); // Rule 6
    double sumSqrOld  = 0.0;
    double sumSqrNew  = 0.0;
    double c1 = 0.0, // Kahan summation algorithm to account for error, c.f. http://en.wikipedia.org/wiki/Kahan_summation_algorithm
           c2 = 0.0,
           y,
           t;

    final Vector weights = getCell(id);

    double weight;
    double trueWeight;
    int idx;
    final int[] terms = doc.getInts();
    final int numTerms = doc.getLength();
    for (int i = 0; i < numTerms; ++i) {
      idx = terms[i];
      weight = weights.getQuick(idx);

      trueWeight = weight * f;
      y = (trueWeight * trueWeight) - c1;
      t = sumSqrOld + y; // S'(t+1) component
      c1 = (t - sumSqrOld) - y;
      sumSqrOld = t;
      // sumSqrOld += trueWeight * trueWeight;

      weight    += adjustment; // adjust weight

      trueWeight = weight * nextF;
 
      y = (trueWeight * trueWeight) - c2;
      t = sumSqrNew + y;
      c2 = (t - sumSqrNew) - y;
      sumSqrNew = t;
      // sumSqrNew += trueWeight * trueWeight; // S_2'(t+1) component

      weights.setQuick(idx, weight);
    }
    CellFactors[id] = nextF;
    S2[id] = sumSqrNew + (rate * rate) * (S2[id] - sumSqrOld); // new S2 component
  }

  void assignTopTerms(final int numTopTerms, final java.util.Vector<String> terms) {
    final PriorityQueue<TermPair>  topWeights = new PriorityQueue<TermPair>(numTopTerms, new TermPair.TermPairComparator());

    for (int i = 0; i < numCells(); ++i) {
      final java.util.Vector<String> topTerms  = new java.util.Vector<String>(numTopTerms);
      topTerms.setSize(numTopTerms);

      final Vector cell = getCell(i);
      final double f = getFactor(i);

      topWeights.clear();
      for (Vector.Element w: cell) {
        int val = (int)(1000 * f * w.get());
        if (topWeights.size() < numTopTerms) {
          topWeights.add(new TermPair(terms.get(w.index()), val));
        }
        else if (topWeights.peek().DocCount < val) {
          topWeights.remove();
          topWeights.add(new TermPair(terms.get(w.index()), val));
        }
      }
      final int numTopWeights = topWeights.size();
      for (int j = numTopWeights - 1; j > -1; --j) {
        topTerms.set(j, topWeights.remove().Term);
      }
      getStats(i).setTopTerms(topTerms);
    }
  }

  void initColorMap(final Set<Integer> colors) {
    colors.clear();
    for (int i = 0; i < NUM_POSSIBLE_REGION_COLORS; ++i) {
      colors.add(i);
    }
  }

  int pickColor(final int region, final Set<Integer> colors, final Integer[] choices, final Random chooser) {
    if (colors.size() > 0) {
      final int choice = chooser.nextInt(colors.size());
      return colors.toArray(choices)[choice];
    }
    else {
      System.err.println("The set of possible colors for region " + region + " is zero!");
      return chooser.nextInt(NUM_POSSIBLE_REGION_COLORS);
    }
  }

  void colorRegions() {
    RegionColors = new int[NumRegions];
    RegionMap = new ArrayList<Set<Integer>>(NumRegions);
    for (int i = 0; i < NumRegions; ++i) {
      RegionMap.add(new HashSet<Integer>());
      RegionColors[i] = -1;
    }
    final IntArrayWritable neighbors = new IntArrayWritable(9);
    for (int y = 0; y < height(); ++y) {
      for (int x = 0; x < width(); ++x) {
        final int id = getID(x, y);
        final int region = getStats(id).getRegion();
        getNeighborhood(x, y, 1, neighbors);
        for (int i = 0; i < neighbors.getLength(); ++i) {
          final int neighbor = neighbors.getInts()[i];
          final int otherRegion = getStats(neighbor).getRegion();
          if (otherRegion != region) {
            RegionMap.get(region).add(otherRegion);
            RegionMap.get(otherRegion).add(region);
          }
        }
      }
    }
    final Random chooser = new Random(29);
    final Set<Integer> colors = new HashSet<Integer>(NUM_POSSIBLE_REGION_COLORS);
    final Integer[] choices = new Integer[NUM_POSSIBLE_REGION_COLORS];
    for (int i = 0; i < NumRegions; ++i) {
      initColorMap(colors);
      for (Integer adj: RegionMap.get(i)) {
        if (RegionColors[adj] != -1) {
          colors.remove(RegionColors[adj]);
        }
      }
      RegionColors[i]  = pickColor(i, colors, choices, chooser);
    }
  }

  int maxTermDifference(final int tID, final int uID) {
    final Vector t = getCell(tID);
    final Vector u = getCell(uID);
    final Vector diff = t.minus(u);
    final double maxValue = diff.maxValue();
    final double minValue = diff.minValue();

    if (minValue < 0 && Math.abs(minValue) > maxValue) { // even if maxValue is negative, this will hold
      return -diff.minValueIndex();
    }
    else {
      return diff.maxValueIndex();
    }
  }

  public HashMap<Integer, Integer> getCellTermDiffs(final int cellID) {
    return CellTermDifferences.get(cellID);
  }

  public int getCellTermDiff(final int lhsID, final int rhsID) {
    if (lhsID == rhsID) {
      return 0;
    }
    else if (lhsID < rhsID) {
      return CellTermDifferences.get(lhsID).get(rhsID);
    }
    else { // rhsID < lhsID
      return -1 * CellTermDifferences.get(rhsID).get(lhsID);
    }
  }

  void setCellTermDiff(final int diff, final int lhsID, final int rhsID) {
    if (lhsID == rhsID) {
      return;
    }
    else if (lhsID < rhsID) {
      CellTermDifferences.get(lhsID).put(rhsID, diff);
    }
    else { // rhsID < lhsID
      CellTermDifferences.get(rhsID).put(lhsID, -1 * diff);
    }
  }

  void assignTermDiffs() {
    // diffs will be stored as ctd[id1][id2], where id1 < id2
    CellTermDifferences = new ArrayList<HashMap<Integer, Integer>>(NumCells);
    for (int i = 0; i < NumCells; ++i) {
      CellTermDifferences.add(new HashMap<Integer, Integer>());
    }
    int id = -1;
    int otherID = -1;
    for (int y = 0; y < Height - 1; ++y) {
      /*
        3 adjacencies involving at least one cell neither in the last row nor
        nor last column.
          --------------
           |     |     |
           | x,y *  1  |
           |     |     |
          ----*--*------
           |     |     |
           |  2  |     |
           |     |     |
          --------------
      */
      for (int x = 0; x < Width - 1; ++x) {
        id      = getID(x, y);
        otherID = getID(x + 1, y);
        setCellTermDiff(maxTermDifference(id, otherID), id, otherID);

        otherID = getID(x, y + 1);
        setCellTermDiff(maxTermDifference(id, otherID), id, otherID);

        otherID = getID(x + 1, y + 1);        
        setCellTermDiff(maxTermDifference(id, otherID), id, otherID);

        // cross cells, 1-2
        id      = getID(x + 1, y);
        otherID = getID(x, y + 1);
        setCellTermDiff(maxTermDifference(id, otherID), id, otherID);
      }
      /*
        Vertical adjacencies for last column.
          --------------
           |     |     |
           |     | x,y |
           |     |     |
          ----------*---  OUT OF BOUNDS
           |     |     |
           |     |     |
           |     |     |
          --------------
      */
      id      = getID(Width - 1, y);
      otherID = getID(Width - 1, y + 1);
      setCellTermDiff(maxTermDifference(id, otherID), id, otherID);
    }
    /*
      Horizontal adjacencies for last row.
          --------------
           |     |     |
           |     |     |
           |     |     |
          --------------
           |     |     |
           | x,y *     |
           |     |     |
          --------------
           OUT OF BOUNDS
    */
    for (int x = 0; x < Width - 1; ++x) {
      id      = getID(x, Height - 1);
      otherID = getID(x + 1, Height - 1);
      setCellTermDiff(maxTermDifference(id, otherID), id, otherID);
    }
  }
}
