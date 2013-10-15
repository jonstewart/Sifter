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
