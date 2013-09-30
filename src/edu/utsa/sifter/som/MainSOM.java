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

import java.io.File;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.BufferedWriter;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Set;
import java.util.PriorityQueue;

import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringEscapeUtils;

import org.apache.lucene.analysis.standard.StandardAnalyzer;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.MultiFields;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.DoubleField;

import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.conf.Configuration;

import org.apache.mahout.math.Vector;
import org.apache.mahout.math.jet.random.Uniform;

import edu.utsa.sifter.som.SifterConfig;

public class MainSOM {

  final SifterConfig Conf;

  final private IndexReader Reader;
  private HashMap<String, Integer> TermIndices;
  private java.util.Vector<String> Terms;

  private int NumDocsWritten;
  private int NumOutliers; // docs with no coincident features
  private int MaxDocTerms;
  private long SumDocTerms;

  public MainSOM(final IndexReader rdr, SifterConfig conf) throws IOException {
    Reader = rdr;
    Conf = conf;

    resetCounters();
  }

  public HashMap<String, Integer> getTermsMap() {
    return TermIndices;
  }

  public int getNumDocs() {
    return NumDocsWritten;
  }

  public int getNumOutliers() {
    return NumOutliers;
  }

  public int getMaxDocTerms() {
    return MaxDocTerms;
  }

  public double getAvgDocTerms() {
    final double sum = SumDocTerms;
    return sum / (NumDocsWritten - NumOutliers);
  }

  void resetCounters() {
    NumDocsWritten = NumOutliers = MaxDocTerms = 0;
    SumDocTerms = 0;
  }

  void initTerms() throws IOException {
    final Terms  terms  = MultiFields.getTerms(Reader, "body");

    System.out.println("number of terms in index: " + terms.size());
    final PriorityQueue<TermPair> topTerms = new PriorityQueue<TermPair>(Conf.MAX_VECTOR_FEATURES, new TermPair.TermPairComparator());

    int num = 0;
    TermsEnum term = terms.iterator(null);
    while (term.next() != null) {
      final int count = term.docFreq();
      final double r  = ((double)count) / Reader.numDocs();

      if (Conf.DOC_FREQ_THRESHOLD_LOW <= r && r <= Conf.DOC_FREQ_THRESHOLD_HIGH) {
        final String s = term.term().utf8ToString();
        if (s.length() >= Conf.MIN_SOM_TERM_LENGTH) {
          if (topTerms.size() < Conf.MAX_VECTOR_FEATURES) {
            topTerms.add(new TermPair(s, count));
          }
          else if (topTerms.peek().DocCount < count) {
            topTerms.remove();
            topTerms.add(new TermPair(s, count));
          }
          ++num;
        }
      }
    }
    System.out.println(num + " terms with in doc frequency range");

    final int numFeatures = Math.min(topTerms.size(), Conf.MAX_VECTOR_FEATURES);
    TermIndices = new HashMap<String, Integer>((numFeatures * 4 +1) / 3); // respect load factor
    Terms = new java.util.Vector<String>(numFeatures);
    Terms.setSize(numFeatures);
    System.out.println("the top " + numFeatures + " features will be used");
    for (int i = numFeatures - 1; i > -1; --i) { // reverse order, to put top terms first
      TermPair t = topTerms.poll(); // least remaining
      TermIndices.put(t.Term, i);
      Terms.set(i, t.Term);
      // System.out.println("Including term " + t.Term + " (" + t.DocCount + ")");
    }
  }

