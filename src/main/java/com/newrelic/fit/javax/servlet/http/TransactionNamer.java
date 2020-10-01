package com.newrelic.fit.javax.servlet.http;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.newrelic.api.agent.Agent;
import com.newrelic.api.agent.Config;
import com.newrelic.api.agent.Logger;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Transaction;
import com.newrelic.api.agent.TransactionNamePriority;

/**
 * Standard "Namer" instrumentation for HTTPServlet-Trasnaction-namer
 * Performs the following 3 transformations on transactions:
 * * Appending HTTP parameters to transaction names
 * * Grouping transaction naes by URI
 * * Obfuscating portions of URIs in transaction names
 *
 * @author Seth Schwartzman (seth@newrelic.com)
 * @author Prakash Reddy (preddy@newrelic.com)
 * @author Scott Dewitt (sdewitt@newrelic.com)
 * @author Craig Shanks (cshanks@newrelic.com)
 */
public class TransactionNamer implements ServletInstrumentation {

	public static class Obfuscation {

		private String[] splitPattern;
		private Pattern pattern;

		public Obfuscation(String[] spat, Pattern pat) {
			setPattern(pat);
			setSplitPattern(spat);
		}

		public Pattern getPattern() {
			return pattern;
		}

		public String[] getSplitPattern() {
			return splitPattern;
		}

		public int getSplitPatternLength() {
			return splitPattern.length;
		}

		public void setPattern(Pattern pat) {
			pattern = pat;
		}

		public void setSplitPattern(String[] spat) {
			splitPattern = spat;
		}
	}

	public class Parameter {

		private String name;
		private String type;

		public Parameter(String pname, String ptype) {
			setName(pname);
			setType(ptype);
		}

		public String getName() {
			return name;
		}

		public int getParamHash() {
			return (name + type).hashCode();
		}

		public String getType() {
			return type;
		}

		public void setName(String pname) {
			name = pname;
		}

		public void setType(String ptype) {
			type = ptype;
		}
	}

	private LinkedHashMap<Integer, Parameter> parametersToAppend = new LinkedHashMap<Integer, Parameter>();
	private LinkedHashMap<String, Obfuscation> obfuscationPatterns = new LinkedHashMap<String, Obfuscation>();
	private LinkedList<Pattern> groupingPatterns = new LinkedList<Pattern>();

	private static final Logger LOGGER = NewRelic.getAgent().getLogger();

	public String appendParameters(HttpServletRequest request) {
		String appendParams = "";
		for (Parameter thisParam : parametersToAppend.values()) {
			String paramType = thisParam.getType();
			String paramName = thisParam.getName();
			LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Append Parameters - Getting parameter type: " + paramType + " name: " + paramName);
			String param = null;
			if (paramType.equals("header")) {
				param = request.getHeader(paramName);
			} else if (paramType.equals("cookie")) {
				Cookie[] requestCookies = request.getCookies();
				if (requestCookies != null) {
					for (Cookie cIsForCookie : requestCookies) {
						if (cIsForCookie.getName().equalsIgnoreCase(paramName)) {
							param = cIsForCookie.getValue();
						}
					}
				}
			} else if (paramType.equals("parameter")) {
				param = request.getParameter(paramName);
			} else {
				LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Append Parameters - parameter type is not one of [cookie, header, parameter]");
			}

			if ((param != null) && !param.isEmpty()) {
				LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Append Parameters - appending parameter value to transaction name: " + param);
				appendParams += param + "/";
			}
		}

		if (appendParams.endsWith("/")) {
			return appendParams.substring(0, appendParams.length() - 1);
		} else {
			return appendParams;
		}
	}

	public String groupURI(String URI) {
		LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Grouper - Grouping URI: " + URI);
		for (Pattern aPattern : groupingPatterns) {
			LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Grouper - Checking against pattern: " + aPattern.toString());
			Matcher URIMatcher = aPattern.matcher(URI);
			if (URIMatcher.find()) {
				LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Grouper - URI matched!");
				String outputURI = "";
				for (int i = 1; i <= URIMatcher.groupCount(); i++) {
					outputURI += URIMatcher.group(i);
				}
				if (outputURI.isEmpty()) {
					LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Grouper - URI matched but not grouped.");
					LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Grouper - Check syntax of this pattern in newrelic.yml.");
				} else {
					LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Grouper - Grouped URI: " + outputURI);
					return outputURI;
				}
			}
		}
		LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Grouper - No (or group-less) matches for URI: " + URI);
		return URI;
	}

	private void initGroupings(Config nrConfig) {
		LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Grouper - initializing grouping patterns.");
		LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Obfuscator - initializing obfuscation patterns.");
		List<String> txnPatterns = Utilities.getStringList(nrConfig.getValue("httpservlet_transaction_namer.name_obfuscator.patterns"));
		if (txnPatterns == null) {
			LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Grouper - grouping patterns not defined.");
			LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Grouper - use \"patterns:\" in newrelic.yml with patterns in an indented list, or a space-delimited string.");
			return;
		}

