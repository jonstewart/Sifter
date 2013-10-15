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
