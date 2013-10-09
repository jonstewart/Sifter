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

import java.util.Map;
import java.util.HashMap;

import java.io.InputStream;
import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.ByteDocValuesField;

import org.apache.tika.parser.AbstractParser;
import org.apache.tika.exception.TikaException;

import org.apache.commons.io.input.BoundedInputStream;

import org.xml.sax.SAXException;

import org.codehaus.jackson.map.ObjectMapper;

public class FileInfo {
  final static ObjectMapper Jackson = new ObjectMapper();

  final private byte[] MetadataBuffer;

  public Map<String, Object> Metadata;

  final public InputStream Data;
  final public long   ID;
  final public long   TotalSize;
  public long   FileSize;
  public long   SlackSize;

  private StringsParser Strings;
  private String        FP;
  private String        Ext;

  public FileInfo(final long id, final byte[] md, final InputStream buf, final long totalSize) {
    ID = id;
    MetadataBuffer = md;
    Data = buf;
    TotalSize = totalSize;
  }

  public void close() throws IOException {
    Data.close();
  }

  public void init() throws IOException {
    Metadata = Jackson.readValue(MetadataBuffer, Map.class);
    long size = TotalSize;
    if (Metadata != null) {
      try {
        final Object metaObj = Metadata.get("meta");
        if (metaObj != null) {
          final Object sizeObj = ((Map<String, Object>)metaObj).get("size");
          if (sizeObj != null) {
            size = ((Number)sizeObj).longValue();
          }
        }        
      }
      catch (ClassCastException e) {}
    }
    // if totalSize < size => file is overwritten
    // if size < totalSize => file has slack
    FileSize  = Math.min(TotalSize, size);
    SlackSize = Math.max(TotalSize - size, 0);
  }

  public boolean hasSlack() {
    return SlackSize > 0;
  }

  public String fullPath() {
    if (FP == null) {
      StringBuilder buf = new StringBuilder((String)Metadata.get("path"));
      buf.append(basename());
      FP = buf.toString();      
    }
    return FP;
  }

  public String basename() {
    return (String)((Map<String,Object>)Metadata.get("name")).get("name");
  }

  public String extension() {
    if (Ext == null) {
      final String name = basename();
      if (name != null) {
        final int dot = name.lastIndexOf('.');
        if (-1 < dot && dot < name.length() - 1) {
          Ext = name.substring(dot + 1).toLowerCase();
        }
      }
    }
    return Ext == null ? "": Ext;
  }

  public void setExtension(final String ext) {
    Ext = ext;
  }

  public boolean isUnallocated() {
    return fullPath().startsWith("$Unallocated/");
  }

  Document rawDoc(final Analyzer analyzer, final Document doc, final InputStream data, final String fp, final boolean testBody) {
    try {
      Strings = new StringsParser();
      if (!DocMaker.addBodyField(doc, Strings.extract(data), analyzer, testBody)) {
        // System.out.println(fp + " had no content, will not be indexed");
        return null;
      }
    }
    catch (IOException ex) {
      System.err.println("Had IOException generating raw body on " + fp + ". " + ex.toString());
    }
    return doc;
  }

  Document makeDoc(final AbstractParser tika, final Analyzer analyzer, final long id, final InputStream data,
                   final Map<String, Object> metadata, final String ext, final boolean noTikaAndTest) throws IOException
  {
    Document doc = new Document();
    DocMaker.addField(doc, "ID", Long.toString(id)); // makes querying easier if this is a string, counter-intuitively
    if (ext != null && !ext.isEmpty()) {
      DocMaker.addField(doc, "extension", ext);
    }
    DocMaker.addMetadata(doc, Metadata, "");
    final String fp = fullPath();
    data.mark((int)FileSize);
    try {
      if (noTikaAndTest) {
        return rawDoc(analyzer, doc, data, fp, noTikaAndTest);
      }
      else {
        DocMaker.addBody(doc, basename(), data, tika, analyzer, false);
      }
    }
    catch (IOException ex) {
      data.reset();
//      System.err.println("Could not extract body from " + fullPath() + ". " + ex.toString());
      return rawDoc(analyzer, doc, data, fp, noTikaAndTest);
    }
    catch (SAXException ex) {
      data.reset();
      // System.err.println("Had SAXException on body of " + fp + ". " + ex.toString());
      return rawDoc(analyzer, doc, data, fp, noTikaAndTest);
    }
    catch (TikaException ex) {
      data.reset();
      // System.err.println("Extracting text raw. Had TikaException on body of " + fp + ". " + ex.toString());
      return rawDoc(analyzer, doc, data, fp, noTikaAndTest);
    }
    return doc;
  }

  public Document generateDoc(final AbstractParser tika, final Analyzer analyzer) throws IOException {
    // System.out.println("generateDoc on " + fullPath());
    String ext = null;
    try {
      ext = extension();
    }
    catch (Exception ex) {
      System.err.println("failed adding extension");
      ex.printStackTrace(System.err);
    }
    // wrap data in BoundedInputStream to only expose contents to tika
    return makeDoc(tika, analyzer, ID, new BoundedInputStream(Data, FileSize), Metadata, ext, fullPath().startsWith("$Unallocated/"));
  }

  public Document generateSlackDoc(final AbstractParser tika, final Analyzer analyzer) throws IOException {
    if (!hasSlack()) {
      return null;
    }
    Data.reset();
    Data.skip(FileSize);

    final Map<String, Object> slackMD = new HashMap<String, Object>();
    slackMD.putAll(Metadata);

    final Object metaObj = slackMD.get("meta");
    if (metaObj != null) {
      try {
        final Map<String, Object> meta = (Map<String, Object>)metaObj;
        meta.remove("size");
        meta.put("size", new Long(SlackSize));
      }
      catch (ClassCastException e) {}
    }
    final Object nameObj = slackMD.get("name");
    if (nameObj != null) {
      try {
        final Map<String, Object> name = (Map<String, Object>)nameObj;
        final Object oldNameField = name.remove("name");
        final StringBuilder newName = new StringBuilder();
        if (oldNameField instanceof String) {
          newName.append((String)oldNameField);
        }
        newName.append(":slack");
        name.put("name", newName.toString());
      }
      catch (ClassCastException e) {}
    }
    // assert: Data must have been advanced beyond contents
    return makeDoc(tika, analyzer, ID + 1, Data, slackMD, null, true);
  }
}
