/*
* File name: ArgumentHandler.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-01-26 22:56:06
// Date modified: 2026-01-27 01:51:38
* ------
*/

package nil.lazzy07.planner.cli;

import org.apache.commons.cli.*;
import org.apache.commons.cli.HelpFormatter;

import nil.lazzy07.planner.logging.LoggerConfigs;

public class ArgumentHandler {
  public static Options buildOptions() {
    Options options = new Options();

    Option configFilePath = new Option("c", "config", true, "Configurations file path");
    configFilePath.setRequired(true);
    configFilePath.setArgName("CONFIG");
    options.addOption(configFilePath);

    Option logLevel = new Option(null, "log-level", true,
        "Logging level (ERROR, WARN, INFO, DEBUG, TRACE)");
    logLevel.setArgName("LEVEL");
    options.addOption(logLevel);

    Option help = new Option("h", "help", false, "Print this help message");
    options.addOption(help);

    return options;
  }

  public static ParsedCLIArguments parseCLIArguments(String[] args) {
    CommandLineParser parser = new DefaultParser();
    Options options = buildOptions();

    try {
      CommandLine cmd = parser.parse(options, args);

      if (cmd.hasOption("help")) {
        printHelp(options);

        return null;
      }

      if (cmd.hasOption("log-level")) {
        LoggerConfigs.configureLoggingLevel(cmd.getOptionValue("log-level").toUpperCase());
      }

      String config = cmd.getOptionValue("config");
      ParsedCLIArguments parsedCLIArguments = new ParsedCLIArguments();

      parsedCLIArguments.setConfigFilePath(config);

      return parsedCLIArguments;

    } catch (ParseException e) {
      printHelp(options);
      System.out.println("Error: Parsing error");

      return null;
    }
  }

  public static void printHelp(Options options) {
    @SuppressWarnings("deprecation")
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("planner", "Narrative Planner using LLM", options, "\nExample: ./run-planner.sh -c config.json",
        true);
  }
}
