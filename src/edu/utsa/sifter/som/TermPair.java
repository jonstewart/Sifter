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

package edu.utsa.sifter.som;

import java.util.Comparator;

public class TermPair {
  public static class TermPairComparator implements Comparator<TermPair> {
    public int compare(final TermPair lhs, final TermPair rhs) {
      if (lhs.DocCount == rhs.DocCount) {
        return 0;
      }
      else {
        return lhs.DocCount < rhs.DocCount ? -1: 1;
      }
    }

    public boolean equals(Object obj) {
      return obj instanceof TermPairComparator;
    }
  }

  final public String Term;
  final public long   DocCount;

  TermPair(final String t, final long count) {
    Term = t;
    DocCount = count;
  }
}
