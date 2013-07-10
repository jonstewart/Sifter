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

import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import java.nio.CharBuffer;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.servlet.http.HttpServletResponse;

import com.sun.jersey.spi.resource.Singleton;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.ParallelCompositeReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.FSDirectory;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory; 

import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;

import edu.utsa.sifter.som.SifterConfig;

@Singleton
@Path("/")
public class IndexResource {

  @Context
  private HttpServletResponse HttpResponse;

  public static class State {
    final static public ConcurrentHashMap<String, IndexReader> Indices = new ConcurrentHashMap();
    final static public ConcurrentHashMap<String, IndexWriter> IndexWriters = new ConcurrentHashMap();
    final static public ConcurrentHashMap<String, IndexInfo> IndexLocations = new ConcurrentHashMap();
    final static public ConcurrentHashMap<String, SearchResults> Searches = new ConcurrentHashMap();

    public static void shutdown() throws IOException {
      for (IndexReader rdr: Indices.values()) {
        rdr.close();
      }
      for (IndexWriter writer: IndexWriters.values()) {
        writer.close();
      }
    }
  }

  final private MessageDigest Hasher = MessageDigest.getInstance("MD5");

  final private Log LOG = LogFactory.getLog(IndexResource.class);

  public IndexResource() throws NoSuchAlgorithmException {}

  @Path("_jersey")
  @GET
  @Produces({MediaType.TEXT_HTML})
  public String jerseyInfo() {
    return "<p>Hello, world!</p>\n";
  }

  @Path("index")
  @POST
  @Consumes({MediaType.APPLICATION_JSON})
  @Produces({MediaType.APPLICATION_JSON})
  public IndexInfo openIndex(IndexInfo idx) {
    if (idx.Id == null) {
      idx.Id = new String(Hex.encodeHex(Hasher.digest(idx.Path.getBytes())));
    }
    idx.Id = idx.Id.toLowerCase();

    IndexReader rdr = State.Indices.get(idx.Id);
    if (rdr == null) {
      try {
        final File evPath = new File(idx.Path);
        final File primaryIdx = new File(evPath, "primary-idx");
        final File somIdx = new File(evPath, "som-idx");
        DirectoryReader parallel[] = new DirectoryReader[2];
        parallel[0] = DirectoryReader.open(FSDirectory.open(primaryIdx));
        parallel[1] = DirectoryReader.open(FSDirectory.open(somIdx));

        rdr = new ParallelCompositeReader(parallel);
      }
      catch (IOException ex) {
        HttpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
      }
    }
    if (rdr != null) {
      idx.NumDocs = rdr.numDocs();

      State.Indices.put(idx.Id, rdr);
      State.IndexLocations.put(idx.Id, idx);
    }
    return idx;
  }

