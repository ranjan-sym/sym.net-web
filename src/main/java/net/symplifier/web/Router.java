package net.symplifier.web;

import net.symplifier.core.application.Session;
import net.symplifier.web.acl.User;
import net.symplifier.web.acl.UserSource;
import org.apache.jasper.servlet.JspServlet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


/**
 * Created by ranjan on 8/10/15.
 */
public class Router {
  public static final Logger LOGGER = LogManager.getLogger(Router.class);

  public static final String WEB_INF = "/WEB-INF";
  public static final String SESSION_USER = "USER";

  public static final String PAGES = WEB_INF + "/pages";
  public static final String PRIVATE = PAGES + "/private";
  public static final String PUBLIC = PAGES + "/public";
  public static final String LOGIN_PAGE = PUBLIC + "/login";
  public static final String COOKIE_REMEMBERED_USER = "remembered-user";

  private final WebAppContext context;
  private final UserSource userSource;
  private final String resource;
  //private final SecureRandom random;
  private final boolean useFileSystem;

  private PreProcessor preProcessor;

  public Router(Server server, UserSource userSource, String path, String resource) {
    this(server, userSource, path, resource, false, PAGES);
  }

  public Router(Server server, UserSource userSource, String path, File wwwRoot) {
    this(server, userSource, path, wwwRoot.getAbsolutePath(), true, PAGES);
  }

  public Router(Server server, UserSource userSource, String path, String resource, boolean useFileSystem, String pagesPath) {
    assert(resource != null);

    this.userSource = userSource;
    this.resource = resource;
    this.useFileSystem = useFileSystem;
    //this.random = new SecureRandom();
    String absolutePath = useFileSystem ?
            resource :
            getClass().getClassLoader().getResource(resource).toExternalForm();

    // Create a web app context
    if (server.getHandlers().length == 0) {
      this.context = new WebAppContext();
    } else {
      this.context = (WebAppContext) server.getHandler();
    }

//    ServletHolder[] holders = context.getServletHandler().getServlets();
//
//    ServletHolder[] filteredHolders = Arrays.stream(holders)
//            .filter(h -> !h.getName().equals("jsp"))      // Filter out default "jsp" servlet
//            .map(h -> { h.setInitOrder(-1); return h;} )  // Set init order to -1 on each of the filtered items
//            .toArray(ServletHolder[]::new);
//
//    context.getServletHandler().setServlets(filteredHolders);

//    context = new WebAppContext();
    context.setContextPath(path);
    context.setWar(absolutePath);

    // Set the ContainerIncludePattern so that jetty examines the container-path
    // jars for tlds, web-fragments, et
    // if you omit the jar that contains the jstl tlds, the jsp engine will scan
    // for them instead
    context.setAttribute(
            "org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
            ".*/[^/]*servlet-api-[^/]*\\.jar$|.*/javax.servlet.jsp.jstl-.*\\.jar$|.*/[^/]*taglibs.*\\.jar$"
    );


    // The internal router that checks on all incoming connections
    // except the static routes
    context.addServlet(new ServletHolder(internalRouter), "/*");

    // Add the default JSP handler
    context.addServlet(new ServletHolder(new JspServlet()), pagesPath + "/*");
  }

  public void setPreProcessor(PreProcessor preProcessor) {
    this.preProcessor = preProcessor;
  }

  public WebAppContext getContext() {
    return context;
  }

  public void addStaticRoute(String path) {
    context.addServlet(new ServletHolder(new DefaultServlet()), path + "/*");
  }

  public void addServlet(String path, HttpServlet servlet) {
    System.out.println("Add servlet to path " + path);
    context.addServlet(new ServletHolder(servlet), WEB_INF + path + "/*");
  }


  // List of dynamic servlets (Added for use in HTTP Protocol in Wscada.net
  private Map<String, HttpServlet> dynamicServlets = new HashMap<>();

  public boolean addDynamicServlet(String path, HttpServlet servlet) {
    if (dynamicServlets.containsKey(path)) {
      return false;
    }

    dynamicServlets.put(path, servlet);
    return true;
  }

  public void removeDynamicServlet(String path) {
    dynamicServlets.remove(path);
  }

