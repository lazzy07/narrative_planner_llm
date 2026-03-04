/*
* File name: Raiders.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-04 02:48:08
// Date modified: 2026-03-04 02:48:51
* ------
*/

package nil.lazzy07.domain.converters;

import java.util.ArrayList;

import edu.uky.cs.nil.sabre.Fluent;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import edu.uky.cs.nil.sabre.logic.Expression;
import edu.uky.cs.nil.sabre.logic.Parameter;
import edu.uky.cs.nil.sabre.logic.True;
import edu.uky.cs.nil.sabre.logic.Unknown;

public class Raiders extends DomainConverter {

  public Raiders(Expression initial, int goal) {
    super(initial, goal);

    agents.put("Jones", "Indiana Jones");
    agents.put("USArmy", "The U.S. Army");
    agents.put("Nazis", "The Nazis");

    places.put("Tanis", "Tanis");
    places.put("USA", "The USA");

    others.put("Ark", "The Ark");

    this.domainDescription = """
        There are two locations in this story: the United States and Tanis.
        There is one item: the Ark of the Covenant.
        There are four characters in this story.
        Indiana wants the US Army to have the Ark.
        The Nazis want to be immortal.
        The Nazis are armed.
        The US Army wants to have the Ark.
        The US Army is armed.

        There are five kinds of actions characters can take in the story.
        A character can travel from one location to another.
        If a character believes the Ark is at their current location they can dig up the Ark.
        One character can give an item to another.
        If one character is armed they can take an item from a second character by force.
        A character can open the Ark of the Covenant.
        If the Ark is safe, opening the Ark makes the character who opened it immortal.
        If the Ark is dangerous, opening the Ark kills the character who opened it.

        Tell me a story using only these locations, item, characters, and actions.
        Do not invent new locations, items, characters, or actions.
        The characters should try to achieve their goals.
        """;
  }

  @Override
  public String authorGoal() {
    return "that ends with the US Army having the Ark and the Nazis being dead.";
  }

  @Override
  public String characterGoals() {
    return "Indiana Jones wants the U.S. Army to have the Ark. "
        + "The U.S. Army wants to have the Ark. "
        + "The Nazis want to be immortal. ";
  }

  @Override
  public String actionTypes() {
    return "Characters can travel, give, take, dig, and open. ";
  }

  @Override
  public String goal() {
    // RaidersText.goal() is fixed; ignore goal int.
    return "The story must end with the Nazis being dead and the U.S. Army having the Ark. ";
  }

  @Override
  public String fluent(Fluent fluent, Expression value) {
    String str = believes(fluent, value);
    String arg0 = fluent.signature.arguments.get(0).toString();

    switch (fluent.signature.name) {
      case "armed":
        if (value.equals(True.TRUE))
          str += arg0 + " is armed";
        else
          str += arg0 + " is unarmed";
        break;

      case "dangerous":
        if (value.equals(True.TRUE))
          str += arg0 + " is dangerous";
        else
          str += arg0 + " is not dangerous";
        break;

      case "at":
        // Note: in RaidersText, "at" is overloaded: can mean location OR possession
        if (value.equals(Unknown.UNKNOWN)) {
          str += "where " + arg0 + " is";
        } else if (!isLocation(value.toString())) {
          str += value + " has " + arg0;
        } else {
          str += arg0 + " is at " + value;
        }
        break;

      case "status":
        if (value.toString().equals("Alive"))
          str += arg0 + " is alive";
        else if (value.toString().equals("Immortal"))
          str += arg0 + " is immortal";
        else
          str += arg0 + " is dead";
        break;

      default:
        str += fluent + " = " + value;
    }

    return clean(str)
        .replaceAll("USArmy", "The U.S. Army")
        .replaceAll("at USA", "in the USA")
        .replaceAll("Nazis", "The Nazis")
        .replaceAll("Jones", "Indiana Jones")
        .replaceAll("Ark", "The Ark")
        .replaceAll(" The ", " the ")
        .replaceAll("Nazis is", "Nazis are")
        .replaceAll("Nazis has", "Nazis have")
        .replaceAll("Nazis believes", "Nazis believe")
        .replaceAll("Nazis does", "Nazis do")
        + ". ";
  }

  private boolean isLocation(String str) {
    return str.equals("Tanis") || str.equals("USA");
  }

  @Override
  public String action(CompiledAction action) {
    String name = action.signature.name;

    ArrayList<String> args = new ArrayList<>();
    for (Parameter arg : action.signature.arguments)
      args.add(arg.toString());

    String arg0 = args.size() > 0 ? args.get(0) : "";
    String str = "";

    // RaidersText special-cases Nazis phrasing (plural + "die" on open)
    if ("Nazis".equals(arg0)) {
      switch (name) {
        case "travel":
          str = "Nazis travel from " + args.get(1) + " to " + args.get(2) + ".";
          break;
        case "dig":
          str = "Nazis dig up the " + args.get(1) + ".";
          break;
        case "give":
          str = "Nazis give the " + args.get(1) + " to " + args.get(2) + ".";
          break;
        case "take":
          str = "Nazis take the " + args.get(1) + " from " + args.get(2) + ".";
          break;
        case "open":
          str = "Nazis open the Ark and die.";
          break;
        default:
          return "";
      }
    } else {
      switch (name) {
        case "travel":
          str = arg0 + " travels from " + args.get(1) + " to " + args.get(2) + ".";
          break;
        case "dig":
          str = arg0 + " digs up the " + args.get(1) + ".";
          break;
        case "give":
          str = arg0 + " gives the " + args.get(1) + " to " + args.get(2) + ".";
          break;
        case "take":
          str = arg0 + " takes the " + args.get(1) + " from " + args.get(2) + ".";
          break;
        case "open":
          if ("Jones".equals(arg0))
            str = arg0 + " opens the Ark and dies.";
          else
            str = arg0 + " open the Ark and die.";
          break;
        default:
          return "";
      }
    }

    return clean(str)
        .replaceAll("USArmy", "The U.S. Army")
        .replaceAll("Nazis", "The Nazis")
        .replaceAll(" The ", " the ");
  }
}
