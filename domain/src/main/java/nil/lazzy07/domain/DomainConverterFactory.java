/*
* File name: DomainConverterFactory.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-01-25 22:43:08
// Date modified: 2026-03-04 03:28:07
* ------
*/

package nil.lazzy07.domain;

import edu.uky.cs.nil.sabre.logic.Expression;
import nil.lazzy07.domain.converters.DeerHunter;
import nil.lazzy07.domain.converters.DomainConverter;
import nil.lazzy07.domain.converters.Fantasy;
import nil.lazzy07.domain.converters.Gramma;
import nil.lazzy07.domain.converters.Hospital;
import nil.lazzy07.domain.converters.Jailbreak;
import nil.lazzy07.domain.converters.Raiders;
import nil.lazzy07.domain.converters.SecretAgent;
import nil.lazzy07.domain.converters.Space;
import nil.lazzy07.domain.converters.Treasure;
import nil.lazzy07.domain.converters.Western;
import nil.lazzy07.domain.converters.Aladdin;
import nil.lazzy07.domain.converters.Basketball;
import nil.lazzy07.domain.converters.Bribery;
import nil.lazzy07.domain.converters.Lovers;

public class DomainConverterFactory {

  public static DomainConverter getDomainConverter(String domain, Expression initial, int goal) {

    if (domain == null || domain.isBlank()) {
      throw new IllegalArgumentException("Domain name cannot be null or empty.");
    }

    // Normalize versioned domain names (e.g., deerhunter_any → deerhunter)
    String base = normalize(domain.toLowerCase());

    switch (base) {

      case "deerhunter":
        return new DeerHunter(initial, goal);

      case "secretagent":
        return new SecretAgent(initial, goal);

      case "bribery":
        return new Bribery(initial, goal);

      case "aladdin":
        return new Aladdin(initial, goal);

      case "hospital":
        return new Hospital(initial, goal);

      case "basketball":
        return new Basketball(initial, goal);

      case "western":
        return new Western(initial, goal);

      case "fantasy":
        return new Fantasy(initial, goal);

      case "space":
        return new Space(initial, goal);

      case "raiders":
        return new Raiders(initial, goal);

      case "treasure":
        return new Treasure(initial, goal);

      case "gramma": // Save Gramma
      case "savegramma":
        return new Gramma(initial, goal);

      case "jailbreak":
        return new Jailbreak(initial, goal);

      case "lovers":
        return new Lovers(initial, goal);

      default:
        throw new IllegalArgumentException("Unknown domain: " + domain);
    }
  }

  /**
   * Maps version names to base domain names.
   */
  private static String normalize(String domain) {

    // Remove common version suffixes
    if (domain.startsWith("deerhunter"))
      return "deerhunter";
    if (domain.startsWith("fantasy"))
      return "fantasy";
    if (domain.startsWith("space"))
      return "space";
    if (domain.startsWith("hospital"))
      return "hospital";
    if (domain.startsWith("basketball"))
      return "basketball";
    if (domain.startsWith("gramma"))
      return "gramma";
    if (domain.startsWith("jailbreak"))
      return "jailbreak";

    return domain;
  }
}
