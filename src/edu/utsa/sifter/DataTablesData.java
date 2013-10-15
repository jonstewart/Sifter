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

import org.codehaus.jackson.annotate.JsonProperty;

import java.util.ArrayList;

public class DataTablesData {
  @JsonProperty
  final public int iTotalRecords;

  @JsonProperty
  final public int iTotalDisplayRecords;

  @JsonProperty
  final public String sEcho;

  // @JsonProperty
  // public String sColumns;

  @JsonProperty
  final public ArrayList< ArrayList< Object > > aaData;

  public DataTablesData(final int total, final int display, final String echo) {
    iTotalRecords = total;
    iTotalDisplayRecords = display;
    sEcho = echo;
    aaData = new ArrayList< ArrayList< Object > >();
  }
}
