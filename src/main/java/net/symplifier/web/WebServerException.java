package net.symplifier.web;

/**
 * Created by ranjan on 7/24/15.
 */
public class WebServerException extends Exception {
  public WebServerException(String message) {
    super(message);
  }

  public WebServerException(Throwable cause) {
    super(cause);
  }

  public WebServerException(String message, Throwable cause) {
    super(message, cause);
  }
}