  private boolean runDynamic(String path, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    for(Map.Entry<String, HttpServlet> entry:dynamicServlets.entrySet()) {
      if (path.startsWith(entry.getKey())) {
        entry.getValue().service(request, response);
        return true;
      }
    }

    return false;
  }
  //TODO It looks like it would be safer to pass the servlet instance to remove here
  // http://stackoverflow.com/questions/5150730/jetty-dynamically-removing-the-registered-servlet
  // otherwise there is a change we might remove something needed by application
  public void removeServlet(String path) {
    Handler handlerToRemove = null;
    for(Handler handler:context.getHandlers()) {
      if(handler instanceof ServletHolder) {
        handlerToRemove = handler;
        break;
      }
    }

    if (handlerToRemove != null) {
      ServletHolder holder = (ServletHolder) handlerToRemove;
    }
  }

  public void addUploadServlet(String path, HttpServlet servlet) {
    ServletHolder holder = new ServletHolder(servlet);
    holder.getRegistration().setMultipartConfig(
            new MultipartConfigElement("")
    );
    context.addServlet(holder, WEB_INF + path + "/*");
  }

  public void addJerseyServlet(String path, Package pkg) {
    ResourceConfig config = new ResourceConfig()
            .packages(pkg.getName())
            .register(JacksonFeature.class);

    ServletContainer container = new ServletContainer(config);

    context.addServlet(new ServletHolder(container), WEB_INF + path + "/*");
  }


  public void setAjaxEntryPoint(WebServer owner, String path, Class clazz) {
    ResourceConfig config = new ResourceConfig()
            .registerClasses(clazz)
            .register(JacksonFeature.class);

    ServletContainer container = new ServletContainer(config);
    context.addServlet(new ServletHolder(container), WEB_INF + path + "/*");

    //owner.setAjaxEntryPoint(WEB_INF + path, clazz);
  }

  private final HttpServlet internalRouter = new HttpServlet() {

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      process(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      process(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      process(req, resp);
    }

    private void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      // The primary function of the default router is to check
      // for JSP pages, and forward to one if available
      String origPath = request.getRequestURI();
      String path = origPath;
      // if we get a path starting with WEB-INF then it means, the
      // resource could not be located
      if(path.startsWith(WEB_INF)) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND, "Page not found");
        return;
      }

      if (path.endsWith("/")) {
        path += "index";
      }

      String publicPath = PUBLIC + path + ".jsp";
      // Let's see if the given path corresponds to a publicly accessible page
      if(exists(publicPath)) {
        forward(publicPath, request, response, false);
        return;
      }

      String privatePath = PRIVATE + path + ".jsp";
      if (exists(privatePath)) {
        forward(privatePath, request, response, true);
      } else if (!runDynamic(origPath, request, response)) {
        // just forward the request to WEB-INF to let other
        // servlet handle the request if one is available
        forward(WEB_INF + origPath, request, response, false);
      }
    }
  };
//
//
//  public static void setSessionUser(HttpServletRequest request, User user) {
//    HttpSession session = request.getSession();
//    session.setAttribute(SESSION_USER, user);
//  }
//
//  public static User getSessionUser(HttpServletRequest request) throws AccessControlException {
//    // Check if we have a session
//    HttpSession session = request.getSession(false);
//    if (session == null) {
//      User user = tryRememberedUser(request, );
//      if (user == null) {
//        throw new AccessControlException();
//      } else {
//        return user;
//      }
//    }
//
//    // Check if we have a user on a session
//    User user = (User)session.getAttribute(SESSION_USER);
//    if (user == null) {
//      user = tryRememberedUser(request);
//      throw new AccessControlException();
//    }
//
//    return user;
//  }
//
//  public static User tryRememberedUser(HttpServletRequest request, UserSource source) {
//    for(Cookie cookie:request.getCookies()) {
//      if (cookie.getName().equals(COOKIE_REMEMBERED_USER)) {
//        String value = cookie.getValue();
//        String parts[] = value.split("-");
//
//        String machineId = parts[0];
//        String tokenId = parts[1];
//
//        return source.find(machineId, tokenId);
//      }
//    }
//
//    return null;
//  }

