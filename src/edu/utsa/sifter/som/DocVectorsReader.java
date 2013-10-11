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
