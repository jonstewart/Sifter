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

import org.apache.hadoop.io.Writable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;

public class IntArrayWritable implements Writable {

  private int[] Data;
  private int   Len;

  public IntArrayWritable(final int capacity) {
    Len = 0;
    Data = new int[capacity];
  }

  public void readFields(final DataInput in) throws IOException {
    Len = in.readInt();
    if (Len > Data.length) {
      Data = new int[Len];
    }
    for (int i = 0; i < Len; ++i) {
      Data[i] = in.readInt();
    }
  }

  public void write(final DataOutput out) throws IOException {
    out.writeInt(Len);
    for (int i = 0; i < Len; ++i) {
      out.writeInt(Data[i]);
    }
  }

  public int[] getInts() {
    return Data;
  }

  public int getLength() {
    return Len;
  }

  public void clear() {
    Len = 0;
  }

  public int getCapacity() {
    return Data.length;
  }

  public void add(final int i) {
    if (Len == Data.length) {
      Data = Arrays.copyOf(Data, Data.length + Math.max(1, Data.length >> 1));
    }
    Data[Len++] = i;
  }
}
