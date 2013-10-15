Sifter
======
Copyright 2013, University of Texas at San Antonio (UTSA)

[http://www.utsa.edu](http://www.utsa.edu)

This project includes open source software developed by other entities, available under different licenses.

## Java

### Apache 2.0 Licenses

_[Apache Software Foundation](http://www.apache.org)_

Sifter includes software belonging to several Apache projects, all available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0). These are [Apache Lucene](http://lucene.apache.org), [Apache Tika](http://tika.apache.org), [Apache Mahout](http://mahout.apache.org), and portions of [Apache Commons](http://commons.apache.org) and [Apache Hadoop](http://hadoop.apache.org).

_[The Eclipse Foundation](http://www.eclipse.org)_

Sifter includes software developed by The Eclipse Foundation, also available under the [Apache 2.0 License](http://projects.eclipse.org/content/apache-license-version-2.0). These libraries comprise the [Jetty http server](http://www.eclipse.org/jetty/) (jetty-all-VERSION.jar, servlet-api-2.5.jar).

_Others_

Apache Mahout depends on [Uncommons Maths](http://maths.uncommons.org/), copyright Daniel Dyer. Uncommons Maths is available under Apache 2.0.

The Apache Lucene Analyzers library depends on the [Guava library](http://code.google.com/p/guava-libraries/), primarily developed by Google. Guave is available under Apache 2.0.

[Jettison](http://jettison.codehaus.org/) is a JSON processing library used by Jersey and copyright Envoi Solutions, Inc. It is available under Apache 2.0.

[Jackson](http://wiki.fasterxml.com/JacksonHome) is another JSON processing library used by Jersey and by Sifter directly. It is available under Apache 2.0.

### BSD

liblinear-1.92.jar is the Java port of [liblinear](http://www.csie.ntu.edu.tw/~cjlin/liblinear/), developed and maintained by [Benedikt Waldvogel](http://liblinear.bwaldvogel.de/). It is available under a [3-clause BSD License](https://github.com/bwaldvogel/liblinear-java/blob/master/COPYRIGHT).

ASM is a library for manipulating Java bytecode files and is used by both Apache Lucene and Jersey. It is copyright INRIA, France Telecom and available under a [3-clause BSD license](http://asm.ow2.org/license.html).

### GPL 2.0 with classpath exception

Sifter uses the [Jersey web application framework](http://jersey.java.net), including jsr311-api-1.1.1.jar, which is copyright Oracle Corporation. Jersey is availabe under either the CDDL or the GPLv2 with the classpath exception. We use it here under the granted classpath exception. See [http://jersey.java.net/license.html](http://jersey.java.net/license.html) for details.

## JavaScript & DHTML

Sifter.js uses the [jQuery library](http://jquery.com) for DOM manipulation and AJAX calls and other utility functions. jQuery is available under the [MIT License](https://jquery.org/license/).

The [DataTables jQuery plugin](http://datatables.net) by SpryMedia powers the table view in Sifter . It is available under either GPLv2 or a 3-clause BSD license, and used by Sifter under the latter.

[Bootstrap](http://getbootstrap.com/2.3.2/) is an HTML/CSS/JavaScript library . It is available under Apache 2.0.

[d3.js](http://www.d3js.org) is a JavaScript library for manipulating data and used by Sifter for generating the SVG display of the Self-Organizing Map. d3.js is developed by Michael Bostock and available under a 3-clause BSD license.

## Evidence Files

Sifter relies on the use of [fsrip](http://jonstewart.github.com/fsrip) for reading disk images/evidence files. Fsript is copyright Lightbox Technologies, Inc and available under Apache 2.0. It uses [The Sleuthkit](http://www.sleuthkit.org/), which is licensed under the IBM Public License and the Common Public License (c.f. http://www.sleuthkit.org/sleuthkit/licenses.php), and [libewf](http://code.google.com/p/libewf/) which is licensed under the [LGPL](http://www.gnu.org/licenses/lgpl.html).

Sifter does not link with fsrip, but does include a Windows executable of fsrip.
