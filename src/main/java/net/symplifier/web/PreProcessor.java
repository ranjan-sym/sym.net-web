package net.symplifier.web;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by ranjan on 9/28/15.
 */
public interface PreProcessor {
  void preProcess(HttpServletRequest request);
}
