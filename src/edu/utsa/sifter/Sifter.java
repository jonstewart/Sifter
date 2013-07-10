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
