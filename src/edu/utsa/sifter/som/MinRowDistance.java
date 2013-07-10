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

import java.util.concurrent.Callable;

public class MinRowDistance implements Callable<CellDistance> {

  private final SelfOrganizingMap SOM;
  private final int StartRow,
                    EndRow;

  private IntArrayWritable DocVec;
  private long   DocID;

  public MinRowDistance(final SelfOrganizingMap som, final int start, final int end) {
    SOM = som;
    StartRow = start;
    EndRow = end;
  }

  public void setDocVec(final long id, final IntArrayWritable vec) {
    DocID = id;
    DocVec = vec;
  }

  public CellDistance call() throws Exception {
    // System.out.println("calculing min distance for row " + Row);
    final int width = SOM.width();
    final CellDistance result = new CellDistance(-1, Double.MAX_VALUE);
    double d;
    for (int id = StartRow; id < EndRow; ++id) {
      d = SOM.computeDistance(id, DocVec);

      if (d < result.Distance) {
        result.Distance = d;
        result.ID = id;
      }
    }
    return result;
  }
}
