package net.symplifier.web.acl;

import net.symplifier.web.AjaxException;
import net.symplifier.web.Router;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * The user servlet is responsible for all user related activities
 *  - login, logout, reset password, remember user, revive user
 *  session, generate forgot token
 *
 * Created by ranjan on 8/12/15.
 */
public class UserAjax {
  private final UserSource userSource;
  public UserAjax(UserSource userSource) {
    this.userSource = userSource;
  }
//
//  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
//
//  }
//
//  @Override
//  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
//
//  }

  // Random number generator for getting a random token during remembering
  private final SecureRandom random = new SecureRandom();

  @POST
  @Path("/login")
  public String login(@FormParam("username") String username,
                      @FormParam("password") String password,
                      @FormParam("remember") Boolean remember,
                      @Context HttpServletRequest request,
                      @Context HttpServletResponse response) {
    User user = userSource.find(username);
    if (user == null) {
      throw new AjaxException(401, "Invalid username");
    }

    if (!user.validatePassword(password)) {
      throw new AjaxException(401, "Invalid password");
    }

    // Save the Session user
    request.getSession().setAttribute(Router.SESSION_USER, user);

    // if the remembered flag is set then we will remember this user

    // we are through, let's see if we need to remember this user over longer
    // sessions
    if (remember != null && remember) {

      String machineId = new BigInteger(130, random).toString(32);
      String id = user.remember(machineId);

      response.addCookie(new Cookie("machine", machineId));
      response.addCookie(new Cookie("remember", id));

    }

    return "OK";
  }

  @POST
  @Path("/forgot")
  public String forgot(@FormParam("email") String username, @Context HttpServletRequest request) {
    User user = userSource.find(username);
    if (user == null) {
      throw new AjaxException(401, "Email address not found");
    }

    System.out.println(request);
    userSource.forgotPassword(user, request.getRemoteAddr());

    return "OK";
  }

  @POST
  @Path("/reset")
  public String resetPassword(@FormParam("username") String username,
                              @FormParam("token") String token,
                              @FormParam("newPassword") String password,
                              @Context HttpServletRequest request) {
    User user = userSource.find(username);
    if (user == null) {
      throw new AjaxException(401, "Invalid reset token");
    }

    if (!userSource.resetPassword(user, request.getRemoteAddr(), token, password)) {
      throw new AjaxException(401, "Invalid reset token");
    }

    return "OK";
  }

  @POST
  @Path("/change")
  public String changePassword(@FormParam("old") String oldPassword,
                               @FormParam("new") String newPassword,
                               @Context HttpServletRequest request) {
    User user = (User)request.getSession().getAttribute(Router.SESSION_USER);
    if (user == null) {
      throw new AjaxException(401, "Session expired");
    }

    if (!user.validatePassword(oldPassword)) {
      throw new AjaxException(401, "Password is not correct");
    }

    userSource.changePassword(user, newPassword);

    return "OK";
  }


}
