/*
* File name: Basketball.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-04 02:26:44
// Date modified: 2026-03-04 02:30:36
* ------
*/

package nil.lazzy07.domain.converters;

import java.util.ArrayList;

import edu.uky.cs.nil.sabre.Fluent;
import edu.uky.cs.nil.sabre.logic.Expression;
import edu.uky.cs.nil.sabre.logic.Parameter;
import edu.uky.cs.nil.sabre.logic.True;
import edu.uky.cs.nil.sabre.Number;
import edu.uky.cs.nil.sabre.comp.CompiledAction;

public class Basketball extends DomainConverter {
  public Basketball(Expression initial, int goal) {
    super(initial, goal);
    agents.put("Alice", "Alice");
    agents.put("Bob", "Bob");
    agents.put("Charlie", "Charlie");
    agents.put("Sherlock", "The detective");
    places.put("HomeB", "Bob's house");
    places.put("BasketballCourt", "The basketball court");
    places.put("Downtown", "downtown");
    others.put("Basketball", "The basketball");
    others.put("Bat", "The bat");
    others.put("Theft", "theft");
    others.put("Murder", "murder");

    this.domainDescription = """
        This domain describes a crime drama where characters can commit theft and murder while investigators search for clues and arrest criminals. Characters get angry when their items are stolen but can let off steam by playing basketball. This story have three civilians, one detective, three locations, and two items.  Charlie starts angry and wants Alice to be dead. The author gains 1 point of utility if fewer than two citizens are angry and 1 point if someone has been arrested. The story must end with a character either being arrested or feeling better. Alice wants to be happy. Bob wants everyone to be happy. Charlie wants Alice to be dead. The detective wants to search for clues and arrest citizens for crimes. Tell me a story using only these locations, items, characters, and actions. Do not invent new locations, items, characters, or actions. The characters should try to achieve their goals. In this story, there are 4 characters, Alice, Bob, Charlie and Sherlock. There are 3 places, HomeB (Bob's house), Basketball court and Downtown. Basketball and a bat are also available as items.
        """;
  }

  public String authorGoal() {
    return "";
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
        str += standardLocation(arg0, value.toString(), str);
        break;
      case "has":
        str += value + " has " + arg0;
        break;
      case "alive":
        if (value.equals(True.TRUE))
          str += arg0 + " is alive";
        else
          str += arg0 + " is not alive";
        break;
      case "underArrest":
        if (value.equals(Number.get(1)))
          str += arg0 + " is under arrest";
        else
          str += arg0 + " is not under arrest";
        break;
      case "angry":
        if (value.equals(Number.get(1)))
          str += arg0 + " is angry";
        else
          str += arg0 + " is not angry";
        break;
      case "searched":
        if (value.equals(Number.get(1)))
          str += arg0 + " has been searched";
        else
          str += arg0 + " has not been searched";
        break;
      case "clue":
        if (value.equals(True.TRUE))
          str += args.get(1) + " is a clue that a " + arg0 + " occurred at " + args.get(2);
        else
          str += args.get(1) + " is not a clue that a " + arg0 + " occurred at " + args.get(2);
        break;
      default:
        str += fluent + " = " + value;
    }
    return clean(str).replaceAll("at downtown", "downtown") + ". ";
  }

  @Override
  // Add a case for every action
  public String action(CompiledAction action) {
    String name = action.signature.name;
    ArrayList<String> args = new ArrayList<String>();
    for (Parameter arg : action.signature.arguments)
      args.add(arg.toString());
    String arg0 = args.get(0);
    String str = "";
    switch (name) {
      case "travel":
        str += arg0 + " travels from " + args.get(1) + " to " + args.get(2);
        break;
      case "arrest":
        str += arg0 + " arrests " + args.get(1) + " for " + args.get(3);
        break;
      case "steal":
        str += arg0 + " steals " + args.get(3) + " from " + args.get(1);
        break;
      case "play_basketball":
        str += arg0 + " and " + args.get(1) + " play basketball and feel better";
        break;
      case "kill":
        str += arg0 + " kills " + args.get(1) + " with " + args.get(3);
        break;
      case "find_clues":
        str += arg0 + " searches " + args.get(3) + " for clues";
        break;
      case "share_clues":
        str += arg0 + " shares clues with " + args.get(1);
        break;
      case "suspect_of_crime":
        str += arg0 + " suspects " + args.get(1) + " of " + args.get(2);
        break;
      default:
        return "";
    }
    return clean(str) + ".";
  }

  @Override
  public String characterGoals() {
    return "Alice wants to be happy. "
        + "Bob wants everyone to be happy. "
        + "Charlie wants Alice to be dead. "
        + "The detective wants to search for clues and arrest citizens for crimes. ";
  }

  @Override
  public String actionTypes() {
    return "Characters can travel, steal, play basketball, and kill. "
        + "Police characters can arrest, find clues, share clues, and suspect citizens of crimes. ";
  }

  @Override
  public String goal() {
    String text = "The story must end with ";
    switch (goal) {
      case 2:
        return text + "a character being under arrest and a character feeling better. ";
      case 1:
        return text + "a character either being arrested or feeling better. ";
      default:
        return "";
    }
  }
}
