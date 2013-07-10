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
