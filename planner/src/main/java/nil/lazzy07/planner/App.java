package nil.lazzy07.planner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nil.lazzy07.planner.cli.ArgumentHandler;
import nil.lazzy07.planner.cli.ParsedCLIArguments;

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

  }
}
