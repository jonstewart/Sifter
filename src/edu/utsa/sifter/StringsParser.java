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

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CoderResult;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

public class StringsParser {

  final CharsetDecoder UTF8;

  final ByteBuffer TempBuf;
  final CharBuffer Decoded;

  public StringsParser() {
    UTF8    = Charset.forName("UTF-8").newDecoder();
    UTF8.onMalformedInput(CodingErrorAction.REPORT);
    UTF8.onUnmappableCharacter(CodingErrorAction.REPORT);

    TempBuf = ByteBuffer.allocate(16384);
    Decoded = CharBuffer.allocate(16384);
  }

  public String extract(final InputStream input) throws IOException {
    final StringBuilder ret = new StringBuilder();

    CoderResult result = null;

    int n = input.read(TempBuf.array());
    UTF8.reset();
    while (n != -1) {
      TempBuf.limit(n);
      Decoded.clear();

      result = UTF8.decode(TempBuf, Decoded, false);
      while (result.isError()) {
        if (result.length() > 1 || TempBuf.array()[TempBuf.position()] != 0) {
          Decoded.append(' '); // don't add space if it's just a single 0 byte
        }
        TempBuf.position(TempBuf.position() + result.length()); // no skip!
        result = UTF8.decode(TempBuf, Decoded, false);
      }
      Decoded.flip();
      char c;
      for (int i = 0; i < Decoded.limit(); ++i) {
        c = Decoded.charAt(i);
        if (c >= ' ') {
          ret.append(c);
        }
      }
      TempBuf.clear();
      n = input.read(TempBuf.array());
    }
    return ret.toString();
  }

  public static void main(String[] args) throws IOException, FileNotFoundException {
    final StringsParser parser = new StringsParser();
    final InputStream input = new FileInputStream(args[0]);
    System.out.println(parser.extract(input));
  }
}
