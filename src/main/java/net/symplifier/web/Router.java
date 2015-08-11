package net.symplifier.web;

import net.symplifier.web.access.User;
import net.symplifier.web.access.UserSource;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.security.SecureRandom;

/**
 * Created by ranjan on 8/10/15.
 */
public class Router extends HttpServlet {
  public static final String ASSETS = "/assets";
  public static final String PAGES = "/pages";
  public static final String PRIVATE = PAGES + "/private";
  public static final String PUBLIC = PAGES + "/public";

  private final UserSource userSource;
  private final String path;
  private final String resourceUrl;
  private final SecureRandom random;
  private final String resource;

  public Router(String path, String resource, UserSource userSource) throws WebServerException{
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

  private boolean exists(String path) {
    System.out.println("Searching for " + resource + path);
    return getClass().getClassLoader().getResource(resource + path) != null;
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response)
          throws IOException, ServletException{
    String target = request.getRequestURI();
    if (target.equals("/login")) {
      try {
        User user = tryLogin(request, response);
        if (user != null) {
          HttpSession session = request.getSession(true);
          session.setAttribute("user", user);

          // redirect to a proper page
          redirect(request, response);
          return;
        }
      } catch(LoginException e) {
        request.setAttribute("message", e.getMessage());
      }
    }

    forward(target, request, response);
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
          throws IOException, ServletException {
    String target = request.getRequestURI();

    switch (target) {
      case "/":
        //Looks like we are trying to access the home page
        target = "/home";
        break;
      case "/logout":
        // Trying to logout
        // Destroy the session
        HttpSession session = request.getSession(false);
        if (session != null) {
          session.invalidate();
        }

        //redirect to a proper page
        redirect(request, response);
        return;
    }

    forward(target, request, response);

  }

  private void forward(String target, HttpServletRequest request, HttpServletResponse response)
          throws IOException, ServletException {
    // Let's see if we have an active session
    HttpSession activeSession = request.getSession(false);
    String page = null;
    User user = null;
    if (activeSession != null) {
      user = (User)activeSession.getAttribute("user");
      if (user != null) {
        // First we will see if we have a page specific to the user role
        page = PAGES + "_" + user.getRole() + target + ".jsp";
        if (!exists(page)) {
          // No role specific page found, so move on to a private page
          page = PRIVATE + target + ".jsp";
        }
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
      }
    } else {
      // We got a page to display, so will dispatch our request there
      //page = "assets/wscada/readme.txt";
      System.out.println(page);
      request.getRequestDispatcher(page).forward(request, response);
    }
  }
}
