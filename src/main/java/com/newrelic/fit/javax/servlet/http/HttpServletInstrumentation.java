package com.newrelic.fit.javax.servlet.http;

import java.io.IOException;
import java.util.logging.Level;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.newrelic.api.agent.Logger;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.weaver.MatchType;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.Weaver;

/**
 * Custom New Relic Agent Extension to instrument {@link HttpServlet}.
 * 
 * This extension weaves the {@link #service(ServletRequest, ServletResponse)}
 * method in this class into the {@link HttpServlet} class.  It adds the
 * logic to look for custom {@link ServletInstrumentation} instances and
 * execute their {@link ServletInstrumentation#instrumentRequest()} methods.
 * 
 * @author Scott DeWitt (sdewitt@newrelic.com)
 */
@Weave(
    originalName = "javax.servlet.http.HttpServlet",
    type = MatchType.BaseClass
)
public abstract class HttpServletInstrumentation {

  /**
   * The method to weave into {@link HttpServlet#service()}.
   * 
   * This method adds the logic to look for custom
   * {@link ServletInstrumentation} instances and execute their
   * {@link ServletInstrumentation#instrumentRequest()} methods.
   * 
   * @param request the {@link HttpServletRequest}.
   * @param response the {@link HttpServletResponse}.
   * 
   * @throws ServletException if a servlet exception occurs.
   * @throws IOException if an I/O error occurs.
   */
  @Trace(dispatcher = true)
  public void service(ServletRequest request, ServletResponse response)
      throws ServletException, IOException {
    final Logger logger = NewRelic.getAgent().getLogger();
    final boolean isLoggingFiner = logger.isLoggable(Level.FINER);

    if (isLoggingFiner) {
      logger.log(Level.FINER, "HttpServletInstrumentation >> Entering");
    }

    if (
        request instanceof HttpServletRequest
        && response instanceof HttpServletResponse
    ) {
      ServletInstrumentationManager.getInstance().applyInstrumentations(
          (HttpServletRequest) request,
          (HttpServletResponse) response
      );
    }

    if (isLoggingFiner) {
      logger.log(
          Level.FINER,
          "HttpServletInstrumentation >> Calling original HttpServlet.service() method"
      );
    }

    Weaver.callOriginal();

    if (isLoggingFiner) {
      logger.log(
          Level.FINER,
          "HttpServletInstrumentation >> Called original HttpServlet.service() method"
      );
    }
  }

}