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

package edu.utsa.sifter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

public class MarkableFileInputStream extends InputStream {

  final private FileInputStream MyFile;

  private long MarkedPos = 0;

  public MarkableFileInputStream(final FileInputStream file) {
    MyFile = file;
  }

  @Override
  public int available() throws IOException {
    return MyFile.available();
  }
  
  @Override
  public void close() throws IOException {
    MyFile.close();
  }

  @Override
  public void mark(final int readLimit) {
    // readLimit, ha, we don't need no stinkin' readLimit
    final FileChannel chnl = MyFile.getChannel();
    try {
      MarkedPos = chnl.position();
    }
    catch(IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public boolean markSupported() {
    return true;
  }

  @Override
  public int read() throws IOException {
    return MyFile.read();
  }

  @Override
  public int read(final byte[] b) throws IOException {
    return MyFile.read(b);
  }

  @Override
  public int read(final byte[] b, int off, int len) throws IOException {
    return MyFile.read(b, off, len);
  }

  @Override
  public void reset() throws IOException {
    final FileChannel chnl = MyFile.getChannel();
    chnl.position(MarkedPos);
  }

  public long skip(final long n) throws IOException {
    return MyFile.skip(n);
  }
}
