package com.newrelic.fit.javax.servlet.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.newrelic.api.agent.Agent;
import com.newrelic.api.agent.Config;
import com.newrelic.api.agent.Logger;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Transaction;

/**
 * A singleton class that loads and runs servlet instrumentations.
 *
 * The {@link ServletInstrumentationManager} is a singleton object that is used
 * to run {@link ServletInstrumentation} instrumentations.
 *
 * @author sdewitt@newrelic.com
 */
public class ServletInstrumentationManager {

  /**
   * The New Relic Logger.
   */
  private static final Logger LOGGER = NewRelic.getAgent().getLogger();

  /**
   * The singleton {@link ServletInstrumentationManager} instance.
   */
  private static ServletInstrumentationManager MANAGER;

  /**
   * The list of discovered instrumentations.
   */
  private List<ServletInstrumentation> instrumentations;

  /**
   * The singleton accessor lock mutex.
   */
  private static final Object LOCK = new Object();

  /**
   * Get the singleton instance to use.
   *
   * @return the singleton instance to use.
   */
  public static ServletInstrumentationManager getInstance() {
    if (MANAGER != null) {
      return MANAGER;
    }

    synchronized (LOCK) {
      if (MANAGER == null) {
        MANAGER = new ServletInstrumentationManager();
        MANAGER.loadInstrumentations();
      }
    }

    return MANAGER;
  }

  /**
   * Load the instrumentation instances list.
   *
   * @see #getInstrumentations()
   */
  private void loadInstrumentations() {
    this.instrumentations = this.getInstrumentations();
  }

  /**
   * Return a list containing an instance of each registered instrumentation.
   *
   * If a particular instrumentation could not be created or fails to
   * initialize, a warning message will be logged.
   *
   * @return list of registered instrumentation instances.
   */

  public List<ServletInstrumentation> getInstrumentations() {
    final boolean isLoggingFiner = LOGGER.isLoggable(Level.FINER);
    final Config config = NewRelic.getAgent().getConfig();
    List<ServletInstrumentation> instrumentations = new ArrayList<ServletInstrumentation>();

    if (isLoggingFiner) {
      LOGGER.log(Level.FINER, "getInstrumentations() >> Entering");
    }

    // Grab the instrumentations array property from the YAML.  We expect it
    // to be a list of strings, each being a fully-qualified classname.
    List<String> classNames = Utilities.getStringList(config.getValue(
        "httpservlet_transaction_namer.instrumentations"
    ));

    // Iterate over each of the instrumentation classes and try to instantiate
    // them and initialize them.
    if (classNames == null) {
      LOGGER.log(
          Level.WARNING,
          "getInstrumentations() >> No instrumentations defined - Use \"instrumentations:\" in newrelic.yml with patterns in an indented list, or space-delimited string"
      );
      return instrumentations;
    }

    for (String className : classNames) {
      className = className.trim();
      try {
          // Load the class.  If it is an instance of ServletInstrumentation,
          // create a new instance and call init().
          if (isLoggingFiner) {
            LOGGER.log(
                Level.FINER,
                "getInstrumentations() >> Attempting to load instrumentation class {0}",
                className
            );
          }
          Class<?> clazz = Class.forName(className);
          if (ServletInstrumentation.class.isAssignableFrom(clazz)) {
            ServletInstrumentation instrumentation
              = (ServletInstrumentation) clazz.newInstance();
            instrumentation.init(config);
            instrumentations.add(instrumentation);
          }
      } catch (
          ClassNotFoundException |
          InstantiationException |
          IllegalAccessException e
      ) {
        LOGGER.log(
            Level.WARNING,
            e,
            "getInstrumentations() >> Instrumentation {0} could not be instantiated: {1}",
            className,
            e.getMessage()
        );
      }
    }

    if (isLoggingFiner) {
      for (ServletInstrumentation inst : instrumentations) {
        LOGGER.log(
            Level.FINER,
            "getInstrumentations() >> Instrumented {0}",
            inst.getClass().toGenericString()
        );
      }
      LOGGER.log(
          Level.FINER,
          "getInstrumentations() >> Exiting"
      );
    }

    return instrumentations;
  }

  /**
   * Apply all instrumentations to the {@code request}.
   *
   * Apply all the discovered instrumentations
   * to the {@link HttpServletRequest}.  If any instrumentation throws an
   * exception, it will be re-thrown from this method.
   *
   * @param request
   * @param response
   * @throws ServletException
   * @throws IOException
   */
  public void applyInstrumentations(
      HttpServletRequest request,
      HttpServletResponse response
  ) throws ServletException, IOException {
    final boolean isLoggingFiner = LOGGER.isLoggable(Level.FINER);
    final Agent agent = NewRelic.getAgent();
    final Transaction transaction = agent.getTransaction();

    if (isLoggingFiner) {
      LOGGER.log(Level.FINER, "applyInstrumentations() >> Entering");
    }

    // Iterate through each instrumentation instance and invoke it's
    // instrumentRequest() method.
    for (ServletInstrumentation instrumentation : this.instrumentations) {
      if (isLoggingFiner) {
        LOGGER.log(Level.FINER, "applyInstrumentations() >> Running instrumentation {0}",
            instrumentation.getClass().getName());
      }
      try {
        instrumentation.instrumentRequest(request, response, agent, transaction);
      } catch (
          ServletException |
          IOException e
      ) {
        LOGGER.log(
            Level.WARNING,
            e,
            "applyInstrumentations() >> Instrumentation {0} threw an exception: {1}",
            instrumentation.getClass().getName(),
            e.getMessage()
        );
      }
    }

    if (isLoggingFiner) {
      LOGGER.log(Level.FINER, "applyInstrumentations() >> Exiting");
    }
  }
}
