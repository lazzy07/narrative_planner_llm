/*
* File name: DomainConverterFactory.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-01-25 22:43:08
// Date modified: 2026-01-25 22:48:49
* ------
*/

package nil.lazzy07.domain;

import edu.uky.cs.nil.sabre.logic.Expression;
import nil.lazzy07.domain.converters.DeerHunter;
import nil.lazzy07.domain.converters.DomainConverter;

public class DomainConverterFactory {


  public static DomainConverter GetDomainConverter(String domain, Expression initial, int goal) {
    switch(domain){
      case "deerhunter":
        return new DeerHunter(initial, goal);
      default:
        return null;
    }
  }
}
