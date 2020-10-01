package com.newrelic.fit.javax.servlet.http;

import com.newrelic.agent.deps.org.slf4j.Logger;
import com.newrelic.agent.deps.org.slf4j.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test harness for standard "Namer" instrumentation for HTTPServlet-Trasnaction-namer
 *
 * @author Craig Shanks (cshanks@newrelic.com)
 */
class TransactionNamerTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(TransactionNamerTest.class);
  private LinkedHashMap<String, TransactionNamer.Obfuscation> obfuscationPatterns = new LinkedHashMap<>();

  @BeforeEach
  void setUp() {

    String pattern = "(?<obfuscatedVin>[A-Za-z\\d]{11}\\d{6})";
    System.out.println("pattern: " + pattern);

    List<String> txnPatterns = new ArrayList<>();
    txnPatterns.add(pattern);

    for (String thisPattern : txnPatterns) {
      if (obfuscationPatterns.containsKey(thisPattern)) {
        continue;
      }

      String[] patternSplit;
      String fixedPattern = "";

      if (thisPattern.matches("\\(\\?<\\w+>.*\\)")) {
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

      obfuscationPatterns.put(thisPattern, new TransactionNamer.Obfuscation(patternSplit, Pattern.compile(fixedPattern)));
    }
  }

  @AfterEach
  void tearDown() {
  }

  @Test
  void initObfuscations() {
    // In setUp method
  }

  @Test
  void obfuscateURI() {
    // Expect pass
    String URI = "/vehicleimage/WV1ZZZ7HZHH161837/etc";
    assertEquals(obfuscateURI(URI), "/vehicleimage/<obfuscatedVin>/etc");

    // Expect fail
    URI = "/vehicleimage/NOTAVINHZHH16183F/etc";
    assertEquals(obfuscateURI(URI), "/vehicleimage/NOTAVINHZHH16183F/etc");
  }

  String obfuscateURI(String URI) {
    LOGGER.debug("HTTPServlet-transaction-namer - Name Obfuscator - testing URI: " + URI);

    String outputURI = "";

    for (TransactionNamer.Obfuscation aPattern : obfuscationPatterns.values()) {

      if (aPattern.getPattern() != null && aPattern.getPattern().pattern().matches("\\(\\?<\\w+>.*\\)")) {

        // Named group replacement
        LOGGER.debug("HTTPServlet-transaction-namer - Name Obfuscator - Checking against pattern: " + aPattern.getPattern().toString());
        String namedGroupRegex = "\\(\\?<(\\w+)>(.*)\\)";
        Pattern namedGroupPattern = Pattern.compile(namedGroupRegex);
        Matcher namedGroupMatcher = namedGroupPattern.matcher(aPattern.getPattern().pattern());
        if (namedGroupMatcher.matches() && namedGroupMatcher.groupCount() == 2) {
          String name = namedGroupMatcher.group(1);
          String pattern = namedGroupMatcher.group(2);
          outputURI = URI.replaceAll(pattern, "<" + name + ">");
        }

      } else {

        String[] URISplit = URI.split("\\/");
        if (aPattern.getSplitPatternLength() > URISplit.length) {
          continue;
        }

        String[] splitPattern = aPattern.getSplitPattern();
        LOGGER.debug("HTTPServlet-transaction-namer  - Name Obfuscator - Checking against pattern: " + aPattern.getPattern().toString());
        Matcher URIMatcher = aPattern.getPattern().matcher(URI);
        if (URIMatcher.find()) {
          LOGGER.debug("HTTPServlet-transaction-namer - Name Obfuscator - URI matched!");
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
      LOGGER.debug("HTTPServlet-transaction-namer - Name Obfuscator - named group regex did not match: " + URI);
      return URI;
    } else {
      LOGGER.debug("HTTPServlet-transaction-namer - Name Obfuscator - named group regex matched: " + outputURI);
      return outputURI;
    }
  }
}
