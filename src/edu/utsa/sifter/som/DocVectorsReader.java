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

import java.io.DataInput;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.mahout.math.Vector;
import org.apache.mahout.math.VectorWritable;
import org.apache.mahout.math.SequentialAccessSparseVector;

import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.LongWritable;

// just iterate over the vectors in a file
public class DocVectorsReader implements Iterable<SequentialAccessSparseVector> {
  public static class DocVectorsIterator implements Iterator<SequentialAccessSparseVector> {
    private LongWritable    ID;
    private VectorWritable  Next;
    final private SequenceFile.Reader Source;

    public DocVectorsIterator(SequenceFile.Reader source) {
      Source = source;
      ID = new LongWritable();
      Next = new VectorWritable();
      advance();
    }

    public boolean hasNext() {
      return Next != null;
    }

    public long curID() {
      return ID.get();
    }

    public SequentialAccessSparseVector next() {
      if (Next == null) {
        throw new NoSuchElementException();
      }
      SequentialAccessSparseVector ret = (SequentialAccessSparseVector)Next.get();
      advance();
      return ret;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }

    private void advance() {
      try {
        if (Source.next(ID, Next)) {
          System.out.println("advanced to doc " + ID.get());
        }
        else {
          System.out.println("could not advance, end of source");
          ID = null;
          Next = null;
        }
      }
      catch (IOException ex) {
        ID = null;
        Next = null;
      }
    }
  }

  final private SequenceFile.Reader Source;

  public DocVectorsReader(SequenceFile.Reader source) {
    Source = source;
  }

  public DocVectorsIterator iterator() {
    System.out.println("iterating doc vectors");
    return new DocVectorsIterator(Source);
  }
}
