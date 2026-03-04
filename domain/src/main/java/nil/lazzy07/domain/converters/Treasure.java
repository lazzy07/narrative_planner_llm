/*
* File name: Treasure.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-04 02:51:01
// Date modified: 2026-03-04 02:51:31
* ------
*/

package nil.lazzy07.domain.converters;

import java.util.ArrayList;

import edu.uky.cs.nil.sabre.Fluent;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import edu.uky.cs.nil.sabre.logic.Expression;
import edu.uky.cs.nil.sabre.logic.Parameter;

public class Treasure extends DomainConverter {

  public Treasure(Expression initial, int goal) {
    super(initial, goal);

    agents.put("Hawkins", "Hawkins");
    agents.put("Silver", "Silver");

    places.put("Port", "Port Royal");
    places.put("Island", "The island");

    others.put("Treasure", "The treasure");
    others.put("Buried", "buried");

    this.domainDescription = """
        There are two locations in this story: the port and the island.
        There is one item in this story: some treasure.
        There are two characters in this story.
        Hawkins is a boy who wants the treasure.
        Silver is a pirate who wants the treasure.

        There are four kinds of actions characters can take in the story.
        If the treasure is buried on the island, Hawkins can spread a rumor that will make Silver believe the treasure is buried on the island.
        Hawkins and Silver can work together to sail a ship from the port to the island.
        If the treasure is buried on the island, Hawkins can dig up the treasure.
        A character can take the treasure once it has been dug up.

        Tell me a story using only these locations, item, characters, and actions.
        Do not invent new locations, items, characters, or actions.
        The characters should try to achieve their goals.
        """;
  }

  @Override
  public String authorGoal() {
    return "that ends with Hawkins having the treasure.";
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
        // TreasureText overloads "at" for location/possession/buried/unknown.
        if (places.containsKey(value.toString()))
          str += arg0 + " is at " + value;
        else if (agents.containsKey(value.toString()))
          str += value + " has " + arg0;
        else if (value.toString().equals("?"))
          str += "where " + arg0 + " is";
        else
          str += arg0 + " is " + value;
        break;

      default:
        str += fluent + " = " + value;
    }

    return clean(str) + ". ";
  }

  @Override
  public String action(CompiledAction action) {
    String name = action.signature.name;

    ArrayList<String> args = new ArrayList<>();
    for (Parameter arg : action.signature.arguments)
      args.add(arg.toString());

    String str = "";

    switch (name) {
      case "rumor":
        str += "Hawkins spreads a rumor that Treasure is buried on Island";
        break;

      case "sail":
        str += "Hawkins and Silver sail from Port to Island";
        break;

      case "dig":
        str += "Hawkins digs up Treasure";
        break;

      case "take":
        str += args.get(0) + " takes Treasure";
        break;

      default:
        return "";
    }

    return clean(str) + ".";
  }

  @Override
  public String characterGoals() {
    return "Jim Hawkins and Long John Silver each want to have the treasure for themselves. ";
  }

  @Override
  public String actionTypes() {
    return "Characters can spread a rumor, sail, dig, and take. ";
  }

  @Override
  public String goal() {
    String text = "The story must end with ";
    switch (goal) {
      case 1:
        return text + "Jim Hawkins having the treasure. ";
      default:
        return "";
    }
  }
}
