package net.symplifier.web.acl;

/**
 * Created by ranjan on 8/10/15.
 */
public interface UserSource {

  User find(String username);

  User find(String machine, String id);
}
