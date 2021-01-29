[![New Relic Experimental header](https://github.com/newrelic/opensource-website/raw/master/src/images/categories/Experimental.png)](https://opensource.newrelic.com/oss-category/#new-relic-experimental)

# New Relic Java Agent - HTTPServlet Transaction Namer

A New Relic Java Agent extension that provides the ability to add custom instrumentation around the J2EE `javax.servlet.http.HttpServlet#service()` method.

**Historical Note:** Historically this extension only provide the naming functionality, which is why the extension is named 'HTTPServlet-transaction-namer'.

## Installation

To begin using this extension:

1. Obtain the distribution from New Relic Expert Services.  The distributable is named httpservlet-transaction-namer-VERSION.zip.
1. Extract the distribution into a local directory.  The contents of the distributable are as follows.

    | Asset | Description |
    | --- | --- |
    | httpservlet-transaction-namer-2.1.jar | The extension JAR file |
    | README.pdf | The PDF instructions for usage |
    | javadoc/* | The public API documentation |

1. Transfer the extension JAR file to the target server
    ```sh
    scp ./httpservlet-transaction-namer-2.1/httpservlet-transaction-namer-2.1.jar myserver.com:/home/me
    ```
1. Copy the extension JAR file into the agent's `extensions` directory (relative to the directory containing the `newrelic.jar` file).
    *Note:* Create the `extensions` directory if it does not exist.
    ```sh
    cp /home/me/httpservlet-transaction-namer-2.1.jar /opt/newrelic/extensions
    ```
1. Add the appropriate settings to `newrelic.yml` as described in the [Configuration](#configuration) section.
1. Restart your JVM
1. After the app has reloaded, generate traffic against your app that will trigger transactions that you expect to see renamed.
1. To debug issues, set `log_level` to `finer` in `newrelic.yml`.

## Getting Started

By default, the built-in [`TransactionNamer`](#httpservlet-transaction-namer) instrumentation is registered. This instrumentation can be used to alter the way in which the `Transaction` name is set for a Servlet request.

## Usage

## Configuration

All configuration of this extension is done in `newrelic.yml` or alternatively, via [java properties](https://docs.newrelic.com/docs/agents/java-agent/configuration/java-agent-configuration-config-file#System_Properties).

1. **You must disable auto transaction naming.** Find the parameter called `enable_auto_transaction_naming` and set it to `false`.
1. Enable the extension you copy the YAML snippet from [the configuration template below](#configuration-template) and paste it within the `common:` section of the `newrelic.yml`. *Note:* It can be anywhere within the common section (for example, below the app_name parameter).
1. See the [renaming options](#transactionnamer-renaming-options) to determine which to use.
1. Ensure that the indentation levels of the `httpservlet_transaction_namer` section match exactly to the way they appear in the template below. **Every indentation in YAML is 2 spaces (_NOT_ tabs)**. The `httpservlet_transaction_namer:` line should have exactly 2 spaces in front of it, the next line should have 4, and so on.

### Configuration Template

```yaml
  httpservlet_transaction_namer:
    instrumentations:
      - com.newrelic.fit.javax.servlet.http.TransactionNamer
    append_parameters:
      enabled: true
      parameters:
        - name: categoryId
          type: parameter
        - name: host
          type: header
    name_grouper:
      enabled: true
      patterns:
        - '(\/wps\/myportal\/[^!]*)!ut.*'
        - '(\/jpetstore_web\/[^.]*)\..*'
        - '(\/jpetstore_web\/accounts\/)[^\/]+\/(.*)'
    name_obfuscator:
      enabled: true
      patterns:
        - '/AncillaryApplication/<recLoc>/<lastName>'
        - 'AncillaryApplication/<recLoc,\w{3}>/<lastName,\w+>'
        - '/Ancillary\w+/<recLoc,\w{3}>/<lastName>'
        - '(?<obfuscatedVin>[A-Za-z\d]{11}\d{6})'
```

### Renaming options

By default, the built-in `TransactionNamer` instrumentation is registered with the extension.  This  instrumentation can be used to alter the way in which the transaction name is set for a Servlet request.  The TransactionNamer provides 3 mechanisms for altering the transaction name:

1. [`append_parameters`](#append-parameters) - Renaming based on HTTP parameters/cookies/headers
1. [`name_grouper`](#name-grouper) - Grouping transaction names
1. [`name_obfuscator`](#name-obfuscator) - Obfuscating transaction names
1. [Custom instrumentation](#custom-instrumentation)

### Append parameters

Use `append_parameters` to rename Transactions using HTTP parameters, cookies & headers. You can append any HTTP request parameter, cookie or header to the transaction name.

```yaml
    append_parameters:
      enabled: true
      parameters:
        - name: [parameter_name]
          type: [cookie|header|parameter]
        - name: [parameter_name]
          type: [cookie|header|parameter]
```
* Valid values for `transaction_parameter_type` are `cookie`, `header` and `parameter`.
* You can append as many parameters as you want. Each one gets its own list member (signified by a `-`), name and type.
* The parameters will be appended in the order in which they are listed.

### Name grouper

Use `name_grouper` to group your transactions into names from URL segments. Using regular expression patterns, choose which URLs to analyise and the segments by which you want to group transations.

```yaml
    name_grouper:
      enabled: true
      patterns:
        - 'pattern 1'
        - 'pattern 2'
        - 'pattern 3'
```

* The patterns are regex patterns that are matched against the URI.
* Each pattern must be on it's own line, surrounded by single-quotes.
* Use normal Java regular expressions as the pattern.
  * Great tutorial/reference for regex: http://www.regular-expressions.info/
  * Regex building tool: http://www.regexr.com/
* For any segment you wish to preserve, use a regex grouping `(like this)`.
* For any segment you wish to group by, do NOT put it in a regex grouping. It will simply not appear in the resultant transaction name.
* Each URI will be successfully matched only once - subsequent patterns that would match that URI will not be tested.

#### Example name_grouper patterns

_Pattern 1: Group WebSphere Portal transactions without Stateful URL string_

* Pattern: `(\/wps\/myportal\/[^!]*)!ut.*`
* Matches the following URLs:
  * `/wps/myportal/Search/Search%20Center/!ut/p/a1/04_Sj9CPykssy0xPLMnMz0vMAfGjzOKd3R0`
  * `/wps/myportal/tagging/!ut/p/a1/04_Sj9CPy328dh23ch249fho2ij1jKJ8x9T`
* Groups these URLs as:
  * `/wps/myportal/Search/Search%20Center/`
  * `/wps/myportal/tagging`
* Does NOT match the following URLs:
  * `/wps/portal/Search/Search%20Center/!ut/p/a1/04_Sj9CPykssy0xPLMnMz0vMAfGjzOKd3R0`
  * `/wps/myportal/tagging/some/other/stuff`

_Pattern 2: Strip extensions from URI (.jsp, .html, etc.)_

* Pattern: `(\/jpetstore_web\/[^.]*)\..*`
* Matches the following URLs:
  * `/jpetstore_web/catalog/Item.jsp`
  * `/jpetstore_web/help.html`
* Groups these URLs as:
* `/jpetstore_web/catalog/Item`
* `/jpetstore_web/help`
* Does NOT match the following URLs:
  * `/jpetstore_web/catalog/Checkout`
  * `/jpetstore_notweb/catalog/Item.jsp`

_Pattern 3: Combine transactions from different subdirectories (i.e. per-account settings)_

* Pattern: `(\/jpetstore_web\/accounts\/)[^\/]+\/(.*)`
* Matches the following URLs:
  * `/jpetstore_web/accounts/account1/editAccount`
  * `/jpetstore_web/accounts/another_account/doEdit`
* Groups these URLs as:
  * `/jpetstore_web/accounts/editAccount`
  * `/jpetstore_web/accounts/doEdit`
* Does NOT match the following URLs:
  * `/jpetstore_web/accounts/noaccount`
  * `/jpetstore_notweb/accounts/account1/editAccount`

### Name obfuscator

Use `name_obfuscator` to obfuscate URL segments in Transaction Names. Using regular expression patterns, choose which URLs to analyise and which segments will be masked AND grouped together.

```yaml
    name_obfuscator:
      enabled: true
      patterns:
        - 'pattern 1'
        - 'pattern 2'
        - 'pattern 3'
```

* For any segment you wish to obfuscate, use `<replacement_name>`, in which `replacement_name` is the name you want to group that segment as, for example `<lastName>`.
* For any segment you wish to obfuscate AND it requires a regex statement to collect, use the following notation:
`<replacement_name,regex>`.
* Each pattern must be on it's own line, surrounded by single-quotes.
* You can use normal Java regular expressions anywhere in the pattern, even outside of obfuscated fields.
  * Great tutorial/reference for regex: http://www.regular-expressions.info/
  * Regex building tool: http://www.regexr.com/

#### Example name_obfuscator patterns

_Pattern 1: Basic pattern match using <replacement_name> notation in URL pattern_

* Pattern: `'/AncillaryApplication/<recLoc>/<lastName>'`
* Matches the following URLs:
  * `/AncillaryApplication/92Jets/Selanne`
  * `/AncillaryApplication/Helsinki/Kurri17`
* Groups these URLs as:
  `/AncillaryApplication/<recLoc>/<lastName>`
* Does NOT match the following URLs:
  * `/AncillaryApplication/92Jets/Selanne/Teemu`
  * `/NotAncillaryApplication/Turku/Koivu`

_Pattern 2: Using <replacement_name,regex> notation in URL pattern_

* Pattern: `'AncillaryApplication/<recLoc,\w{3}>/<lastName,\w+>'`
* Matches the following URLs:
  * `/AncillaryApplication/HEL/Kapanen`
  * `/AncillaryApplication/KUO/Timonen`
* Groups these URLs as:
  `/AncillaryApplication/<recLoc>/<lastName>`
* Does NOT match the following URLs:
  * `/AncillaryApplication/Helsinki/Tikkanen`
  * `/AncillaryApplication/OUL/Pitkanen25`

_Pattern 3: Using regex elsewhere in URL pattern_

* Pattern: `'/Ancillary\w+/<recLoc,\w{3}>/<lastName>'`
* Matches the following URLs:
  * `/AncillaryApplication/TMP/Numminen` grouped as `/AncillaryApplication/<recLoc>/<lastName>`
  * `/AncillaryApp/TKU/Salo` grouped as `/AncillaryApp/<recLoc>/<lastName>`

_Pattern 4: Replace regex pattern everywhere in URL path_

* Pattern: `'(?<obfuscatedVin>[A-Za-z\d]{11}\d{6})'`
* Matches the following URLs:
  * `/VehicleApplication/AB1CDE2EFGH567890` grouped as `/VehicleApplication/<obfuscatedVin>`
  * `/VehicleApplication/AB1CDE2EFGH567890/AnotherSegment/IJ1KLM2NOPQ567890/TheEnd` grouped as `/VehicleApplication/<obfuscatedVin>/AnotherSegment/<obfuscatedVin>/TheEnd`

### Custom instrumentation

Additional custom instrumentations can be created as follows.

1. Create a class that implements the `ServletInstrumentation` interface.  The
Javadoc for this interface is included with the distribution package.
    1. Implement the `init(Config config)` method.  This method is called only once
    throughout the lifetime of the parent class loader.  Use this time to initialize
    any private variables based on the Agent configuration in `config`.
    1. Implement the `instrumentRequest(request, response, agent, config)` method.
    This method is called once for every servlet request.  Logic contained in this
    method should consume as few compute resources as possible since it is called
    frequently.
1. Ensure that the compiled class file for you class is present on the
application server `CLASSPATH`.  Mechanisms for this vary and are outside the
scope of this documentation.
1. Register the custom instrumentation by updating the New Relic Java agent
configuration (newrelic.yml) as follows.
    1. Locate the `custom.httpservlet_transaction_namer.instrumentations` property
    in the YML.
    1. Add a new line with proper indentation that contains the ` - ` prefix and
    the fully-qualified class name of the custom class.  E.g.
        ```yaml
        httpservlet_transaction_namer:
          instrumentations:
           - com.newrelic.fit.javax.servlet.http.TransactionNamer
           - path.to.my.package.CustomInstrumentation
        ```
    1. Add any other configuration necessary for the custom instrumentation within
    the `httpservlet_transaction_namer` container in the YAML.  E.g.
        ```yaml
        httpservlet_transaction_namer:
        ...
          my_custom_stuff:
            foo: bar
            list:
             - 1
             - 2
        ```
    The custom instrumentation can access this data through the `config` parameter
    in the `init(config)` method.
1. Restart your JVM and your extension should be available.

## Building

This project uses the Gradle build technology for building the distributable
assets.  The Gradle `distribution` plugin is used to actually build the
distribution file that can be provided to customers.

To build the distribution, perform the following steps.

1. Clone this repository

    ```sh
    git clone https://source.datanerd.us/sdewitt/httpservlet-transaction-namer
    ```

1. Build the distribution

    ```sh
    cd httpservlet-transaction-namer
    ./gradlew clean build buildPdf distZip
    ```

This will produce the following asset in the build directory:

```
./build/distributions/httpservlet-transaction-namer-VERSION.zip
```

The Zip contents will be as follows

| Asset | Description |
| --- | --- |
| httpservlet-transaction-namer-2.1.jar | The extension JAR file |
| README.pdf | The PDF instructions for usage |
| javadoc/* | The public API documentation |

## Testing

There is a `test` gradle target defined for this project that runs JUnit tests.

## Support

New Relic has open-sourced this project. This project is provided AS-IS WITHOUT WARRANTY OR DEDICATED SUPPORT. Issues and contributions should be reported to the project here on GitHub. We encourage you to bring your experiences and questions to the [Explorers Hub](https://discuss.newrelic.com) where our community members collaborate on solutions and new ideas.

## Contributing

We encourage your contributions to improve HTTPServlet Transaction Namer! Keep in mind when you submit your pull request, you'll need to sign the CLA via the click-through using CLA-Assistant. You only have to sign the CLA one time per project.
If you have any questions, or to execute our corporate CLA, required if your contribution is on behalf of a company,  please drop us an email at opensource@newrelic.com.

**A note about vulnerabilities**

As noted in our [security policy](../../security/policy), New Relic is committed to the privacy and security of our customers and their data. We believe that providing coordinated disclosure by security researchers and engaging with the security community are important means to achieve our security goals.

If you believe you have found a security vulnerability in this project or any of New Relic's products or websites, we welcome and greatly appreciate you reporting it to New Relic through [HackerOne](https://hackerone.com/newrelic).

## License

HTTPServlet Transaction Namer is licensed under the [Apache 2.0](http://apache.org/licenses/LICENSE-2.0.txt) License.
