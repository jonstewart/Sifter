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

import java.util.Properties;
import java.util.InvalidPropertiesFormatException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

public class SifterConfig {
  public int THREAD_POOL_SIZE;
  public int RANDOM_SEED;

  public int INDEXING_BUFFER_SIZE;
  public int LARGE_FILE_THRESHOLD;
  public String TEMP_DIR;
  public String FILETYPE_MODEL_FILE;

  public double DOC_FREQ_THRESHOLD_HIGH;
  public double DOC_FREQ_THRESHOLD_LOW;

  public int MIN_SOM_TERM_LENGTH;
  public int MAX_VECTOR_FEATURES;

  public int SOM_WIDTH;
  public int SOM_HEIGHT;

  public int MAX_NEIGHBOR_RADIUS;
  public int MIN_NEIGHBOR_RADIUS;

  public double MAX_ALPHA;
  public double MIN_ALPHA;

  public int NUM_SOM_ITERATIONS;

  public int NUM_TOP_CELL_TERMS;

  public String DEFAULT_STOP_LIST_FILE;

  public SifterConfig() {
  }

  public void loadFromXMLFile(final String inPath) throws IOException {
    InputStream in = null;
    try {
      in = new FileInputStream(inPath);
    }
    catch (FileNotFoundException ex) {
      ;
    }
    loadFromXML(in);
  }

  public void loadFromXML(final InputStream in) throws IOException {
    final Properties props = new Properties();
    if (in != null) {
      try {
        props.loadFromXML(in);
      }
      catch (InvalidPropertiesFormatException ex) {
        System.err.println("Invalid properties XML, default values will be used. " + ex.toString());
      }
    }
    else {
      System.err.println("No XML properties file existed, default values will be used.");
    }
    THREAD_POOL_SIZE = Integer.decode(props.getProperty("thread_pool_size", "2"));
    RANDOM_SEED = Integer.decode(props.getProperty("random_seed", "17"));

    INDEXING_BUFFER_SIZE = Integer.decode(props.getProperty("indexing_buffer_size", "128"));
    LARGE_FILE_THRESHOLD = Integer.decode(props.getProperty("large_file_threshold", "128"));
    TEMP_DIR             = props.getProperty("temp_dir", "");
    FILETYPE_MODEL_FILE  = props.getProperty("filetype_model_file", "");

    DOC_FREQ_THRESHOLD_HIGH = Double.parseDouble(props.getProperty("doc_freq_threshold_high", ".66"));
    DOC_FREQ_THRESHOLD_LOW = Double.parseDouble(props.getProperty("doc_freq_threshold_low", ".0001"));

    MIN_SOM_TERM_LENGTH = Integer.decode(props.getProperty("min_som_term_length", "3"));
    MAX_VECTOR_FEATURES = Integer.decode(props.getProperty("max_vector_features", "5000"));

    SOM_WIDTH = Integer.decode(props.getProperty("som_width", "50"));
    SOM_HEIGHT = Integer.decode(props.getProperty("som_height", "50"));

    MAX_NEIGHBOR_RADIUS = Integer.decode(props.getProperty("max_neighbor_radius", "30"));
    MIN_NEIGHBOR_RADIUS = Integer.decode(props.getProperty("min_neighbor_radius", "1"));

    MAX_ALPHA = Double.parseDouble(props.getProperty("max_alpha", "0.05"));
    MIN_ALPHA = Double.parseDouble(props.getProperty("min_alpha", "0.01"));

    NUM_SOM_ITERATIONS = Integer.decode(props.getProperty("num_som_iterations", "5"));

    NUM_TOP_CELL_TERMS = Integer.decode(props.getProperty("num_top_cell_terms", "20"));
  }

  public void storeToXML(final String outPath) throws IOException {
    FileOutputStream out = null;
    try {
      out = new FileOutputStream(outPath);
      storeToXML(out);
    }
    finally {
      if (out != null) {
        out.close();      
      }
    }
  }

  public void storeToXML(final OutputStream out) throws IOException {
    final Properties props = new Properties();
    props.setProperty("thread_pool_size", Integer.toString(THREAD_POOL_SIZE));
    props.setProperty("random_seed", Integer.toString(RANDOM_SEED));

    props.setProperty("indexing_buffer_size", Integer.toString(INDEXING_BUFFER_SIZE));
    props.setProperty("large_file_threshold", Integer.toString(LARGE_FILE_THRESHOLD));
    props.setProperty("temp_dir", TEMP_DIR);
    props.setProperty("filetype_model_file", FILETYPE_MODEL_FILE);

    props.setProperty("doc_freq_threshold_high", Double.toString(DOC_FREQ_THRESHOLD_HIGH));
    props.setProperty("doc_freq_threshold_low", Double.toString(DOC_FREQ_THRESHOLD_LOW));

    props.setProperty("min_som_term_length", Integer.toString(MIN_SOM_TERM_LENGTH));
    props.setProperty("max_vector_features", Integer.toString(MAX_VECTOR_FEATURES));

    props.setProperty("som_width", Integer.toString(SOM_WIDTH));
    props.setProperty("som_height", Integer.toString(SOM_HEIGHT));

    props.setProperty("max_neighbor_radius", Integer.toString(MAX_NEIGHBOR_RADIUS));
    props.setProperty("min_neighbor_radius", Integer.toString(MIN_NEIGHBOR_RADIUS));

    props.setProperty("max_alpha", Double.toString(MAX_ALPHA));
    props.setProperty("min_alpha", Double.toString(MIN_ALPHA));

    props.setProperty("num_som_iterations", Integer.toString(NUM_SOM_ITERATIONS));

    props.setProperty("num_top_cell_terms", Integer.toString(NUM_TOP_CELL_TERMS));

    props.storeToXML(out, "No comment");
  }
}