  void writeVectors(final SequenceFile.Writer file) throws IOException, CorruptIndexException, NoSuchFieldException {
    System.out.println("Creating document term vectors");
    final LongWritable id = new LongWritable();
    final IntArrayWritable vec = new IntArrayWritable(TermIndices.size());
    final HashSet<String> idFields = new HashSet();
    idFields.add("ID");

    int max = Reader.maxDoc();
    int noTVs = 0;

    TermsEnum term = null;
    // iterate docs
    for (int i = 0; i < max; ++i) {
      vec.clear();
      final Document doc = Reader.document(i, idFields);
      final IndexableField idField = doc.getField("ID");
      if (idField == null) {
        throw new NoSuchFieldException("document " + i + " does not have an ID field");
      }
      id.set(Long.parseLong(idField.stringValue()));

      // get term vector for body field
      final Terms terms = Reader.getTermVector(i, "body");
      if (terms != null) {
        // count terms in doc
        int numTerms = 0;
        term = terms.iterator(term);
        int j = 0;
        while (term.next() != null) {
          // System.out.println("doc " + i + " had term '" + term.term().utf8ToString() + "'");
          // System.out.println("doc freq: " + term.docFreq());
          // System.out.println("ord: " + term.ord());
          // System.out.println("totalTermFreq: " + term.totalTermFreq());
          Integer index = TermIndices.get(term.term().utf8ToString());
          if (index != null) {
            vec.add(index);
            ++numTerms;
          }
        }
        if (numTerms > 0) {
          // System.out.println("doc " + i + " had " + numTerms + " terms");
          MaxDocTerms = Math.max(MaxDocTerms, numTerms);
          SumDocTerms += numTerms;          
        }
      }
      else {
        ++noTVs;
        // System.err.println("doc " + i + " had no term vector for body");
      }
      if (vec.getLength() == 0) {
        ++NumOutliers;
      }
      file.append(id, vec);
      ++NumDocsWritten;
    }
    System.out.println(noTVs + " docs had no term vectors");
  }

  void somStats(final SifterConfig conf, final SelfOrganizingMap som, final ArrayList< ArrayList< Long > > clusters, final Writer somJS) throws IOException {
    somJS.write("{\"width\":" + som.width() + ", \"height\":" + som.height() + ", \n\"cells\":[\n");

    int numZero = 0;
    int numWith = 0;
    int totalWith = 0;
    long totalSSD = 0;
    int maxNum = 0;
    double maxSSD = 0;
    double maxStd = 0;

    for (int i = 0; i < som.numCells(); ++i) {
      final ArrayList<Long> cluster = clusters.get(i);
      if (cluster.size() == 0) {
        ++numZero;
      }
      else {
        ++numWith;
        totalWith += cluster.size();
      }
      totalSSD += som.getStats(i).sumSqrDistance();

      maxNum = Math.max(maxNum, cluster.size());
      maxSSD = Math.max(maxSSD, som.getStats(i).sumSqrDistance());
      maxStd = Math.max(maxStd, som.getStats(i).stdDev());

      somJS.write("{\"topTerms\":[");
      final java.util.Vector<String> topTerms = som.getStats(i).getTopTerms();
      for (int j = 0; j < Conf.NUM_TOP_CELL_TERMS; ++j) {
        if (j != 0) {
          somJS.write(", ");
        }
        somJS.write("\"");
        somJS.write(StringEscapeUtils.escapeEcmaScript(topTerms.get(j)));
        somJS.write("\"");
      }
      somJS.write("], ");
      somJS.write("\"num\":" + cluster.size() + ", \"stdDev\":" + som.getStats(i).stdDev() + ", \"ssd\":" + som.getStats(i).sumSqrDistance());
      somJS.write(", \"region\":" + som.getStats(i).getRegion());
      if (i + 1 == som.numCells()) {
        somJS.write("}\n");
      }
      else {
        somJS.write("},\n");
      }
    }
    somJS.write("], \"numZero\":" + numZero + ", \"numWith\":" + numWith);
    somJS.write(", \"totalWith\":" + totalWith + ", \"avgNum\":" + (double)totalWith/numWith);
    somJS.write(", \"numOutliers\":" + getNumOutliers());
    somJS.write(", \"ssd\":" + totalSSD + ", \"numRegions\":" + som.getNumRegions());
    somJS.write(", \"maxCellNum\":" + maxNum + ", \"maxCellSSD\":" + maxSSD + ", \"maxCellStd\":" + maxStd + ",\n\"regionColors\":[");
    for (int i = 0; i < som.getNumRegions(); ++i) {
      if (i > 0) {
        somJS.write(", ");
      }
      somJS.write(Integer.toString(som.getRegionColor(i)));
    }
    somJS.write("],\n\"regionMap\":[");
    final ArrayList<Set<Integer>> regionMap = som.getRegionMap();
    for (int i = 0; i < regionMap.size(); ++i) {
      if (i > 0) {
        somJS.write(", ");
      }
      somJS.write("[");
      final Set<Integer> adjMap = regionMap.get(i);
      int j = 0;
      for (Integer adj: adjMap) {
        if (j > 0) {
          somJS.write(", ");
        }
        somJS.write(Integer.toString(adj));
        ++j;
      }
      somJS.write("]");
    }
    somJS.write("],\n");

    somJS.write("\"cellTermDiffs\":[\n");
    for (int i = 0; i < som.numCells(); ++i) {
      final HashMap<Integer, Integer> diffs = som.getCellTermDiffs(i);
      if (i != 0) {
        somJS.write(",\n");
      }
      somJS.write("{");
      int j = 0;
      for (Map.Entry<Integer, Integer> pair: diffs.entrySet()) {
        if (j != 0) {
          somJS.write(", ");
        }
        ++j;
        somJS.write("\"");
        somJS.write(Integer.toString(pair.getKey()));
        somJS.write("\": \"");
        int val = pair.getValue();
        if (val < 0) {
          somJS.write("-");
          val = -1 * val;
        }
        somJS.write(Terms.get(val));
        somJS.write("\"");
      }
      somJS.write("}");
    }
    somJS.write("]\n");
    somJS.write("}\n");
  }

