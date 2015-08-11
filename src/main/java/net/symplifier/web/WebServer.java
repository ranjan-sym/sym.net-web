package net.symplifier.web;

import net.symplifier.web.access.UserSource;
import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.jsp.JettyJspServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.servlet.http.HttpServlet;
import java.net.URL;


/**
 * Created by ranjan on 7/23/15.
 */
public class WebServer {

  private final Server server;
  private final ContextHandlerCollection contexts = new ContextHandlerCollection();


  public WebServer(int port) {
    server = new Server(port);

    // We are also going to support JSP and JSTL by pages.private.
    // Annotation configuration is required in order to correctly set up the
    // JSP container
    Configuration.ClassList classList
            = Configuration.ClassList.setServerDefault(server);
    classList.addBefore(
            JettyWebXmlConfiguration.class.getCanonicalName(),
            AnnotationConfiguration.class.getCanonicalName()
    );
  }

  public void start() throws WebServerException {
    server.setHandler(contexts);

    try {
      server.start();
    } catch (Exception e) {
      throw new WebServerException(e);
    }
  }

  public void setDefaultRouter(String path, String resource, UserSource userSource) throws WebServerException{
    WebAppContext context = new WebAppContext();
    context.setContextPath(path);
    URL url = ClassLoader.getSystemClassLoader().getResource(resource);
    if (url == null) {
      throw new WebServerException("The resource '" + resource + "' was not found");
    }

    context.setWar(url.toExternalForm());

    // Set the ContainerIncludePattern so that jetty examines the container-path
    // jars for tlds, web-fragments, et
    // if you omit the jar that contains the jstl tlds, the jsp engine will scan
    // for them instead
    context.setAttribute(
            "org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
            ".*/[^/]*servlet-api-[^/]*\\.jar$|.*/javax.servlet.jsp.jstl-.*\\.jar$|.*/[^/]*taglibs.*\\.jar$"
    );


    context.addServlet(new ServletHolder(new Router(path, resource, userSource)), "/*");
    context.addServlet(new ServletHolder(new JettyJspServlet()), "/pages/*");
    context.addServlet(new ServletHolder(new DefaultServlet()), "/assets/*");

    contexts.addHandler(context);

//
//    // The router will handle all the requests
//    setServlet(path, new Router(path, resource, userSource));
//
//    if (path.equals("/")) {
//      path = "";
//    }
//
//    // We assume 'assets' folder to contain all the static files
//    ResourceHandler handler = new ResourceHandler();
//    handler.setDirectoriesListed(false);
//    URL url = ClassLoader.getSystemClassLoader().getResource(resource + Router.ASSETS);
//    if (url == null) {
//      throw new WebServerException("The resource '" + resource + Router.ASSETS + "' was not found");
//    }
//    handler.setResourceBase(url.toExternalForm());
//    ContextHandler resourceContext = new ContextHandler(path + Router.ASSETS);
//    resourceContext.setHandler(handler);
//    contexts.addHandler(resourceContext);
//
//    // All the JSP pages are served from /pages path relative to the default router
//    setContextFromResource(path + Router.PAGES, resource + Router.PAGES);
  }

  public void setContextFromResource(String path, String resource) throws WebServerException {
    WebAppContext context = new WebAppContext();
    context.setContextPath(path);
    URL url = ClassLoader.getSystemClassLoader().getResource(resource);
    if (url == null) {
      throw new WebServerException("The resource '" + resource + "' was not found");
    }

    context.setWar(url.toExternalForm());

    // Set the ContainerIncludePattern so that jetty examines the container-path
    // jars for tlds, web-fragments, et
    // if you omit the jar that contains the jstl tlds, the jsp engine will scan
    // for them instead
    context.setAttribute(
            "org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
            ".*/[^/]*servlet-api-[^/]*\\.jar$|.*/javax.servlet.jsp.jstl-.*\\.jar$|.*/[^/]*taglibs.*\\.jar$"
    );

    contexts.addHandler(context);

  }

  public void setServlet(String path, HttpServlet servlet) {
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath(path);

    context.addServlet(new ServletHolder(servlet), "/*");

    contexts.addHandler(context);
  }

  public void setJsonServlet(String path, Package pkg) {

    ResourceConfig config = new ResourceConfig()
            .packages(pkg.getName())
            .register(JacksonFeature.class);
    ServletContainer container = new ServletContainer(config);

    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath(path);

    context.addServlet(new ServletHolder(container), "/*");
    contexts.addHandler(context);


  }
}
