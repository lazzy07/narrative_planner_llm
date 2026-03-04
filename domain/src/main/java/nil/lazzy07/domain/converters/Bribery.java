/*
* File name: Bribery.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-25 04:02:48
// Date modified: 2026-03-04 02:11:33
* ------
*/

package nil.lazzy07.domain.converters;

import java.util.ArrayList;

import edu.uky.cs.nil.sabre.Fluent;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import edu.uky.cs.nil.sabre.logic.Expression;
import edu.uky.cs.nil.sabre.logic.Parameter;
import edu.uky.cs.nil.sabre.logic.True;

public class Bribery extends DomainConverter {
  public Bribery(Expression initial, int goal) {
    super(initial, goal);

    agents.put("Hero", "The hero");
    agents.put("Villain", "The villain");
    agents.put("President", "The President");
    places.put("Bank", "The bank");

    this.domainDescription = """
        All events take place in one location, a bank. There is one item in the game: money. There are three characters in this story. The president of the United States is a character. The hero is a character. The villain is a character. The villain wants to control the president. There are five kinds of actions characters can take in the story. Characters can steal an item which is at their location. If one character has the money, they can bribe a second character by giving money to the second character to gain control over them. One character can threaten a second character, causing the second character to be afraid of the first character. One character can coerce a second character into wanting a third character to have an item if the second character fears the first character. One character can give an item to another character.
        """;
  }

  @Override
  public String fluent(Fluent fluent, Expression value) {
    String str = believes(fluent, value);
    ArrayList<String> args = new ArrayList<>();
    for (Parameter arg : fluent.signature.arguments)
      args.add(arg.toString());
    String arg0 = args.get(0);
    switch (fluent.signature.name) {
      case "at":
        str += value + " has " + arg0;
        break;
      case "fears":
        if (value.equals(True.TRUE))
          str += arg0 + " fears " + args.get(1);
        else
          str += arg0 + " does not fear " + args.get(1);
        break;
      case "controls":
        if (value.equals(True.TRUE))
          str += arg0 + " controls " + args.get(1);
        else
          str += arg0 + " does not control " + args.get(1);
        break;
      case "intends":
        str += arg0 + " wants " + value + " to have " + args.get(1);
        break;
      default:
        str += fluent + " = " + value;
    }
    return clean(str) + ". ";
  }

  @Override
  public String action(CompiledAction action) {
    String name = action.signature.name;
    ArrayList<String> args = new ArrayList<String>();
    for (Parameter arg : action.signature.arguments)
      args.add(arg.toString());
    String arg0 = args.get(0);
    String str = "";
    switch (name) {
      case "steal":
        str += arg0 + " steals " + args.get(1) + " from " + args.get(2);
        break;
      case "bribe":
        str += arg0 + " bribes " + args.get(1) + " with " + args.get(2);
        break;
      case "threaten":
        str += arg0 + " threatens " + args.get(1);
        break;
      case "coerce":
        str += arg0 + " coerces " + args.get(1) + " into giving up " + args.get(2);
        break;
      case "give":
        str += arg0 + " gives " + args.get(2) + " to " + args.get(1);
        break;
      default:
        throw new RuntimeException(NO_ACTION + action);
    }
    return clean(str) + ".";
  }

  @Override
  public String characterGoals() {
    return "The villain wants to control the President. ";
  }

  @Override
  public String actionTypes() {
    return "Characters can steal, bribe, threaten, coerce, and give. ";
  }

  @Override
  public String goal() {
    return "The story must end with "
        + "the villain controlling the President. ";
  }

  public String authorGoal() {
    return "that ends with the villain controlling the president.";
  }
}
