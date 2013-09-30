package edu.utsa.sifter;

import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;

import org.apache.lucene.document.Document;

import java.io.IOException;

import java.util.ArrayList;
import java.util.HashSet;

public class BookmarkSearcher {
  final private IndexSearcher Searcher; 
  final private HashSet<String> FieldsToLoad = new HashSet<String>();

  final private static int MAX_RESULTS = 1000;

  private Query SearchQuery;
  private TopDocs Docs;


  BookmarkSearcher(final IndexSearcher s, final Query q) throws IOException {
    Searcher = s;
    if (q != null) {
      executeQuery(q);
    }
    FieldsToLoad.add("Comment");
    FieldsToLoad.add("Created");
    FieldsToLoad.add("Docs");
  }

  public void executeQuery(final Query q) throws IOException {
    SearchQuery = q;
    Docs = Searcher.search(SearchQuery, MAX_RESULTS);
  }

  public ArrayList<Bookmark> retrieve() throws IOException {
    if (Docs.totalHits > 0) {
      final ArrayList<Bookmark> list = new ArrayList();
      for (int i = 0; i < Docs.totalHits; ++i) {
        list.add(getResult(Docs.scoreDocs[i].doc));
      }
      return list;
    }
    else {
      return null;
    }
  }

  public Bookmark getResult(final int id) throws IOException {
    final Document doc = Searcher.document(id, FieldsToLoad);
    if (doc != null) {
      return new Bookmark(doc);
    }
    return null;
  }
}
