package com.newrelic.fit.javax.servlet.http;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.newrelic.api.agent.Agent;
import com.newrelic.api.agent.Config;
import com.newrelic.api.agent.Transaction;

/**
 * Contract for objects which provide custom servlet request instrumentations.
 * 
 * @author sdewitt@newrelic.com
 */
public interface ServletInstrumentation {

  /**
   * Initialize the instrumentation.
   * 
   * This method is called once per JVM by the
   * {@link ServletInstrumentationManager} singleton.  The instrumentation
   * may use configuration from the New Relic agent configuration provided
   * in the {@code config} object to check for instrumentation specific
   * configuration.
   *  
   * @param config The New Relic Agent configuration.
   */
  void init(Config config);

  /**
   * Instrument the specified servlet {@code request}.
   * 
   * @param request the HTTP servlet request.
   * @param response the HTTP servlet response.
   * @param agent the New Relic Agent API.
   * @param transaction the current New Relic {@link Transaction}.
   * 
   * @throws ServletException if a servlet exception occurrs.
   * @throws IOException if an I/O error occurs.
   */
  void instrumentRequest(
      HttpServletRequest request,
      HttpServletResponse response, 
      Agent agent,
      Transaction transaction
  ) throws ServletException, IOException;

}