  @Path("index")
  @DELETE
  @Consumes({MediaType.APPLICATION_JSON})
  public void deleteIndex(IndexInfo idx) {
    if (idx.Id != null) {
      IndexReader rdr = State.Indices.remove(idx.Id);
      if (rdr != null) {
        return;
      }
    }
    HttpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  Query parseQuery(final String queryString) throws QueryNodeException {
    if (queryString != null && !queryString.isEmpty()) {
      StandardQueryParser qp = new StandardQueryParser();
      return qp.parse(queryString, "body");
    }
    else {
      return new MatchAllDocsQuery();
    }
  }

  @Path("search")
  @GET
  @Produces({MediaType.APPLICATION_JSON})
  public SearchResults performQuery(@QueryParam("q") final String queryString) throws IOException {
    IndexReader   rdr      = new MultiReader(State.Indices.values().toArray(new IndexReader[0]));
    IndexSearcher searcher = new IndexSearcher(rdr);
    SearchResults results = null;
    try {
      Query query = parseQuery(queryString); // qp.parse(queryString, "body");
      results = new SearchResults(searcher, query, false);
      State.Searches.put(results.Id, results);
    }
    catch (QueryNodeException ex) {
      HttpResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
    return results;
  }

  @Path("bookmark")
  @POST
  @Consumes({MediaType.APPLICATION_JSON})
  @Produces({MediaType.APPLICATION_JSON})
  public Bookmark openIndex(final Bookmark mark, @QueryParam("id") final String indexID) {
    System.err.println("Received a bookmark");
    try {
      final IndexInfo info = State.IndexLocations.get(indexID);
      if (info == null) {
        HttpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
      }
      else {
        System.err.println("Retrieving index");
        IndexWriter writer = State.IndexWriters.get(indexID);
        if (writer == null) {
          final SifterConfig conf = new SifterConfig();
          conf.loadFromXML(null);
          writer = Indexer.getIndexWriter(info.Path, null, conf);
          State.IndexWriters.put(indexID, writer);
        }
        mark.index(writer);
        return mark;
      }
    }
    catch (Exception ex) {
      ex.printStackTrace();
      HttpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
    return null;
  }

  @Path("doc")
  @GET
  @Produces({MediaType.APPLICATION_JSON})
  public Result getBody(@QueryParam("id") final long docID) throws IOException {
    Result ret = null;

    final IndexReader   rdr      = new MultiReader(State.Indices.values().toArray(new IndexReader[0]));
    final IndexSearcher searcher = new IndexSearcher(rdr);
    try {
      final Query query = parseQuery("+ID:" + docID);
      final SearchResults results = new SearchResults(searcher, query, true);
      final ArrayList<Result> resultSet = results.retrieve(0, 1);
      if (resultSet != null && resultSet.size() == 1) {
        ret = resultSet.get(0);
      }
    }
    catch (QueryNodeException ex) {
      HttpResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
    return ret;
  }

  @Path("dt-results")
  @GET
  @Produces({MediaType.APPLICATION_JSON})
  public DataTablesData getDataTablesResults(
                                      // @Context HttpHeaders hh,
                                      @QueryParam("id") final String id,
                                      @QueryParam("sEcho") final String echo,
                                      @DefaultValue("0") @QueryParam("iDisplayStart") final int start,
                                      @DefaultValue("20") @QueryParam("iDisplayLength") final int len) throws IOException
  {
    final SearchResults resultSet = id != null ? State.Searches.get(id): null;
    if (resultSet != null) {
      final DataTablesData dtData = new DataTablesData(resultSet.TotalHits, resultSet.TotalHits, echo);
      final ArrayList<Result> results = resultSet.retrieve(start, start + len);
      if (results != null) {
        for (Result r: results) {
          final ArrayList<Object> rec = new ArrayList<Object>(5);
          rec.add(r.ID);
          rec.add(r.Score);
          rec.add(r.Name);
          rec.add(r.Path);
          rec.add(r.Extension);
          rec.add(r.Size);
          rec.add((new Date(r.Modified)).toString());
          rec.add((new Date(r.Accessed)).toString());
          rec.add((new Date(r.Created)).toString());
          rec.add(r.Cell);
          rec.add(r.CellDistance);
          dtData.aaData.add(rec);
        }
      }
      return dtData;
    }
    else {
      HttpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return null;
    }
  }

  @Path("results")
  @GET
  @Produces({MediaType.APPLICATION_JSON})
  public ArrayList<Result> getResults(
                                      // @Context HttpHeaders hh,
                                      @QueryParam("id") final String id,
                                      @DefaultValue("0") @QueryParam("start") final int start,
                                      @DefaultValue("20") @QueryParam("end") final int end) throws IOException
  {
    final SearchResults results = id != null ? State.Searches.get(id): null;
    if (results != null) {
      return results.retrieve(start, end);
    }
    else {
      HttpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return null;
    }
  }

  static String nullCheck(final String s) {
    return s != null ? s: "";
  }

  @Path("export")
  @GET
  @Produces({"text/csv"})
  public Response getExport(@QueryParam("id") final String id) throws IOException {
    final SearchResults results = id != null ? State.Searches.get(id): null;
    if (results != null) {
      final ArrayList<Result> hits = results.retrieve(0, results.TotalHits);
      final StreamingOutput stream = new StreamingOutput() {
        public void write(OutputStream output) throws IOException, WebApplicationException {
          final OutputStreamWriter writer = new OutputStreamWriter(output, "UTF-8");
          try {
            writer.write("ID,Score,Name,Path,Extension,Size,Modified,Accessed,Created,Cell,CellDistance\n");
            for (Result hit: hits) {
              writer.write(nullCheck(hit.ID));
              writer.write(",");
              writer.write(Double.toString(hit.Score));
              writer.write(",\"");
              writer.write(nullCheck(hit.Name));
              writer.write("\",\"");
              writer.write(nullCheck(hit.Path));
              writer.write("\",\"");
              writer.write(nullCheck(hit.Extension));
              writer.write("\",");
              writer.write(Long.toString(hit.Size));
              writer.write(",");
              writer.write(Long.toString(hit.Modified));
              writer.write(",");
              writer.write(Long.toString(hit.Accessed));
              writer.write(",");
              writer.write(Long.toString(hit.Created));
              writer.write(",");
              writer.write(nullCheck(hit.Cell));
              writer.write(",");
              writer.write(Double.toString(hit.CellDistance));
              writer.write("\n");
            }
          } catch (Exception e) {
            throw new WebApplicationException(e);
          }
        }
      };

      return Response.ok(stream).header("content-disposition","attachment; filename = export.csv").build();
    }
    else {
      HttpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return null;
    }
  } 

  @Path("som")
  @GET
  @Produces({MediaType.APPLICATION_JSON})
  public String getSOM(@QueryParam("id") final String indexID) throws IOException {
    try {
      final IndexInfo info = State.IndexLocations.get(indexID);
      if (info != null) {
        final File evPath  = new File(info.Path);
        final File somPath = new File(evPath, "som.js");
        final FileInputStream in = new FileInputStream(somPath);
        final InputStreamReader wrapper = new InputStreamReader(in, "UTF-8");
        final BufferedReader rdr = new BufferedReader(wrapper, 256 * 1024);


        final StringBuilder buf = new StringBuilder();
        String line;
        while ((line = rdr.readLine()) != null) {
          buf.append(line);
          buf.append("\n");
        }
        return buf.toString();
      }
    }
    catch (Exception ex) {
      HttpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
    return null;
  }
}
