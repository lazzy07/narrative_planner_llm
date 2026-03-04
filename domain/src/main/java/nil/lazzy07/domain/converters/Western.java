/*
* File name: Western.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-04 02:34:29
// Date modified: 2026-03-04 02:38:00
* ------
*/

package nil.lazzy07.domain.converters;

import java.util.ArrayList;

import edu.uky.cs.nil.sabre.Fluent;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import edu.uky.cs.nil.sabre.logic.Expression;
import edu.uky.cs.nil.sabre.logic.Parameter;
import edu.uky.cs.nil.sabre.logic.True;

public class Western extends DomainConverter {

  public Western(Expression initial, int goal) {
    super(initial, goal);

    // Agents
    agents.put("Hank", "Hank");
    agents.put("Timmy", "Timmy");
    agents.put("Will", "Sheriff Will");
    agents.put("Carl", "Carl");

    // Places
    places.put("Ranch", "The ranch");
    places.put("Saloon", "The saloon");
    places.put("Jailhouse", "The jailhouse");
    places.put("GeneralStore", "The general store");

    // Other objects / constants
    others.put("Antivenom", "The antivenom");
    others.put("Snakebite", "snakebite");

    // Domain description (natural-language prompt)
    this.domainDescription = """
        This domain describes a western story set in a small frontier town.
        The story has four characters: Hank, Timmy, Sheriff Will, and Carl.
        There are four locations: Ranch (the ranch), Saloon (the saloon), Jailhouse (the jailhouse), and GeneralStore (the general store).
        The antivenom item exists, and snakebite is an illness that can make people sick.

        Characters can get bitten by a snake, die, travel, give items, tie someone up, untie someone, take items, and heal.
        Everyone wants people they love to be healthy and free, and everyone wants to have the items they own.
        The sheriff wants stolen items to be returned to their owners.

        Tell me a story using only these locations, items, characters, and actions.
        Do not invent new locations, items, characters, or actions.
        The characters should try to achieve their goals.
        """;
  }

  @Override
  public String authorGoal() {
    return "";
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
      case "at":
        str += standardLocation(arg0, value.toString(), str);
        break;

      case "loves":
        if (value.equals(True.TRUE))
          str += arg0 + " loves " + args.get(1);
        else
          str += arg0 + " does not love " + args.get(1);
        break;

      case "sheriff":
        if (value.equals(True.TRUE))
          str += arg0 + " is the sheriff";
        else
          str += arg0 + " is not the sheriff";
        break;

      case "alive":
        if (value.equals(True.TRUE))
          str += arg0 + " is alive";
        else
          str += arg0 + " is not alive";
        break;

      case "sick":
        if (value.equals(True.TRUE))
          str += arg0 + " is sick from " + args.get(1);
        else
          str += arg0 + " is not sick from " + args.get(1);
        break;

      case "free":
        if (value.equals(True.TRUE))
          str += arg0 + " is free";
        else
          str += arg0 + " is not free";
        break;

      case "relationship":
        // relationship(Hank, Timmy) = <value>
        str += arg0 + "'s relationship with " + args.get(1) + " is " + value;
        break;

      case "cures":
        if (value.equals(True.TRUE))
          str += arg0 + " cures a " + args.get(1);
        else
          str += arg0 + " does not cure a " + args.get(1);
        break;

      case "owner":
        // owner(Antivenom) = Hank -> Hank owns Antivenom
        str += value + " owns " + arg0;
        break;

      case "possession":
        // possession(Antivenom, ?) = Hank -> Antivenom possesses Hank?s (kept as-is
        // from source text style)
        str += arg0 + " possesses " + value + args.get(1) + "s";
        break;

      case "stolen":
        // stolen(Antivenom, ?) = Hank -> Antivenom has stolen Hank?s (kept as-is from
        // source text style)
        str += arg0 + " has stolen " + value + args.get(1) + "s";
        break;

      default:
        str += fluent + " = " + value;
    }

    // WesternText replaces unknowns; keep it simple + safe:
    return clean(str).replace("?", "Unknown") + ". ";
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
      case "snakebite":
        str += arg0 + " gets bitten by a snake";
        break;

      case "die":
        str += arg0 + " dies from the " + args.get(1);
        break;

      case "travel":
        str += arg0 + " travels to " + args.get(1);
        break;

      case "give":
        str += arg0 + " gives " + args.get(1) + " to " + args.get(2);
        break;

      case "tie_up":
        str += arg0 + " ties up " + args.get(1) + " at " + args.get(2);
        break;

      case "untie":
        str += arg0 + " unties " + args.get(1);
        break;

      case "force_travel":
        str += arg0 + " takes " + args.get(1) + " to " + args.get(2);
        break;

      case "take":
        str += arg0 + " takes " + args.get(1) + " from " + args.get(2);
        break;

      case "heal":
        str += arg0 + " heals " + args.get(1) + "'s " + args.get(2) + " with " + args.get(3);
        break;

      default:
        return "";
    }

    return clean(str) + ".";
  }

  @Override
  public String characterGoals() {
    return "Everyone wants people they love to be healthy and free. "
        + "Everyone wants to have the items they own. "
        + "The sheriff wants stolen items to be returned to their owners. ";
  }

  @Override
  public String actionTypes() {
    return "Characters can get bitten by a snake, die, travel, give, tie up, untie, take, and heal. ";
  }

  @Override
  public String goal() {
    // WesternText uses a single fixed goal:
    return "The story must end with Timmy dead and Hank tied up at the jailhouse. ";
  }
}