		for (String thisPattern : txnPatterns) {
			try {
				Pattern thisGrouping = Pattern.compile(thisPattern);
				if (!groupingPatterns.contains(thisGrouping)) {
					groupingPatterns.add(thisGrouping);
				}
			} catch (Exception e) {
				LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Grouper - invalid pattern: " + thisPattern);
			}
		}
	}

	private void initObfuscations(Config nrConfig) {
		LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Obfuscator - initializing obfuscation patterns.");
		List<String> txnPatterns = Utilities.getStringList(nrConfig.getValue("httpservlet_transaction_namer.name_obfuscator.patterns"));
		if (txnPatterns == null) {
			LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Obfuscator - obfuscation patterns not defined.");
			LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Obfuscator - use \"patterns:\" in newrelic.yml with patterns in an indented list, or a space-delimited string.");
			return;
		}

		for (String thisPattern : txnPatterns) {
			if (obfuscationPatterns.containsKey(thisPattern)) {
				continue;
			}
      String[] patternSplit;
      String fixedPattern = "";
      if (thisPattern.matches("\\(\\?<\\w+>.*\\)")) {
        LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Obfuscator - named group replacement defined.");
        patternSplit = null;
        fixedPattern += thisPattern;
      } else {
        patternSplit = thisPattern.split("/");
        for (int i = 0; i < patternSplit.length; i++) {
          if (patternSplit[i].startsWith("<") && patternSplit[i].endsWith(">")) {
            if (patternSplit[i].contains(",")) {
              String[] commaSplitPattern = patternSplit[i].split(",");
              fixedPattern += commaSplitPattern[1].substring(0, commaSplitPattern[1].length() - 1);
              patternSplit[i] = commaSplitPattern[0] + ">";
            } else {
              fixedPattern += "[^/]+";
            }
          } else {
            fixedPattern += patternSplit[i];
          }
          if (patternSplit.length > (i + 1)) {
            fixedPattern += "/";
          }
        }
      }
			obfuscationPatterns.put(thisPattern, new Obfuscation(patternSplit, Pattern.compile(fixedPattern)));
		}
	}

	@SuppressWarnings("unchecked")
	private void initAppendParameters(Config nrConfig) {
		Object paramsObj = nrConfig.getValue("httpservlet_transaction_namer.append_parameters.parameters");

		if ((paramsObj == null) || !(paramsObj instanceof List)) {
			LOGGER.log(Level.SEVERE, "HTTPServlet-transaction-namer - Append Parameters - parameters not defined.");
			LOGGER.log(Level.SEVERE, "HTTPServlet-transaction-namer - Append Parameters - use \"parameters:\" in newrelic.yml with parameters in an indented list.");
			return;
		}
		for (Object thisParamObj : (List<Object>) paramsObj) {
			if ((thisParamObj != null) && (thisParamObj instanceof Map)) {
				LinkedHashMap<String, String> thisParamMap = (LinkedHashMap<String, String>) thisParamObj;
				if ((thisParamMap != null) && thisParamMap.containsKey("name") && thisParamMap.containsKey("type")) {
					String name = thisParamMap.get("name");
					String type = thisParamMap.get("type");
					int paramHashCode = (name + type).hashCode();
					parametersToAppend.put(paramHashCode, new Parameter(name, type));
				} else {
					LOGGER.log(Level.SEVERE, "HTTPServlet-transaction-namer - Append Parameters - incorrect syntax for parameter. use \"name:\" and \"type:\" for each parameter in an indented list.");
				}
			} else {
				LOGGER.log(Level.SEVERE, "HTTPServlet-transaction-namer - Append Parameters - incorrect syntax for parameter. use \"name:\" and \"type:\" for each parameter in an indented list.");
			}
		}
	}

	public boolean isGroupingEnabled() {
		return !groupingPatterns.isEmpty();
	}

	public boolean isObfuscationEnabled() {
		return !obfuscationPatterns.isEmpty();
	}

	public boolean isParameterAppendingEnabled() {
		return !parametersToAppend.isEmpty();
	}

	public String obfuscateURI(String URI) {
		LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Obfuscator - checking URI: " + URI);

		String outputURI = "";
		for (Obfuscation aPattern : obfuscationPatterns.values()) {
      LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Obfuscator - checking against pattern: " + aPattern.getPattern().toString());
      if (aPattern.getPattern() != null && aPattern.getPattern().pattern().matches("\\(\\?<\\w+>.*\\)")) {
        // Named group replacement
        String namedGroupRegex = "\\(\\?<(\\w+)>(.*)\\)";
        Pattern namedGroupPattern = Pattern.compile(namedGroupRegex);
        Matcher namedGroupMatcher = namedGroupPattern.matcher(aPattern.getPattern().pattern());
        if (namedGroupMatcher.matches() && namedGroupMatcher.groupCount() == 2) {
					LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Obfuscator - URI matched named group: " + namedGroupMatcher.group(1) + ", pattern: " + namedGroupMatcher.group(2));
					String name = namedGroupMatcher.group(1);
          String pattern = namedGroupMatcher.group(2);
          outputURI = URI.replaceAll(pattern, "<" + name + ">");
					break;
        }
      } else {
        String[] splitPattern = aPattern.getSplitPattern();
        Matcher URIMatcher = aPattern.getPattern().matcher(URI);
        if (URIMatcher.find()) {
          LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Obfuscator - URI matched pattern: " + aPattern.getPattern().toString());
					String[] URISplit;
					if(URI.startsWith("/")) {
						outputURI += "/";
						URISplit = URI.substring(1).split("\\/");
					} else {
						URISplit = URI.split("\\/");
					}
					if (aPattern.getSplitPatternLength() > URISplit.length) {
						continue;
					}
					for (int i = 0; i < URISplit.length; i++) {
            if (splitPattern[i].startsWith("<") && splitPattern[i].endsWith(">")) {
              outputURI += splitPattern[i];
					  } else {
              outputURI += URISplit[i];
					  }
            if (URISplit.length > (i + 1)) {
              outputURI += "/";
					  }
          }
          break;
        }
      }
		}

		if (outputURI.isEmpty()) {
      LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Obfuscator - no patterns matched to: " + URI);
			return URI;
		}

		LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Name Obfuscator - obfuscated URI: " + outputURI);
		return outputURI;
	}

	@Override
	public void init(Config nrConfig) {
		LOGGER.log(Level.INFO, "HTTPServlet-transaction-namer - Initializing.");
		if (Utilities.checkBoolean(nrConfig.getValue("httpservlet_transaction_namer.append_parameters.enabled"))) {
			LOGGER.log(Level.INFO, "HTTPServlet-transaction-namer - Append Parameters - Enabled.");
			initAppendParameters(nrConfig);
		}
		if (Utilities.checkBoolean(nrConfig.getValue("httpservlet_transaction_namer.name_obfuscator.enabled"))) {
			LOGGER.log(Level.INFO, "HTTPServlet-transaction-namer - Name Obfuscator - Enabled.");
			initObfuscations(nrConfig);
		}
		if (Utilities.checkBoolean(nrConfig.getValue("httpservlet_transaction_namer.name_grouper.enabled"))) {
			LOGGER.log(Level.INFO, "HTTPServlet-transaction-namer - Name Grouper - Enabled.");
			initGroupings(nrConfig);
		}
	}

	@Override
	public void instrumentRequest(
		HttpServletRequest request,
		HttpServletResponse response,
		Agent agent,
		Transaction transaction
	) throws ServletException, IOException {
		LOGGER.log(Level.FINER, "HTTPServlet-transaction-namer - Activated for this request.");
		String txnAppend = "";
		String URI = request.getRequestURI();

		if(isGroupingEnabled()) {
			URI = groupURI(URI);
		}

		if(isObfuscationEnabled()) {
			URI = obfuscateURI(URI);
			// If the request.uri attribute is not excluded, obfuscate it on the Transaction Event
			// As of Sept 20, 2019 the UI still populates fields labelled 'URI' and 'HTTP referer' with the non-obfuscated values
			NewRelic.addCustomParameter("request.uri", URI);
			// If request.ui is excluded, create an obfuscated custom attribute on the Transaction Event
			NewRelic.addCustomParameter("custom.request.uri", URI);

			String referer = request.getHeader("referer");
			if (referer != null) {
				String obfuscatedReferer = obfuscateURI(referer);
				// If the request.headers.referer attribute is not excluded, obfuscate it on the Transaction Event
				// As of Sept 20, 2019 the UI still populates fields labelled 'URI' and 'HTTP referer' with the non-obfuscated values
				NewRelic.addCustomParameter("request.headers.referer", obfuscatedReferer);
				// If request.headers.referer is excluded, create an obfuscated custom attribute on the Transaction Event
				NewRelic.addCustomParameter("custom.request.headers.referer", obfuscatedReferer);
			}
		}

		if(isParameterAppendingEnabled()) {
			txnAppend = appendParameters(request);
		}

		if (URI != null && !URI.isEmpty()) {
			if(txnAppend == null ||  txnAppend.isEmpty()) {
				LOGGER.log(Level.FINER,
					"HTTPServlet-transaction-namer - setting transaction name to: " + URI);
				transaction.setTransactionName(TransactionNamePriority.CUSTOM_HIGH,
					false, "HTTPServlet", URI);
			} else {
				LOGGER.log(Level.FINER,
					"HTTPServlet-transaction-namer - setting transaction name to: " + URI + "/" + txnAppend);
				transaction.setTransactionName(TransactionNamePriority.CUSTOM_HIGH,
					false, "HTTPServlet", URI, txnAppend);
			}
		}
	}
}