  void addDoc(final IndexWriter writer, final SelfOrganizingMap som, final long docID, final CellDistance cell, final int cellID) throws IOException {
    final Document doc = new Document();
    doc.add(new LongField("som-docID", docID, Field.Store.YES));
    doc.add(new StringField("cell", Integer.toString(cellID), Field.Store.YES));
    if (cell != null) {
      doc.add(new IntField("som-x", som.getX(cell.ID), Field.Store.YES));
      doc.add(new IntField("som-y", som.getY(cell.ID), Field.Store.YES));
      doc.add(new DoubleField("som-cell-distance", cell.Distance, Field.Store.YES));
    }
    writer.addDocument(doc);
  }

  void makeSOM(final SifterConfig conf, final SequenceFile.Reader seqRdr, final IndexWriter writer, final Writer somJS) throws IOException, InterruptedException, ExecutionException {
    final IntArrayWritable  docVec = new IntArrayWritable(TermIndices.size());
    final LongWritable      id = new LongWritable();
    final SelfOrganizingMap som = new SelfOrganizingMap(conf.SOM_HEIGHT, conf.SOM_WIDTH, TermIndices.size());
    final SOMBuilder        builder = new SOMBuilder(som, conf);
    try {
      som.init(new Uniform(0.0, 1.0, conf.RANDOM_SEED));

      final double alphaStep  = conf.NUM_SOM_ITERATIONS > 1 ? (conf.MAX_ALPHA - conf.MIN_ALPHA) / (conf.NUM_SOM_ITERATIONS - 1): 0;
      final double radiusStep = conf.NUM_SOM_ITERATIONS > 1 ? ((double)conf.MAX_NEIGHBOR_RADIUS - conf.MIN_NEIGHBOR_RADIUS) / (conf.NUM_SOM_ITERATIONS - 1): 0;
      final long   seqRdrStart = seqRdr.getPosition();

      builder.setSteps(alphaStep, radiusStep);
      for (int i = 0; i < conf.NUM_SOM_ITERATIONS; ++i) {
        builder.iterate(seqRdr);
        System.out.println("Finished iteration " + i);
        seqRdr.seek(seqRdrStart);
      }

      System.out.println("Assigning documents to clusters");
      final ArrayList< ArrayList< Long > > clusters = new ArrayList< ArrayList< Long > >(som.numCells());
      for (int i = 0; i < som.numCells(); ++i) {
        clusters.add(new ArrayList< Long >());
      }
      while (seqRdr.next(id, docVec)) {
        CellDistance winner = null;
        int cellID = -1;
        if (docVec.getLength() > 0) {
          winner = builder.findMin(id.get(), docVec);
          cellID = winner.ID;
        }
        if (cellID > -1) {
          clusters.get(cellID).add(id.get());
          som.assignCell(cellID, winner.Distance);
        }
        addDoc(writer, som, id.get(), winner, cellID);
  //      System.out.println("doc " + id.get() + " is closest to (" + winner.X + ", " + winner.Y + ")");
      }
      System.out.println("Rescaling SOM vectors");
      som.rescale(); // set weights[i] = f[i] * weights[i], f[i] = 1.0; for distance calcs between cells
      System.out.println("Assigning top terms to each cell");
      som.assignTopTerms(conf.NUM_TOP_CELL_TERMS, Terms);
      System.out.println("Calculating greatest neighbor term difference");
      som.assignTermDiffs();
      System.out.println("Assigning cells to regions");
      builder.assignRegions();
      System.out.println("Writing final output");
      somStats(conf, som, clusters, somJS);
    }
    finally {
      somJS.close();
      builder.shutdown();
      seqRdr.close();
    }
  }

