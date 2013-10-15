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

import java.io.IOException;

import org.codehaus.jackson.annotate.JsonProperty;

import org.apache.lucene.document.Document;
//import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.Field;
//import org.apache.lucene.index.FieldInfo.IndexOptions;

import java.util.Date;

public class Bookmark {
  @JsonProperty
  public String Comment;

  @JsonProperty
  public String Docs;

  @JsonProperty
  public long Created;

  public Bookmark() {}

  public Bookmark(final Document doc) {
    Comment = doc.get("Comment");
    Docs = doc.get("Docs");
    Created  = DocUtil.getLongField(doc, "Created", 0);
  }

  public void index(final IndexWriter writer) throws IOException {
    // System.err.println("Indexing bookmark: \"" + Comment + "\" on docs \"" + Docs + "\"");
    final Date now = new Date();
    Created = now.getTime();

    final Document doc = new Document();
    doc.add(new TextField("Comment", Comment, Field.Store.YES));
    doc.add(new LongField("Created", Created, Field.Store.YES));
    doc.add(new TextField("Docs", Docs, Field.Store.YES));

    writer.addDocument(doc);
    writer.commit();
  }
}
