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

    TempBuf = ByteBuffer.allocate(4096);
    Decoded = CharBuffer.allocate(4096);
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
