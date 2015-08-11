package net.symplifier.web.access;

/**
 * Created by ranjan on 8/10/15.
 */
public interface UserSource {
  User find(String username);
}
