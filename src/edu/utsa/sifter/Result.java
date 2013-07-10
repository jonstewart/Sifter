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

import org.codehaus.jackson.annotate.JsonProperty;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;

public class Result {
  @JsonProperty
  public String ID;

  @JsonProperty
  public float Score;

  @JsonProperty
  public String Name;

  @JsonProperty
  public String Path;

  @JsonProperty
  public String Extension;

  @JsonProperty
  public long Size;

  @JsonProperty
  public long Modified;

  @JsonProperty
  public long Accessed;

  @JsonProperty
  public long Created;

  @JsonProperty
  public String Body;

  @JsonProperty
  public String Cell;

  @JsonProperty
  public double CellDistance;

  Result() {}

  Result(final Document doc, final float score) {
    Score = score;
    ID = doc.get("ID");
    Name = doc.get("name");
    Extension = doc.get("extension");
    // System.out.println("result name = " + (Name == null ? "null": Name));
    Path = doc.get("path");
    Size = DocUtil.getLongField(doc, "size", 0);

    Modified = DocUtil.getLongField(doc, "modified", 0) * 1000;
    Accessed = DocUtil.getLongField(doc, "accessed", 0) * 1000;
    Created  = DocUtil.getLongField(doc, "created", 0) * 1000;

    Body = doc.get("body");

    Cell = doc.get("cell");
    CellDistance = (float)DocUtil.getDoubleField(doc, "som-cell-distance", 0);
  }
}
