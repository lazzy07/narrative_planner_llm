package nil.lazzy07.planner;

import nil.lazzy07.planner.cli.ArgumentHandler;
import nil.lazzy07.planner.cli.ParsedCLIArguments;

/**
 * Hello world!
 */
public class App {
  public static void main(String[] args) {
    ParsedCLIArguments parsedCLIArguments = ArgumentHandler.parseCLIArguments(args);
  }
}
