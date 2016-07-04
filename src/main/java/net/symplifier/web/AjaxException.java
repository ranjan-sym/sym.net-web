package net.symplifier.web;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Created by ranjan on 9/25/15.
 */
public class AjaxException extends WebApplicationException {
  public AjaxException(int status, String message) {
    super(Response.status(status)
            .type(MediaType.TEXT_PLAIN)
            .entity(message).build());
  }
}
