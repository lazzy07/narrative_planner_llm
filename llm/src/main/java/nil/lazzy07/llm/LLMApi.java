/*
* File name: LLMApi.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-01-25 22:44:36
// Date modified: 2026-01-25 22:50:48
* ------
*/

package nil.lazzy07.llm;

import nil.lazzy07.domain.DomainConverter;

public class LLMApi {
  public void printHello() {
    DomainConverter dc = new DomainConverter();
    dc.printHello();

    System.out.println("Hello from LLMApi");
  }
}