//
//  public Router(WebAppContext context, String path, String resource, UserSource userSource) throws WebServerException{
//    this.context = context;
//    if (path.equals("/")) {
//      this.path = "";
//    } else {
//      this.path = path;
//    }
//    URL url = ClassLoader.getSystemClassLoader().getResource(resource);
//    if (url == null) {
//      throw new WebServerException("The resource path '" + resource + "' is not available");
//    }
//    this.resource = resource;
//    this.resourceUrl = url.toExternalForm();
//    this.userSource = userSource;
//    this.random = new SecureRandom();
//
//  }

  public static class LoginException extends Exception {
    public LoginException(String message) {
      super(message);
    }
  }

  /**
   * Check if a return url is provided in which case redirect to that url
   * otherwise redirect to home
   *
   * @param request The HttpRequest object
   * @param response The HttpResponse object
   */
  private void redirect(HttpServletRequest request, HttpServletResponse response)
          throws IOException {
    String url = request.getParameter("return");
    if (url == null || url.isEmpty()) {
      url = "/";
    }

    response.sendRedirect(url);

  }

//  /**
//   * Try to login using the given request. The login parameters are expected to
//   * be provided by POST method on 'username', 'password' and 'remember'. If a
//   * login is successful, the method returns true, otherwise if its not capable
//   * to do the login, it returns false. If a problem occurs during login an
//   * exception is thrown with message
//   *
//   * @param request
//   * @return
//   */
//  private User tryLogin(HttpServletRequest request, HttpServletResponse response) throws LoginException {
//    String username = request.getParameter("username");
//    String password = request.getParameter("password");
//    String remember = request.getParameter("remember");
//
//    // Try to see if we can find a user with the given username
//    User user = userSource.find(username);
//    if (user == null) {
//      throw new LoginException("User not found");
//    }
//
//    if (!user.validatePassword(password)) {
//      throw new LoginException("Invalid password");
//    }
//
//    // we are through, let's see if we need to remember this user over longer
//    // sessions
//    if (remember != null && remember.trim().length() > 0) {
//
//      String machineId = new BigInteger(130, random).toString(32);
//      String id = user.remember(machineId);
//
//      response.addCookie(new Cookie("machine", machineId));
//      response.addCookie(new Cookie("remember", id));
//
//    }
//
//    return user;
//  }

  private User findRememberedUser(HttpServletRequest request) {
    // The cookies array is null in the very first request, need to handle that
    Cookie[] cookies = request.getCookies();
    if (cookies ==  null) {
      return null;
    }

    // Only if we have some cookies, then we go through it
    for(Cookie cookie:cookies) {
      if (cookie.getName().equals("user-remembered")) {
        String parts[] = cookie.getValue().split("-");
        if (parts.length != 2) {
          return null;
        }

        String machineId = parts[0];
        String rememberedId = parts[1];

        return userSource.find(parts[0], parts[1]);
      }
    }

    return null;
  }

  private boolean exists(String path) {
    System.out.println("Searching for " + resource + path);
    if (useFileSystem) {
      return new File(resource, path).exists();
    } else {
      return getClass().getClassLoader().getResource(resource + path) != null;
    }
  }

  public static class HttpSessionDelegation implements Session.Delegation {
    private final HttpSession httpSession;

    public HttpSessionDelegation(HttpSession httpSession) {
      this.httpSession = httpSession;
    }
    @Override
    public Object getAttribute(String name) {
      return httpSession.getAttribute(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
      httpSession.setAttribute(name, value);
    }
  }

  private void forward(String target, HttpServletRequest request, HttpServletResponse response, boolean privateAccess)
          throws IOException, ServletException {

    HttpSession httpSession = request.getSession();
    User user = httpSession != null ? (User) httpSession.getAttribute(SESSION_USER): null;
    if (user == null) {
      user = findRememberedUser(request);
    }

    // Let's check if we are trying to access a private page and a user has
    // not been defined, in which case, we will forward the request to a login page
    if (privateAccess && user == null) {
      // Redirect to the login page
      String returnUrl = request.getQueryString();
      if (returnUrl != null && returnUrl.length() > 0) {
        returnUrl = request.getRequestURI() + URLEncoder.encode("?" + returnUrl, "UTF-8");
      } else {
        returnUrl = request.getRequestURI();
      }

      response.sendRedirect("/login?returnUrl=" + returnUrl);
    } else {

      // Start application session
      Session session = Session.start(user, new HttpSessionDelegation(request.getSession()));

      try {
        // Pre-process the request, allowing the application to add certain
        // attributes to the request
        if (preProcessor != null) {
          preProcessor.preProcess(request);
        }

        // Handle the request
        request.getRequestDispatcher(target).forward(request, response);

        session.commit();
      } catch(Exception e) {
        e.printStackTrace();
        LOGGER.error("Error in JSP session", e);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        session.rollback();
      } finally {
        // End the application session
        session.end();
      }

    }
  }
}
