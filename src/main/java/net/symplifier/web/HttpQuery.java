package net.symplifier.web;

import javax.servlet.http.HttpServletRequest;

/**
 * A HelperClass for use within JSPs and Servlets for data retrieval
 *
 * Created by ranjan on 8/14/15.
 */
public class HttpQuery {
  public static String p(HttpServletRequest req, String parameter, String defaultValue) {
    String value = req.getParameter(parameter);
    if (value == null) {
      return defaultValue;
    } else {
      return value;
    }
  }

  public static String[] a(HttpServletRequest req, String parameter, String defaultValue) {
    String[] values = req.getParameterValues(parameter);
    for(int i=0; i<values.length; ++i) {
      if (values[i] == null) values[i]=defaultValue;
    }
    return values;
  }

  public static int p(HttpServletRequest req, String parameter, int defaultValue) {
    String value = req.getParameter(parameter);
    if (value == null) {
      return defaultValue;
    } else {
      try {
        return Integer.parseInt(value);
      } catch(NumberFormatException e) {
        return defaultValue;
      }
    }
  }

  public static int[] a(HttpServletRequest req, String parameter, int defaultValue) {
    String[] values = req.getParameterValues(parameter);
    int[] res = new int[values.length];
    for(int i=0; i<values.length; ++i) {
      try {
        res[i] = Integer.parseInt(values[i]);
      } catch(NumberFormatException e) {
        res[i] = defaultValue;
      }
    }
    return res;
  }
}
