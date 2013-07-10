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

package edu.utsa.sifter.som;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

import java.io.IOException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.mahout.math.Vector;
import org.apache.mahout.math.VectorWritable;

import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.LongWritable;

public class SOMBuilder {

  private final SelfOrganizingMap   SOM;
  private final ExecutorService     Pool;
  private final CellDistanceComparator    DistCmp;
  private ArrayList<MinRowDistance> RowMinWorkers;

  private final IntArrayWritable        Neighbors;
  private final ArrayList<CellUpdater>  CellUpdaters;

  private double  CurRadius;
  private double  CurAlpha;
  private double  RadiusStep;
  private double  AlphaStep;

  final private int NumThreads;
  final private int Stride;

  public SOMBuilder(final SelfOrganizingMap som, final SifterConfig conf) {
    SOM  = som;
    Pool = Executors.newFixedThreadPool(conf.THREAD_POOL_SIZE);
    CurAlpha = conf.MAX_ALPHA;
    CurRadius = conf.MAX_NEIGHBOR_RADIUS;

    int maxNeighbors = (conf.MAX_NEIGHBOR_RADIUS * 2) + 1;
    maxNeighbors *= maxNeighbors;
    Neighbors     = new IntArrayWritable(maxNeighbors);
    CellUpdaters  = new ArrayList<CellUpdater>(conf.THREAD_POOL_SIZE);
    for (int i = 0; i < conf.THREAD_POOL_SIZE; ++i) {
      CellUpdaters.add(new CellUpdater(SOM, Neighbors));
    }

    DistCmp = new CellDistanceComparator();
    System.out.println("som height = " + som.height() + ", width = " + som.width());
    NumThreads = conf.THREAD_POOL_SIZE;
    RowMinWorkers = new ArrayList<MinRowDistance>(conf.THREAD_POOL_SIZE);
    int stride = calculateStride(som.numCells(), conf.THREAD_POOL_SIZE);
    Stride = stride;
    for (int i = 0; i < conf.THREAD_POOL_SIZE; ++i) {
      RowMinWorkers.add(new MinRowDistance(SOM, i * stride, Math.min(som.numCells(), (i + 1) * stride)));
    }
  }

  void setSteps(final double aStep, final double rStep) {
    AlphaStep = aStep;
    RadiusStep = rStep;
  }

  void shutdown() {
    Pool.shutdown();
  }

  CellDistance findMin(final long id, final IntArrayWritable docVec) throws InterruptedException, ExecutionException {
    // it'd be nice if we could get rid of this loop without being too hacky
    // i.e., if a common vector was set on these at construction time
    // RowMinWorkers = new ArrayList<MinRowDistance>(SOM.height());
    for (MinRowDistance dister: RowMinWorkers) {
      dister.setDocVec(id, docVec);
    }
    // figure out min distances in rows in parallel, then find abs min in results
    // int preSize = RowMinWorkers.size();
    final List< Future< CellDistance > > results = Pool.invokeAll(RowMinWorkers);
    // System.out.println("preSize = " + preSize + ", RowMinWorkers.size() " + RowMinWorkers.size() + ", results.size() = " + results.size());
    CellDistance min = null;
    for (Future< CellDistance > fut: results) {
      CellDistance rowMin = fut.get();

      if (min == null || DistCmp.compare(rowMin, min) < 0) {
        min = rowMin;
      }
    }
    return min;
  }

  int calculateStride(final int num, final int partitions) {
    int ret = num / partitions;
    if (ret * partitions != num) {
      ++ret;
    }
    return ret;
  }

  boolean processDoc(final long id, final IntArrayWritable docVec) throws InterruptedException, ExecutionException {
    final CellDistance min = findMin(id, docVec);
    if (min != null) {
      // System.out.println("Closest cell to " + id + " is (" + min.X + ", " + min.Y + "), at d = " + min.Distance);
      // ready
      SOM.getNeighborhood(SOM.getX(min.ID), SOM.getY(min.ID), (int)CurRadius, Neighbors);
      // aim
      final int numNeighbors = Neighbors.getLength();
      final int stride = calculateStride(numNeighbors, CellUpdaters.size());
      for (int i = 0; i < CellUpdaters.size(); ++i) {
        CellUpdaters.get(i).setParams(CurAlpha, docVec, i * stride, Math.min((i + 1) * stride, numNeighbors));
      }
      // fire -- process cell updates in parallel with threadpool, block until finished
      Pool.invokeAll(CellUpdaters);
      return true;
    }
    return false;
  }

  void iterate(final SequenceFile.Reader docs) throws IOException, InterruptedException, ExecutionException {
    System.out.println("CurAlpha = " + CurAlpha + ", CurRadius = " + CurRadius);  
    // SOM.recalculateS2();
    int numCycles = 0;
    int numMinsFound = 0;
    LongWritable id = new LongWritable();
    final IntArrayWritable docVec = new IntArrayWritable(256);
    while (docs.next(id, docVec)) {
      if (docVec.getLength() > 0 && processDoc(id.get(), docVec)) {
        ++numMinsFound;
      }
      ++numCycles;
      if (numCycles % 1000 == 0) {
        System.out.println(numCycles + " cycles processed");
      }
    }
    System.out.println("processed " + numCycles + " docs in this iteration, " + numMinsFound + " had closest cells");
    CurRadius -= RadiusStep;
    CurAlpha -= AlphaStep;
  }

  void assignRegions() throws InterruptedException {
    final HashMap<String, Integer> topTermMap = new HashMap<String, Integer>();
    for (int i = 0; i < SOM.numCells(); ++i) {
      final String term = SOM.getStats(i).getTopTerms().get(0);
      if (!topTermMap.containsKey(term)) {
        topTermMap.put(term, topTermMap.size());
      }
      SOM.getStats(i).setRegion(topTermMap.get(term));
    }
    SOM.setNumRegions(topTermMap.size());
    SOM.colorRegions();
  }
}