  IndexWriter createWriter(final File somIdx, final SifterConfig conf) throws CorruptIndexException, IOException {
    Directory dir = FSDirectory.open(somIdx);

    StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_40);
    IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_40, analyzer);
    iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
    iwc.setRAMBufferSizeMB(conf.INDEXING_BUFFER_SIZE);
    IndexWriter writer = new IndexWriter(dir, iwc);
    return writer;
  }

  public static void main(String[] args) throws IOException, InterruptedException, CorruptIndexException, NoSuchFieldException {
    final File evPath  = new File(args[0]);
    final File idxPath = new File(evPath, "primary-idx");

    final long begin = System.currentTimeMillis();

    // createIndex(path);
    final Path outPath = new Path(new Path(evPath.toString()), "docVectors.seq");
    final Configuration hadoopConf = new Configuration();
    final LocalFileSystem fs = FileSystem.getLocal(hadoopConf);
    final SequenceFile.Writer file = SequenceFile.createWriter(fs, hadoopConf,
                                                              outPath, LongWritable.class, IntArrayWritable.class);

    final DirectoryReader dirReader = DirectoryReader.open(FSDirectory.open(idxPath));

    final SifterConfig conf = new SifterConfig();
    InputStream  xmlProps = null;
    try {
      xmlProps = new FileInputStream("sifter_props.xml");
    }
    catch (FileNotFoundException ex) {
      ; // swallow exeption
    }
    conf.loadFromXML(xmlProps); // safe with null

    final MainSOM     builder = new MainSOM(dirReader, conf);
    IndexWriter writer = null;
    FileOutputStream somJSFile   = null;
    try {
      builder.initTerms();
      builder.writeVectors(file);
      file.close();

      final SequenceFile.Reader seqRdr = new SequenceFile.Reader(fs, outPath, hadoopConf);
      writer = builder.createWriter(new File(evPath, "som-idx"), conf);

      somJSFile  = new FileOutputStream(new File(evPath, "som.js"));
      final CharsetEncoder utf8 = Charset.forName("UTF-8").newEncoder();
      utf8.onMalformedInput(CodingErrorAction.IGNORE);
      final Writer somJS = new BufferedWriter(new OutputStreamWriter(somJSFile, utf8));
      builder.makeSOM(conf, seqRdr, writer, somJS);
      writer.forceMerge(1);
    }
    catch (Exception e) {
      e.printStackTrace(System.err);
    }
    finally {
      file.close();
      if (writer != null) {
        writer.close();        
      }
      if (somJSFile != null) {
        somJSFile.close();
      }
      dirReader.close();

      System.out.println("Number of docs written: " + builder.getNumDocs());
      System.out.println("Number of outlier docs: " + builder.getNumOutliers());
      System.out.println("Total term dimensions: " + builder.getTermsMap().size());
      System.out.println("Max terms per doc: " + builder.getMaxDocTerms());
      System.out.println("Avg terms per doc: " + builder.getAvgDocTerms());
      System.out.println("Duration: " + ((System.currentTimeMillis() - begin) / 1000) + " seconds");

      conf.storeToXML(new FileOutputStream("sifter_props.xml"));
    }
  }    
}
