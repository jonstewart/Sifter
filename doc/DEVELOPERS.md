Sifter v1.0 Developer Documentation
=======================

---

## Building

### Jar

Building the Sifter.jar is relatively easy. The Python-based [SCons](http://www.scons.org) build system is used. Once Python, SCons, and the JDK are installed on a system, typing `scons` within the Sifter directory should be enough to compile the Java classes and then aggregate them into Sifter.jar.

SCons really only performs two commands, however:

    javac -classpath lib/asm-3.1.jar:lib/commons-codec-1.5.jar:lib/commons-configuration-1.6.jar:lib/commons-io-2.4.jar:lib/guava-14.0-rc1.jar:lib/hadoop-core-1.0.4.jar:lib/jackson-core-asl-1.9.2.jar:lib/jackson-jaxrs-1.9.2.jar:lib/jackson-mapper-asl-1.9.2.jar:lib/jackson-xc-1.9.2.jar:lib/jersey-client-1.17.1.jar:lib/jersey-core-1.17.1.jar:lib/jersey-json-1.17.1.jar:lib/jersey-server-1.17.1.jar:lib/jersey-servlet-1.17.1.jar:lib/jettison-1.1.jar:lib/jetty-all-7.4.5.v20110725.jar:lib/jsr311-api-1.1.1.jar:lib/mahout-core-0.7.jar:lib/mahout-math-0.7.jar:lib/servlet-api-2.5.jar:lib/tika-app-1.2.jar:lib/uncommons-maths-1.2.2.jar:lib/liblinear-1.92.jar:lib/lucene-analyzers-common-4.4.0.jar:lib/lucene-core-4.4.0.jar:lib/lucene-highlighter-4.4-SNAPSHOT.jar:lib/lucene-queryparser-4.4.0.jar:lib/lucene-sandbox-4.4.0.jar:lib/commons-lang3-3.1.jar:lib/commons-lang-2.4.jar -d build -sourcepath src src/edu/utsa/sifter/Bookmark.java src/edu/utsa/sifter/BookmarkSearcher.java src/edu/utsa/sifter/DataTablesData.java src/edu/utsa/sifter/DocIndexer.java src/edu/utsa/sifter/DocMaker.java src/edu/utsa/sifter/DocMakerTask.java src/edu/utsa/sifter/DocUtil.java src/edu/utsa/sifter/FSRipReader.java src/edu/utsa/sifter/FileInfo.java src/edu/utsa/sifter/ForRealBlockingQueue.java src/edu/utsa/sifter/HitRanker.java src/edu/utsa/sifter/HitsGetter.java src/edu/utsa/sifter/IndexInfo.java src/edu/utsa/sifter/IndexResource.java src/edu/utsa/sifter/Indexer.java src/edu/utsa/sifter/PublicByteArrayInputStream.java src/edu/utsa/sifter/Result.java src/edu/utsa/sifter/Sceadan.java src/edu/utsa/sifter/SearchHit.java src/edu/utsa/sifter/SearchInfo.java src/edu/utsa/sifter/SearchResults.java src/edu/utsa/sifter/Sifter.java src/edu/utsa/sifter/StringsParser.java src/edu/utsa/sifter/som/CellDistance.java src/edu/utsa/sifter/som/CellDistanceComparator.java src/edu/utsa/sifter/som/CellUpdater.java src/edu/utsa/sifter/som/DocVectorsReader.java src/edu/utsa/sifter/som/IntArrayWritable.java src/edu/utsa/sifter/som/MainSOM.java src/edu/utsa/sifter/som/MinRowDistance.java src/edu/utsa/sifter/som/SOMBuilder.java src/edu/utsa/sifter/som/SelfOrganizingMap.java src/edu/utsa/sifter/som/SifterConfig.java src/edu/utsa/sifter/som/TermPair.java

and

    jar cf sifter.jar -C build .

As you can see, the `javac` compilation sets the classpath to contain every jar in Sifter's lib/ directory, and then compiles every .java file within src/.

### Windows Installer

[InnoSetup](http://www.jrsoftware.org/isinfo.php) is a popular open source Windows tool used for generating Windows application installer executables. Once Sifter.jar has been built and InnoSetup has been installed, creating a new installer can be performed by double-clicking on the `sifter_setup_script.iss` file. This should open within InnoSetup, and the installer will then be created by clicking on the Compile button. `Sifter_Setup.exe` will then be placed in innosetup_output/.

## Indexing

Sifter uses the [Apache Lucene](http://lucene.apache.org) library for providing indexed search capabilities. The first step in using Sifter is to extract data from a disk image and index it. A command-line utility, [fsrip](http://jonstewart.github.io/fsrip/), is used to read files from the disk image. Fsrip is written in C++ and is a thin wrapper around the [Sleuthkit](http://www.sleuthkit.org/) library. It is used as a separate utility to avoid using the Sleuthkit directly through JNI bindings. Instead, fsrip outputs data to stdout and Sifter is able to read incoming this stream from stdin. By utilizing a pipe as a communication channel, there's little need to write out data to disk for indexing.

### fsrip stream format

The output stream format from fsrip is binary, but very simple. Output for a single file contains both metadata, as a JSON object, and the file content, as raw binary. The stream as a whole is a sequence of such output, with no header or footer or other data.

Output for a single file is encoded as such:

    Field          Size      Comment      
    ----------------------------------------
    Metadata Size  8 bytes   Little endian 
    Metadata       Variable  JSON object   
    File Size      8 bytes   Little endian 
    File contents  Variable  Includes slack

The JSON metadata corresponds pretty closely to the [TSK_FS_FILE](http://www.sleuthkit.org/sleuthkit/docs/api-docs/structTSK__FS__FILE.html) struct in The Sleuthkit.

### Ingest

Sifter's indexing starts off in [Indexer.java](../src/edu/utsa/sifter/Indexer.java). Indexer creates the Lucene index directory (specified on the command-line), reads in the list of stopwords (also specified on the command-line), loads the XML options, and ingests the fsrip stream from System.in and indexes it, using [FSRipReader](../src/edu/utsa/sifter/FSRipReader.java). Once indexing has completed, it merges all Lucene indices into a single segment. This is necessary because we'll later create a separate, parallel index during the generation of the Self-Organizing Map and Lucene's ability to work with a parallel index only works if each index has a single segment.

Upon construction, FSRipReader creates a threadpool and several large byte arrays of equal size (specified by `INDEXING_BUFFER_SIZE` in SifterConfig). These are put into a thread-safe queue. `FSRipReader.readData()` reads in fsrip output from an InputStream. It first reads the JSON metadata and keeps it in a byte array. It then checks the size of the file contents. If the file contents can fit into one of the pre-created byte arrays in the thread-safe queue, it will remove one of the arrays from the queue and read the file contents into the array. If the size of the file exceeds the array size, then a temporary file is created and the file contents are put into the temporary file on disk. In this way FSRipReader can accommodate very large files without exhausting memory, but only uses memory for the vast majority of files.

Once a file's data has been consumed by FSRipReader, it is aggregated into a [FileInfo](../src/edu/utsa/sifter/FileInfo.java) object, wrapped with a [DocMakerTask](../src/edu/utsa/sifter/DocMakerTask.java) and placed onto another thread-safe queue, for consumption and execution by a threadpool (using Java's `ThreadPoolExecutor`).

### Indexing

Indexing of a file begins in `DocMakerTask.run()`, which is executed by a background thread within FSRipReader's threadpool. The JSON metadata is first parsed. If the file repesents an unallocated cluster, then its file type is guessed by the classifier in the [Sceadan](../src/edu/utsa/sifter/Sceadan.java) object, which uses a joint bi-gram/uni-gram model via [LibLinear](http://www.csie.ntu.edu.tw/~cjlin/liblinear/).

Text extraction of the file contents then occurs via helper functions [DocMaker](../src/edu/utsa/sifter/DocMaker.java], using the [Apache Tika](http://tika.apache.org/) library. If Tika is not able to extract the text successfully, a [StringsParser](../src/edu/utsa/sifter/StringsParser.java) object is used, which attempts to extract UTF-8 and ASCII-compatible UTF-16LE strings within the file. If the text extraction contains no tokens (after the removal of stopwords), then the file is _not_ indexed. Zeroed sectors are therefore not added to the index or otherwise considered by Sifter.

Once the Lucene Document object is created for the file, it is added to the index. If the file contains slack space, that is then processed and indexed as a separate Document.

Finally, if the file contents were backed by one of the pre-allocated byte arrays, the array is pushed back into the thread-safe queue, for re-use.

Both Lucene and Tika are thread-safe and have taken some pains to be optimized for concurrent use. Given this, they are shared between the threads in the threadpool. The best indexing performance is achieved by increasing Lucene's own buffer size to at least 128MB (if not larger) and using relatively large file buffers (e.g., 100MB). There is some diminishing return to threadpool size with normal files (beyond ~6 threads), but the Sceadan classifier is generally CPU bound so disk images with a great deal of unallocated space will benefit from a computer with many cores. 

## SOM Generation

### Overview

The creation of the [self-organizing map (SOM)](http://en.wikipedia.org/wiki/Self-organizing_map) is a separate command-line invocation that must happen after the disk image has been indexed. The Lucene index is opened and then the term vectors are extracted from the index and used as input for the SOM algorithm. The vectors in the SOM cells are initialized with random values, and the document feature vectors are used to train the SOM. This happens in an iterative fashion, with the number of iterations controlled by `SifterConfig.NUM_SOM_ITERATIONS`.

After the SOM has been trained, then each document is assigned to the closest cell in the SOM. This information is recorded in a new Lucene index, which is written out in the same order as the primary index (one entry per document). Because the secondary index parallels the first, Lucene can automatically treat them as a single index. In this way we avoid the unnecessary cost of updating every Lucene document, which would be significant.

At the end of the SOM process, a JavaScript file, som.js, is output, containing some high-level information about the SOM structure. This is used in the display of the SOM in the web client.

### Implementation

[MainSOM](../src/edu/utsa/sifter/som/MainSOM.java) is the starting point for the SOM generation process. MainSOM is fairly procedural and could probably stand to have a helper class or two extracted from it. Upon startup, the first significant thing that happens is to insert all terms with their corpus-wide frequency into a priority queue (`MainSOM.initTerms()`). Once the queue reaches its maximum size, new terms' frequencies are compared with the least frequest term in the queue. If a new term's frequency is greater than the least frequent term in the queue, the least frequent term is removed from the queue and the new term added. Once all terms have been examined, the priority queue is transformed into both a vector of the terms, in order of most frequest to least frequent, and a HashMap for looking up the index into this vector based on the term string itself.

Once this has happened, a SequenceFile (a binary file format often used in Apache Hadoop) is created and then `MainSOM.writeVectors()` is called. This iterates over all documents in the primary Lucene index, retrieves their term vectors, converts the term vectors into an [IntArrayWritable](../src/edu/utsa/sifter/som/IntArrayWritable.java) object, which contains the occurrences of the corpus-wide most-frequent terms in the document, and then writes it to the SequenceFile. This I/O operation is done to avoid keeping all of the document vectors in memory and/or to avoid re-iterating through the Lucene index and converting the Lucene document term vectors on the fly (which is somewhat slow). The SequenceFile format is fast and works well with sequential reads, which is all we'll use it for.

It is important to note that at this point we know which documents will be outliers: those documents that do not have any of the most frequent terms.

MainSOM uses a class named [SOMBuilder](../src/edu/utsa/sifter/som/SOMBuilder.java) to assist with training the SOM. Another class, [SelfOrganizingMap](../src/edu/utsa/sifter/som/SelfOrganizingMap.java), represents the SOM data itself. Although training a SOM is not a data-parallel operation by _document_, some parallelism does exist in the major operations. SOMBuilder uses a threadpool to find the closest cell to a given term vector in parallel, and, once the minimum has been found, to update that cell and its neighbors in parallel.

Once the SequenceFile has been written, it's re-opened and iterated in `MainSOM.makeSOM()` (and then in `SOMBuilder.iterate()`). Each document's term vector is read in, and then the nearest cell in the SOM is found for that document (`SOMBuilder.findMin()`). Once found, that cell and its neighbors are updated to be a little closer to the current document. After every document has been read in, the updating amount ("alpha") and neighborhood size ("radius") are both decreased and the whole process starts again. It proceeds for `SifterConfig.NUM_SOM_ITERATIONS` times.

The SelfOrganizingMap class has a few things worth discussing. First is that we use the Scalable Self-Organizing Map algorithm due to Roussinov. This involves a lot of algebra to avoid having to update all the cell weights for every document (instead, only weights coincident with the current document term vector are updated). Second, the SelfOrganizingMap.computeDistance() function utilizes [Kahan's Summation Algorithm](http://en.wikipedia.org/wiki/Kahan_summation_algorithm) for reducing floating point error. This a fairly small impact on performance, and has empirically prevented negative distances from being calculated (which happens if the summation is done naively).

After the SOM has been trained, the documents are "assigned" to their nearest cells and this information is recorded in a new Lucene index. The SOM is then normalized (`SelfOrganizingMap.rescale()`), the top-most terms for each cell are calculated (`SelfOrganizingMap.assignTopTerms()`), and cells are aggregated into colored regions based upon their top-most term (`SelfOrganizingMap.assignTermDiffs()`, `SelfOrganizingMap.assignRegions`).

Finally, the som.js file is written via `MainSOM.somStats()`.

## Sifter Client

### Jersey and IndexResource.java

[Jersey](https://jersey.java.net/) is an open source library and framework for creating RESTful web services in Java. Methods are decorated with annotations describing the routing URL, HTTP method, and arguments to be used, and Jersey dynamically routes incoming HTTP traffic to the corresponding methods. With assistance from annotations, Jersey is able to handle serialization to/from XML & JSON automatically, resulting in fairly terse code (for Java).

Sifter.java contains the main() method for starting up the web service, which uses [Jetty](http://www.eclipse.org/jetty/), an embedded web server, and initializes it to serve things up via Jersey. The main entry point for responsive services is then IndexResource.java. IndexResource is a singleton class object that receives callbacks on its annotated methods in response to incoming web service requests.

### sifter.js

The Sifter web client operates by using JavaScript to make asynchronous web service requests to the Sifter server, communicating via JSON. The main logic for this is in [sifter.js](../client/sifter.js) with the initial HTML document in [index.html](../client/index.html). The Sifter.js file makes heavy use of [jQuery](http://www.jquery.com/) throughout, as well as [Bootstrap](http://getbootstrap.com/2.3.2/), [DataTables](http://datatables.net/), and [d3](http://www.d3js.org/).

When index.html is loaded, control to the JavaScript is passed to the "$(document).ready()" callback at the end of Sifter.js. This block of code binds other callback functions in the file to user-interface elements in the DOM/HTML. Those functions are then executed only in response to user actions.

Most of the JavaScript is fairly straightforward (for JavaScript). The drawSOM() function uses d3 to convert the data held within the som.js file of the loaded index into the SVG representation of the SOM, complete with popover details for each cell. Probably the most important technical item of note here is the use of a Hue-Saturation-Luminance (HSL) color scheme. The hue is determined by randomly-assigned region coloring (done by `SelfOrganizingMap.assignRegions()`), with saturation determined by the error of the cluster and luminance by the number of items in the cluster (on a logarithmic scale). The full range of luminance is not used, as it would make it very difficult to see differences in hue and saturation. The nitty-gritty aspects of the table are best described by the DataTables documentation.

### Bookmarks

Sifter is primarily a read-only application, with very little modifiable state. However, the user can bookmark documents, associating their own comment with the set of documents bookmarked. The relevant source files are [Bookmark](../src/edu/utsa/sifter/Bookmark.java) and [BookmarkSearcher](../src/edu/utsa/sifter/BookmarkSearcher.java), as well as `IndexResource.getBookmarks()` and `IndexResource.createBookmarks()`. Bookmarks are stored in a tertiary Lucene index, which is _not_ parallel to the primary and secondary indices. It is treated as a separate store, and must be joined manually with the Document results (see `IndexResource.getHitExport()` for an example of joining query results with any associated bookmarks).

### Hit ranking

When the user submits a search query, the initial list of responsive documents is created and ranked by Lucene. Lucene's response time is generally very fast, so the top K documents are returned immediately (c.f. SearchResults.java). Before these initial results are returned, however, a background thread is launched to analyze the individual hits within the result set and rank them via a custom method. This happens asynchronously, leaving a good chance that the ranking of hits will complete before the user requests them in the web app.

For each hit, a number of numeric features related to the hit are calculated. These features are then multiplied by constant weights (the `AllocatedModel` and `UnallocatedModel` arrays in HitsGetter.java) and summed for an initial score. Finally, the initial score is normalized based on the range of scores on all the hits responsive to the query.

The features and the weights used for a hit vary based on whether the document is in unallocated. Allocated documents use filesystem metadata as the basis for some features, which is not present for unallocated documents. Some of the features are shared between these two different models.

The following source files pertain to hit ranking:

 * [HitRanker.java](../src/edu/utsa/sifter/HitRanker.java)
 * [HitsGetter.java](../src/edu/utsa/sifter/HitsGetter.java)
 * [Result.java](../src/edu/utsa/sifter/Result.java)
 * [SearchHit.java](../src/edu/utsa/sifter/SearchHit.java)

The general mechanism is to make use of the new [PassageFormatter](http://lucene.apache.org/core/4_4_0/highlighter/index.html?org/apache/lucene/search/postingshighlight/PassageFormatter.html) facility in Lucene. This allows for iteration of hits in a result set.

By default, Lucene's PassageFormatter splits a document into sentences. We treat each sentence containing one or more hits as a discrete item to be ranked, and do not distinguish between multiple hits contained in the same sentence. The advantage of the sentence discretization is that it automatically carries some context with it for review, and that it already works well within Lucene. If more than one hit is contained within the sentence, then we choose the applicable features of the sentence in such a way as to improve the sentence's ranking.

Invoking `PostingsHighlighter.highlight()` in HitRanker.java results in the creation of a static class `HitRank` (contained in HitsGetter.java) object, and the automatic iteration of documents in the result set. When the `HitRank` object is created, TF-IDF scores are calculated for every term in the query, as `Corpus Term Frequency * log(Number of docs in corpus / (1 + Number of docs in corpus containing term))` and the maximum value is noted. For each document, `HitRank.format()` is then invoked and passed all sentences in the document as an array of Passage objects.

The Lucene Document object is retrieved and then document-level features are calculated by `Result.docRankFactors()` in the `Result` class. Because proximity of hits is involved as a feature for ranking, the passages are iterated to calculate the distance between one passage and the next and these distances are stored. The passages are then iterated again for calculation of the features and the initial rank (`Result.calculateScore()`).

The minimum and maximum scores are tracked for both allocated and unallocated files. These are updated after calculating the initial rank of each hit. Once all of the hits have been ranked, their scores are then normalized with the corresponding minimum and maximum scores (zeroed to the minimum and divided by max - min), and then multiplied by 10, to place the normalized score on a 0-10 scale. Finally, the hits are sorted and then made available to the web service.

The features are detailed below:

 * Document-level Features (c.f. Result.java, `Result.docRankFactors()`)
     * For both allocated and unallocated files:

         * High Priority File Type: 1 if the extension (determined by the Sceadan model for unallocated docs) is in the high priority list, else 0. The file types are defined in [DocMaker](../src/edu/utsa/sifter/DocMaker.java).
         * Medium Priority File Type: 1 if the extension is in the medium priority list, else 0
         * Low Priority File Type: 1 if the extension is in the low priority list, else 0
         * Cosine Similarity between the query and the document: the sum of the term frequencies in the document for terms contained in the query divided by the sum of the square roots of the sum of the squared frequencies of all terms in the document and the number of terms in the query.
         * Query cardinality ratio: the number of unique query terms in the doc divided by the total number of terms in the query.

     * For allocated files only:

         * Created difference: the absolute difference between the reference timestamp and the Created timestamp, divided by the reference timestamp (all timestamps are milliseconds since beginning of Unix epoch).
         * Modified difference: the absolute difference between the reference timestamp and the Modified timestamp, divided by the reference timestamp.
         * Accessed difference: the absolute difference between the reference timestamp and the Accessed timestamp, divided by the reference timestamp.
         * Average timestamp difference: the average of the preceding three features.
         * Filename direct: Currently this is always zeroed.
         * Filename indirect: 1 if the full path of the file contains any of the terms in the query, else 0.
         * User directory: 0 if the path exists within a list of known system directories, else 1. The directories are defined in [Result](../src/edu/utsa/sifter/Result.java).

 * Hit-level Features (c.f. SearchHit.java, `SearchHit.calculateScore()`)
    * Term TF-IDF: The maximum TFIDF score of a query term within the sentence, divided by the maximum TFIDF of any term within the document.
    * Hit Frequency: The maximum term frequency of a query term contained within the sentence, divided by the maximum term frequency of any term within the document.
    * Proximity: The nearest distance between a query term in the sentence and a different query term in the document, divided by the size of the document (i.e., the size of the extracted text, _not_ the file size).
    * Term Length: The maximum length of a query term in the sentence divided by the maximum length of any term in the document.
    * Hit Offset: The offset of the first term in the sentence from the start of the document, divided by the size of the document (i.e., the size of the extracted text).
