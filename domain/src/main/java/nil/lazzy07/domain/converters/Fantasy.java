/*
* File name: Fantasy.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-04 02:43:53
// Date modified: 2026-03-04 02:43:59
* ------
*/

package nil.lazzy07.domain.converters;

import java.util.ArrayList;

import edu.uky.cs.nil.sabre.Fluent;
import edu.uky.cs.nil.sabre.Number;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import edu.uky.cs.nil.sabre.logic.Expression;
import edu.uky.cs.nil.sabre.logic.Parameter;
import edu.uky.cs.nil.sabre.logic.True;

public class Fantasy extends DomainConverter {

  public Fantasy(Expression initial, int goal) {
    super(initial, goal);

    // agents
    agents.put("Talia", "Talia");
    agents.put("Rory", "Rory");
    agents.put("Vince", "Vince");
    agents.put("Gargax", "Gargax");

    // places
    places.put("Village", "The village");
    places.put("Cave", "The cave");

    // items / other symbols
    others.put("Money", "the money");
    others.put("Treasure", "the treasure");

    // Domain description (based on fantasy_description.txt + the same “don’t
    // invent” constraints you used)
    this.domainDescription = """
        This domain describes a fantasy story with two locations (Village and Cave), two items (Money and Treasure), and four characters (Talia, Rory, Vince, and Gargax).
        Talia, Rory, and Vince are humans. Gargax is a dragon.
        Talia wants to be happy and wealthy. Rory wants to be happy, wealthy, and not hungry. Vince wants to be happy and not hungry (and does not care if he is rich). Gargax wants to be wealthy and not hungry (and does not care about being happy).
        Characters can propose marriage, accept proposals (which makes both characters happy), and marry if a proposal was made and accepted. Characters can travel, pick up items, take items from dead bodies, become hungry, and Gargax can eat a human at the same location.
        The story must end with the specified goal condition for Talia (marriage and/or having Money/Treasure depending on the goal setting).
        Tell me a story using only these locations, items, characters, and actions. Do not invent new locations, items, characters, or actions. The characters should try to achieve their goals.
        """;
  }

  @Override
  public String authorGoal() {
    // From FantasyText.authorGoal()
    return "that ends with Talia becoming happy or Talia becoming wealthy or both.";
  }

  @Override
  public String fluent(Fluent fluent, Expression value) {
    String str = believes(fluent, value);
    ArrayList<String> args = new ArrayList<>();

    for (Parameter arg : fluent.signature.arguments) {
      args.add(arg.toString());
    }

    String arg0 = args.get(0);

    switch (fluent.signature.name) {
      case "alive":
        if (value.equals(True.TRUE))
          str += arg0 + " is alive";
        else
          str += arg0 + " is not alive";
        break;

      case "loves":
        if (value.equals(True.TRUE))
          str += arg0 + " loves " + args.get(1);
        else
          str += arg0 + " does not love " + args.get(1);
        break;

      case "relationship": {
        // FantasyText compares against "proposed"/"accepted"/else married. We'll do
        // string-based checks safely.
        String v = value.toString();
        if (v.equals("proposed"))
          str += arg0 + " has proposed to " + args.get(1);
        else if (v.equals("accepted"))
          str += arg0 + " and " + args.get(1) + " are engaged";
        else
          str += arg0 + " and " + args.get(1) + " are married";
        break;
      }

      case "happiness":
        str += arg0 + "'s happiness is " + value;
        break;

      case "wealth":
        str += arg0 + "'s wealth is " + value;
        break;

      case "hunger":
        str += arg0 + "'s hunger is " + value;
        break;

      case "at":
        str += standardLocation(arg0, value.toString(), str);
        break;

      case "has": {
        // In FantasyText, "has" prints: "<char> has <count> <item>s"
        // Signature is typically has(character, itemType) = number/count.
        String itemType = (args.size() > 1) ? args.get(1) : "item";
        String v = value.toString();

        // Optional singular/plural nicety if count is 1.
        boolean isOne = value.equals(Number.get(1)) || "1".equals(v);

        str += arg0 + " has " + value + " " + itemType + (isOne ? "" : "s");
        break;
      }

      default:
        str += fluent + " = " + value;
        break;
    }

    return clean(str) + ". ";
  }

  @Override
  public String action(CompiledAction action) {
    String name = action.signature.name;

    ArrayList<String> args = new ArrayList<>();
    for (Parameter arg : action.signature.arguments) {
      args.add(arg.toString());
    }

    String arg0 = args.size() > 0 ? args.get(0) : "";
    String str = "";

    switch (name) {
      case "propose":
        str += arg0 + " proposes to " + args.get(1);
        break;

      case "accept":
        str += arg0 + " accepts " + args.get(1) + "'s proposal";
        break;

      case "marry":
        str += arg0 + " and " + args.get(1) + " get married";
        break;

      case "travel":
        str += arg0 + " travels from " + args.get(1) + " to " + args.get(2);
        break;

      case "pickup":
        str += arg0 + " picks up " + args.get(1);
        break;

      case "take":
        str += arg0 + " takes " + args.get(1) + " from " + args.get(2);
        break;

      case "get_hungry":
        str += arg0 + " gets hungry";
        break;

      case "eat":
        str += arg0 + " eats " + args.get(1);
        break;

      default:
        return "";
    }

    return clean(str) + ".";
  }

  @Override
  public String characterGoals() {
    // From FantasyText.characterGoals()
    return "Talia wants to be happy and wealthy. "
        + "Rory wants to be happy. "
        + "Vince wants to be happy and wealthy. "
        + "Gargax wants to be wealthy and does not want to be hungry. ";
  }

  @Override
  public String actionTypes() {
    // From FantasyText.actionTypes()
    return "Characters can propose, accept, marry, travel, pick up, take, get hungry, and eat. ";
  }

  @Override
  public String goal() {
    // From FantasyText.goal()
    String text = "The story must end with ";
    switch (goal) {
      case 3:
        return text + "Talia being married and having both the treasure and the money. ";
      case 2:
        return text + "Talia either having both the treasure and the money, or being married and having one of these. ";
      case 1:
        return text + "Talia either being married or having the treasure or the money. ";
      default:
        return "";
    }
  }
}
