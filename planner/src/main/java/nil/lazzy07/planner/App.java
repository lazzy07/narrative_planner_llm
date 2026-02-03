package nil.lazzy07.planner;

import nil.lazzy07.planner.core.PlannerCore;
import nil.lazzy07.planner.search.util.SearchNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nil.lazzy07.planner.cli.ArgumentHandler;
import nil.lazzy07.planner.cli.ParsedCLIArguments;
import nil.lazzy07.planner.config.ConfigFile;
import nil.lazzy07.planner.config.ConfigFileParser;

/**
 * Hello world!
 */
public class App {

  private static final Logger log = LoggerFactory.getLogger(App.class);

  public static void main(String[] args) {

    ParsedCLIArguments parsedCLIArguments = ArgumentHandler.parseCLIArguments(args);

    if (parsedCLIArguments == null)
      return;

    log.info("Parsing CLI Arguments completed");

    ConfigFileParser.Init(parsedCLIArguments.getConfigFilePath());
    ConfigFile configurations = ConfigFileParser.GetConfigFile();

    if (configurations == null) {
      log.error("Configuration file reading error, application is quitting");
      return;
    }

    log.info("Current planner configurations: {}", configurations);

    PlannerCore plannerCore = new PlannerCore(configurations);
    SearchNode.SetProgressionTreeMap(plannerCore.getProgressionTreeMap());

    log.info("Planner setup is complete");


  }
}
