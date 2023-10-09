package com.newrelic.fit.javax.servlet.http;

import java.util.Arrays;
import java.util.List;

/**
 * Common utilities for HTTPServlet-Transaction-Namer
 *
 * @author Seth Schwartzman (seth@newrelic.com)
 */
@SuppressWarnings("unchecked")
public class Utilities {

  // If it's a non-empty List of Strings
  // or a String (which it attempts to split via spaces),
  // returns a List of Strings.
  // Otherwise returns null.
  public static List<String> getStringList(Object toParse) {
    if (toParse == null) {
    	return null;
		} else if(toParse instanceof List) {
      List<Object> toParseListObj = (List<Object>) toParse;
      if(!toParseListObj.isEmpty() && toParseListObj.get(0) instanceof String) {
  			return (List<String>) toParse;
      }
      return null;
    } else if (toParse instanceof String) {
			String patternsStr = (String) toParse;
      return Arrays.asList(patternsStr.split("\\s+"));
		}
    return null;
  }

  // Used to check Booleans,
  // whether they come in as Booleans (from Yaml config)
  // or strings (from Java properties)
  public static Boolean checkBoolean(Object toCheck) {
		if(toCheck == null) {
			return false;
		} else if (toCheck instanceof Boolean) {
			return (Boolean)toCheck;
		} else if (toCheck instanceof String) {
			return ((String)toCheck).equalsIgnoreCase("true");
		} else {
			return false;
		}
	}
}
