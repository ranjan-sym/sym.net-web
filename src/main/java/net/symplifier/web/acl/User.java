package net.symplifier.web.acl;

/**
 * Created by ranjan on 8/10/15.
 */
public interface User extends net.symplifier.core.application.User {

  /**
   * Check if the clear password text is correct or not for this User
   * @param password The clear password to check
   * @return {@code true} if the password is correct {@code false} otherwise
   */
  boolean validatePassword(String password);

  /**
   * Retrieve the role name of the given user
   * @return
   */
  String getRole();

  /**
   * Remember this user for the given machine name
   * @param machine The name of the machine to be remembered
   * @return The id used to identify the user for the given machine
   */
  String remember(String machine);
}
