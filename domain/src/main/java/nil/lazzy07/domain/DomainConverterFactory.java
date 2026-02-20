/*
* File name: DomainConverterFactory.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-01-25 22:43:08
// Date modified: 2026-02-17 23:56:02
* ------
*/

package nil.lazzy07.domain;

import edu.uky.cs.nil.sabre.logic.Expression;
import nil.lazzy07.domain.converters.DeerHunter;
import nil.lazzy07.domain.converters.DomainConverter;
import nil.lazzy07.domain.converters.SecretAgent;

public class DomainConverterFactory {

  public static DomainConverter GetDomainConverter(String domain, Expression initial, int goal) {
    switch (domain) {
      case "deerhunter":
        return new DeerHunter(initial, goal);
      case "secretagent":
        return new SecretAgent(initial, goal);
      default:
        return null;
    }
  }
}
