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
