package net.symplifier.web;

import net.symplifier.core.application.Session;
import net.symplifier.web.acl.AccessControlException;
import net.symplifier.web.acl.User;
import net.symplifier.web.acl.UserSource;
import org.apache.jasper.servlet.JspServlet;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.security.SecureRandom;

/**
 * Created by ranjan on 8/10/15.
 */
public class Router {
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
  private final SecureRandom random;
  private final boolean useFileSystem;

  public Router(UserSource userSource, String path, String resource) {
    this(userSource, path, resource, false, PAGES);
  }

  public Router(UserSource userSource, String path, File wwwRoot) {
    this(userSource, path, wwwRoot.getAbsolutePath(), true, PAGES);
  }

  public Router(UserSource userSource, String path, String resource, boolean useFileSystem, String pagesPath) {
    assert(resource != null);

    this.userSource = userSource;
    this.resource = resource;
    this.useFileSystem = useFileSystem;
    this.random = new SecureRandom();
    String absolutePath = useFileSystem ?
            resource :
            getClass().getClassLoader().getResource(resource).toExternalForm();

    // Create a web app context
    context = new WebAppContext();
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

  public WebAppContext getContext() {
    return context;
  }

  public void addStaticRoute(String path) {
    context.addServlet(new ServletHolder(new DefaultServlet()), path + "/*");
  }

  public void addServlet(String path, HttpServlet servlet) {
    context.addServlet(new ServletHolder(servlet), WEB_INF + path + "/*");
  }

  public void addJerseyServlet(String path, Package pkg) {
    ResourceConfig config = new ResourceConfig()
            .packages(pkg.getName())
            .register(JacksonFeature.class);

    ServletContainer container = new ServletContainer(config);

    context.addServlet(new ServletHolder(container), WEB_INF + path + "/*");
  }

  private final HttpServlet internalRouter = new HttpServlet() {
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

      String path = request.getRequestURI();
      // if we get a path starting with WEB-INF then it means, the
      // resource could not be located
      if(path.startsWith(WEB_INF)) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND, "Page not found");
        return;
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
      } else {
        // just forward the request to WEB-INF to let other
        // servlet handle the request if one is available
        forward(WEB_INF + path, request, response, false);
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

  /**
   * Try to login using the given request. The login parameters are expected to
   * be provided by POST method on 'username', 'password' and 'remember'. If a
   * login is successful, the method returns true, otherwise if its not capable
   * to do the login, it returns false. If a problem occurs during login an
   * exception is thrown with message
   *
   * @param request
   * @return
   */
  private User tryLogin(HttpServletRequest request, HttpServletResponse response) throws LoginException {
    String username = request.getParameter("username");
    String password = request.getParameter("password");
    String remember = request.getParameter("remember");

    // Try to see if we can find a user with the given username
    User user = userSource.find(username);
    if (user == null) {
      throw new LoginException("User not found");
    }

    if (!user.validatePassword(password)) {
      throw new LoginException("Invalid password");
    }

    // we are through, let's see if we need to remember this user over longer
    // sessions
    if (remember != null && remember.trim().length() > 0) {

      String machineId = new BigInteger(130, random).toString(32);
      String id = user.remember(machineId);

      response.addCookie(new Cookie("machine", machineId));
      response.addCookie(new Cookie("remember", id));

    }

    return user;
  }

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

  private static class HttpSessionDelegation implements Session.Delegation {
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
      response.sendRedirect("/login?returnUrl=" + request.getRequestURI());
    } else {

      // Start application session
      Session session = Session.start(user, new HttpSessionDelegation(request.getSession()));

      // Handle the request
      request.getRequestDispatcher(target).forward(request, response);

      // End the application session
      session.end();

    }
  }
}
