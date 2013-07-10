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
    Created  = DocUtil.getLongField(doc, "Created", 0) * 1000;
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
  }
}
