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

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.spi.container.servlet.ServletContainer;

import java.io.IOException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Handler;

// import org.eclipse.jetty.servlet.Context;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.HandlerList;

class Sifter {
  public static void main(String[] args) throws Exception {
    System.out.println("Starting");

    ResourceHandler resHandler = new ResourceHandler();
    resHandler.setDirectoriesListed(true);
    resHandler.setWelcomeFiles(new String[]{"index.html"});
    resHandler.setResourceBase(args[0]);

    ServletHolder sh = new ServletHolder(ServletContainer.class);
    sh.setInitParameter("com.sun.jersey.config.property.resourceConfigClass", "com.sun.jersey.api.core.PackagesResourceConfig");
    sh.setInitParameter("com.sun.jersey.config.property.packages", "edu.utsa.sifter");
    sh.setInitParameter("com.sun.jersey.api.json.POJOMappingFeature", "true");

    Server server = new Server(8080);

    ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
    handler.setContextPath("/");
    handler.addServlet(sh, "/*");

    HandlerList handlers = new HandlerList();
    handlers.setHandlers(new Handler[]{resHandler, handler});

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
        public void run() {
          try {
            IndexResource.State.shutdown();
          }
          catch (IOException ex) {
            System.err.println("Error closing indices, they may be wedged.");
          }
        }
    });

    server.setHandler(handlers);
    server.start();
    server.join();
  }
}
