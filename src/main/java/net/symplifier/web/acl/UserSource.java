package net.symplifier.web.acl;

/**
 * Created by ranjan on 8/10/15.
 */
public interface UserSource {

  User find(String username);

  User find(String machine, String id);

  void forgotPassword(User user, String remoteAddr);

  boolean resetPassword(User user, String remoteAddr, String token, String newPassword);

  /**
   * Changes the password for the given user.
   *
   * @param user The {@link User} whose password needs to be changed
   * @param password The new password.
   */
  void changePassword(User user, String password);
}
