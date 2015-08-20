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
  private final String path;
  private final String resourceUrl;
  private final SecureRandom random;
  private final String resource;

  public Router(String path, URL resource) {
    this(path, resource, "/pages");
  }

  public Router(String path, URL resource, String pagesPath) {
    assert(resource != null);

    // Create a web app context
    context = new WebAppContext();
    context.setContextPath(path);
    context.setWar(resource.toExternalForm());

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
    context.addServlet(new ServletHolder(new JspServlet()), WEB_INF + pagesPath + "/*");
  }

  public void addStaticRoute(String path, URL resource) {
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
      super.doGet(req, resp);


    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      super.doPost(req, resp);
    }

    private void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      // The primary function of the default router is to check
      // for JSP pages, and forward to one if available

      String path = request.getRequestURI();
      // if we get a path starting with WEB-INF then in means, the
      // resource could not be located
      if(path.startsWith(WEB_INF)) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND, "Page not found");
        return;
      }

      String publicPath = PUBLIC + path;
      // Let's see if the given path corresponds to a publicly accessible page
      if(exists(publicPath)) {
        forward(publicPath, request, response, false);
        return;
      }

      String privatePath = PRIVATE + path;
      if (exists(privatePath)) {
        forward(privatePath, request, response, true);
      } else {
        // just forward the request to WEB-INF to let other
        // servlet handle the request if one is available
        forward(WEB_INF + path, request, response, false);
      }
    }
  };


  public static void setSessionUser(HttpServletRequest request, User user) {
    HttpSession session = request.getSession();
    session.setAttribute(SESSION_USER, user);
  }

  public static User getSessionUser(HttpServletRequest request) throws AccessControlException {
    // Check if we have a session
    HttpSession session = request.getSession(false);
    if (session == null) {
      User user = tryRememberedUser(null, request);
      if (user == null) {
        throw new AccessControlException();
      } else {
        return user;
      }
    }

    // Check if we have a user on a session
    User user = (User)session.getAttribute(SESSION_USER);
    if (user == null) {
      user = tryRememberedUser(session, request);
      throw new AccessControlException();
    }

    return user;
  }

  public static User tryRememberedUser(HttpSession session, HttpServletRequest request, UserSource source) {
    for(Cookie cookie:request.getCookies()) {
      if (cookie.getName().equals(COOKIE_REMEMBERED_USER)) {
        String value = cookie.getValue();
        String parts[] = value.split("-");

        String machineId = parts[0];
        String tokenId = parts[1];

        return source.find(machineId, tokenId);
      }
    }

    return null;
  }


  public Router(WebAppContext context, String path, String resource, UserSource userSource) throws WebServerException{
    this.context = context;
    if (path.equals("/")) {
      this.path = "";
    } else {
      this.path = path;
    }
    URL url = ClassLoader.getSystemClassLoader().getResource(resource);
    if (url == null) {
      throw new WebServerException("The resource path '" + resource + "' is not available");
    }
    this.resource = resource;
    this.resourceUrl = url.toExternalForm();
    this.userSource = userSource;
    this.random = new SecureRandom();

  }

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
    for(Cookie cookie:request.getCookies()) {
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
    return getClass().getClassLoader().getResource(resource + path) != null;
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

  private void forward(String target, HttpServletRequest request, HttpServletResponse response)
          throws IOException, ServletException {
    HttpSession session = request.getSession();
    User user = (User) session.getAttribute(SESSION_USER);

    Session.start(user, new HttpSessionDelegation(request.getSession()));

    // Let's see if we have an active session
    HttpSession activeSession = request.getSession(false);
    String page = null;
    User user = null;
    if (activeSession != null) {
      user = (User) activeSession.getAttribute("user");
    }

    // Try to find a user from a remembered token
    if (user == null) {
      user = findRememberedUser(request);
    }

    if (user != null) {
      // First we will see if we have a page specific to the user role
      page = PAGES + "_" + user.getRole() + target + ".jsp";
      if (!exists(page)) {
        // No role specific page found, so move on to a private page
        page = PRIVATE + target + ".jsp";
      }
    }

    // Let's see if we need to fall back to a public page
    if (page == null || !exists(page)) {
      page = PUBLIC + target + ".jsp";
    }

    if (!exists(page)) {
      // Let's see if page is actually a private page and a user could not be
      // found
      if (user == null && exists(PRIVATE + target + ".jsp")) {
        // no proper access, so we will redirect to the login page
        request.getRequestDispatcher("/login").forward(request, response);
      } else {
        // We got a 404 error here
        System.out.println("Page not found");
        request.getRequestDispatcher("/WEB-INF" + target).forward(request, response);
      }
    } else {
      // We got a page to display, so will dispatch our request here
      //page = "assets/wscada/readme.txt";
      System.out.println(page);
      request.getRequestDispatcher(page).forward(request, response);
    }
  }
}
