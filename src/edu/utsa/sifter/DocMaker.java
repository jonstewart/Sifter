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

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;

import java.io.InputStream;
import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.FieldType;

import org.apache.lucene.index.FieldInfo.IndexOptions;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.exception.TikaException;

import org.xml.sax.SAXException;

public class DocMaker {

  final static private int MAX_TIKA_CHARS = 1024 * 1024 * 1024;

  final static private FieldType BodyOptions = new FieldType();

  final static private HashMap<String, String> FieldRenames = new HashMap<String, String>();

  final static public HashSet<String> HighPriorityTypes = new HashSet<String>();
  final static public HashSet<String> MedPriorityTypes = new HashSet<String>();

  static {
    BodyOptions.setIndexed(true);
    BodyOptions.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    BodyOptions.setStored(true);
    BodyOptions.setStoreTermVectors(true);
    BodyOptions.setTokenized(true);

    FieldRenames.put("name-name", "name");
    FieldRenames.put("meta-mtime", "modified");
    FieldRenames.put("meta-crtime", "created");
    FieldRenames.put("meta-atime", "accessed");
    FieldRenames.put("meta-size", "size");

    HighPriorityTypes.add("doc");
    HighPriorityTypes.add("htm");
    HighPriorityTypes.add("html");
    HighPriorityTypes.add("pdf");
    HighPriorityTypes.add("ppt");
    HighPriorityTypes.add("pst");
    HighPriorityTypes.add("txt");
    HighPriorityTypes.add("xls");
    HighPriorityTypes.add("zip");

    MedPriorityTypes.add("bak");
    MedPriorityTypes.add("dat");
    MedPriorityTypes.add("data");
    MedPriorityTypes.add("db");
    MedPriorityTypes.add("dot");
    MedPriorityTypes.add("dtd");
    MedPriorityTypes.add("Evt");
    MedPriorityTypes.add("ini");
    MedPriorityTypes.add("json");
    MedPriorityTypes.add("lnk");
    MedPriorityTypes.add("msg");
    MedPriorityTypes.add("rar");
    MedPriorityTypes.add("sql");
    MedPriorityTypes.add("sqlite");
    MedPriorityTypes.add("sys");
    MedPriorityTypes.add("tif");
    MedPriorityTypes.add("tmp");
    MedPriorityTypes.add("url");
    MedPriorityTypes.add("xml");
  }

  public static void addField(final Document doc, String key, Object val) {
    if (FieldRenames.containsKey(key)) {
      key = FieldRenames.get(key);
    }
    if (val instanceof String) {
      doc.add(new StringField(key, (String)val, Field.Store.YES));
    }
    else if (val instanceof Number) {
      doc.add(new LongField(key, ((Number)val).longValue(), Field.Store.YES));
    }
    else if (val instanceof Map) {
      addMetadata(doc, (Map<String,Object>)val, key);
    }
    else if (val instanceof List) {
      int i = 0;
      StringBuilder buf = new StringBuilder();
      for (Object o: (List)val) {
        buf.append(key);
        buf.append("-");
        buf.append(i);
        addField(doc, buf.toString(), o);
        ++i;
        buf.setLength(0);
      }
    }
    else {
      System.err.println("Got a different field type: " + val.getClass().getName());
    }
  }

  public static void addMetadata(final Document doc, final Map<String,Object> metadata, String prefix) {
    Set<Map.Entry<String, Object>> set = metadata.entrySet();

    StringBuilder key = new StringBuilder();
    for (Map.Entry<String, Object> field : set) {
      if (prefix.length() > 0) {
        key.append(prefix);
        key.append("-");
      }
      key.append(field.getKey());

      Object val = field.getValue();
      addField(doc, key.toString(), val);
      key.setLength(0);
    }
  }  

  public static boolean addBody(final Document doc, final String name, final InputStream contents, final Parser tika, final Analyzer analyzer, final boolean testEmpty) throws IOException, SAXException, TikaException {
    BodyContentHandler handler = new BodyContentHandler(MAX_TIKA_CHARS);
    Metadata metadata = new Metadata();
    ParseContext ctx = new ParseContext();
    metadata.set(Metadata.RESOURCE_NAME_KEY, name);
    // System.out.println("Extracting contents from " + name);
    tika.parse(contents, handler, metadata, ctx);
    return addBodyField(doc, handler.toString(), analyzer, testEmpty);
  }

  public static boolean addBodyField(final Document doc, final String body, final Analyzer analyzer, boolean testEmpty) throws IOException {
    final Field f = new Field("body", body, BodyOptions);
    if (testEmpty) {
      // System.out.println("testing if doc has empty body");
      final TokenStream toks = f.tokenStream(analyzer);
      toks.reset();
      if (!toks.incrementToken()) {
        // System.out.println("empty body, won't index");
        toks.close();
        return false;
      }
    }
    doc.add(new Field("body", body, BodyOptions));
    doc.add(new LongField("body-len", body.length(), Field.Store.YES));
    return true;
  }
}
