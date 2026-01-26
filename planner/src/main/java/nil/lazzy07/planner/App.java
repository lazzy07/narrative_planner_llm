package nil.lazzy07.planner;

import nil.lazzy07.domain.DomainConverter;
import nil.lazzy07.llm.LLMApi;

/**
 * Hello world!
 */
public class App {
  public static void main(String[] args) {
    System.out.println("Hello World!");

    DomainConverter dc = new DomainConverter();
    dc.printHello();

    LLMApi api = new LLMApi();
    api.printHello();
  }
}
