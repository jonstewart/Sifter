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
