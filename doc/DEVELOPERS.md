Developer Documentation
=======================

`[ toc ]`

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



## SOM Generation


## Sifter Client

### Jersey and IndexResource.java

[Jersey](https://jersey.java.net/) is an open source library and framework for creating RESTful web services in Java. Methods are decorated with annotations describing the routing URL, HTTP method, and arguments to be used, and Jersey dynamically routes incoming HTTP traffic to the corresponding methods. With assistance from annotations, Jersey is able to handle serialization to/from XML & JSON automatically, resulting in fairly terse code (for Java).

Sifter.java contains the main() method for starting up the web service, which uses [Jetty](http://www.eclipse.org/jetty/), an embedded web server, and initializes it to serve things up via Jersey. The main entry point for responsive services is then IndexResource.java. IndexResource is a singleton class object that receives callbacks on its annotated methods in response to incoming web service requests.

### sifter.js

### Hit ranking

When the user submits a search query, the initial list of responsive documents is created and ranked by Lucene. Lucene's response time is generally very fast, so the top K documents are returned immediately (c.f. SearchResults.java). Before these initial results are returned, however, a background thread is launched to analyze the individual hits within the result set and rank them via a custom method. This happens asynchronously, leaving a good chance that the ranking of hits will complete before the user requests them in the web app.

For each hit, a number of numeric features related to the hit are calculated. These features are then multiplied by constant weights (the `AllocatedModel` and `UnallocatedModel` arrays in HitsGetter.java) and summed for an initial score. Finally, the initial score is normalized based on the range of scores on all the hits.

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

         * High Priority File Type: 1 if the extension (determined by the Sceadan model for unallocated docs) is in the high priority list, else 0
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
         * User directory: 0 if the path exists within a list of known system directories, else 1

 * Hit-level Features (c.f. SearchHit.java, `SearchHit.calculateScore()`)
    * Term TF-IDF: The maximum TFIDF score of a query term within the sentence, divided by the maximum TFIDF of any term within the document.
    * Hit Frequency: The maximum term frequency of a query term contained within the sentence, divided by the maximum term frequency of any term within the document.
    * Proximity: The nearest distance between a query term in the sentence and a different query term in the document, divided by the size of the document (i.e., the size of the extracted text, _not_ the file size).
    * Term Length: The minimum length of a query term in the sentence divided by the maximum length of any term in the document.
    * Hit Offset: The offset of the first term in the sentence from the start of the document, divided by the size of the document (i.e., the size of the extracted text).
