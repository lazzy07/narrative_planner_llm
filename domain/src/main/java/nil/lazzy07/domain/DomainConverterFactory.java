/*
* File name: DomainConverterFactory.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-01-25 22:43:08
// Date modified: 2026-03-04 03:39:45
* ------
*/
package nil.lazzy07.domain;

import edu.uky.cs.nil.sabre.logic.Clause;
import edu.uky.cs.nil.sabre.logic.Effect;
import edu.uky.cs.nil.sabre.logic.Expression;
import nil.lazzy07.domain.converters.DomainConverter;
import nil.lazzy07.domain.converters.*;

public class DomainConverterFactory {

  // Your existing signature (keep it if other code uses it)
  public static DomainConverter GetDomainConverter(String domain, Expression initial, int goal) {
    if (domain == null || domain.isBlank())
      throw new IllegalArgumentException("Domain name cannot be null/empty.");

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
      case "gramma":
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

  // ✅ Overload matching your PlannerCore call site
  public static DomainConverter GetDomainConverter(
      String domain,
      Clause<Effect> initial,
      int goal) {

    // Clause is an Expression in Sabre's type hierarchy (typically),
    // but this cast keeps the method signature exact as requested.
    // If your Sabre version has Clause not extending Expression, see note below.
    return GetDomainConverter(domain, (Expression) initial, goal);
  }

  private static String normalize(String domain) {
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
    if (domain.startsWith("aladdin"))
      return "aladdin";
    return domain;
  }
}
