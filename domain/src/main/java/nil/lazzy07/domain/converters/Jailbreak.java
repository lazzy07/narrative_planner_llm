/*
* File name: Jailbreak.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-04 02:58:22
// Date modified: 2026-03-04 02:58:30
* ------
*/

package nil.lazzy07.domain.converters;

import java.util.ArrayList;

import edu.uky.cs.nil.sabre.Fluent;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import edu.uky.cs.nil.sabre.logic.Expression;
import edu.uky.cs.nil.sabre.logic.Parameter;
import edu.uky.cs.nil.sabre.logic.True;

public class Jailbreak extends DomainConverter {

  public Jailbreak(Expression initial, int goal) {
    super(initial, goal);

    agents.put("Ernest", "Ernest");
    agents.put("Roy", "Roy");
    agents.put("Bully", "The bully");

    places.put("Cells", "The cells");
    places.put("Laundry", "The laundry room");
    places.put("Kitchen", "The kitchen");
    places.put("Gym", "The gym");
    places.put("Hall", "The hall");
    places.put("Highway", "The highway");

    others.put("Cigarettes", "The cigarettes");
    others.put("Clothes", "The clothes");
    others.put("Knife", "The knife");

    this.domainDescription = """
        There are six locations in this story: a prison cell block, a prison laundry room, a prison kitchen,
        a prison gym, a prison hallway, and the highway outside the prison.
        There are three items in this story: a pack of cigarettes, a set of civilian clothes, and a knife.
        There are two days in the story: day one and day two.
        There are three characters in this story. Ernest is a prisoner. Roy is a prisoner. The bully is a prisoner.
        Ernest wants to be alive, does not want to be threatened, and wants to have a pack of cigarettes.
        Roy wants to be alive, does not want to be threatened, and wants to have a pack of cigarettes.
        Ernest and Roy are friends. Ernest and Roy are the main characters.
        The bully wants to kill people he has threatened and wants to spend time in the gym. The bully owns the pack of cigarettes.

        There are thirteen kinds of actions characters can take.
        A main character can steal an item which is at their current location.
        If a main character steals the bully’s cigarettes, the bully threatens both Ernest and Roy.
        The bully can kill a main character in any room except for the cell block.
        The prison guards can confiscate an item from one of the main characters and punish the main character by making them clean the highway or clean the gym.
        A main character can go to the laundry room or the kitchen to do their daily chores.
        The bully can go to the gym for recreation time.
        A main character can move from any room to the hallway.
        A main character who is in the hallway can crawl through the vents to get to either the highway or the gym.
        If a main character is on the highway or in the hallway they can put on the civilian clothes to disguise themselves.
        A main character who is in the hallway can lock the gym door.
        A main character can kill the bully with a knife in any room except the cell block.
        The prison guards can thwart a main character who is carrying the civilian clothes or the knife by killing the main character.
        If a main character is wearing the civilian clothes disguise and are on the highway they can escape from the prison.
        If a main character is locked in the gym with the bully and has the knife, the main character can get revenge by killing the bully.
        Day one can end at any time and day two begins.

        Tell me a story using only these locations, items, characters, and actions.
        Do not invent new locations, items, characters, or actions.
        The characters should try to achieve their goals.
        """;
  }

  @Override
  public String authorGoal() {
    return "that ends with Ernest dying or Roy dying or where Ernest or Roy overcomes the bully’s threats by either escaping or getting revenge.";
  }

  @Override
  public String fluent(Fluent fluent, Expression value) {
    String str = believes(fluent, value);

    ArrayList<String> args = new ArrayList<>();
    for (Parameter arg : fluent.signature.arguments)
      args.add(arg.toString());

    switch (fluent.signature.name) {
      case "location":
        str += standardLocation(args.get(0), value.toString(), str).replace(" at ", " in ");
        break;

      case "alive":
        if (value.equals(True.TRUE))
          str += args.get(0) + " is alive";
        else
          str += args.get(0) + " is not alive";
        break;

      case "time":
        if (value.toString().equals("Day1"))
          str += "It is the first day";
        else if (value.toString().equals("Day2"))
          str += "It is the second day";
        else
          str += "What day it is";
        break;

      case "threatened":
        if (value.equals(True.TRUE))
          str += args.get(0) + " is threatened";
        else
          str += args.get(0) + " is not threatened";
        break;

      case "chores":
        if (value.equals(True.TRUE))
          str += args.get(0) + " has done his chores";
        else
          str += args.get(0) + " has not done his chores";
        break;

      case "locked":
        if (value.equals(True.TRUE))
          str += args.get(0) + " is locked";
        else
          str += args.get(0) + " is not locked";
        break;

      case "disguised":
        if (value.equals(True.TRUE))
          str += args.get(0) + " is disguised";
        else
          str += args.get(0) + " is not disguised";
        break;

      default:
        str += fluent + " = " + value;
        break;
    }

    return clean(str)
        .replaceAll(" What ", " what ")
        .replaceAll(" It ", " it ")
        .replaceAll("cigarettes is", "cigarettes are")
        .replaceAll("clothes is", "clothes are")
        + ". ";
  }

  @Override
  public String action(CompiledAction action) {
    String name = action.signature.name;

    ArrayList<String> args = new ArrayList<>();
    for (Parameter arg : action.signature.arguments)
      args.add(arg.toString());

    String str = "";

    switch (name) {
      case "confiscate":
        str += "A guard confiscates " + args.get(1) + " from " + args.get(0)
            + " and sends him to " + args.get(3) + " for punishment duties";
        break;

      case "chores":
        str += args.get(0) + " does his chores in " + args.get(1);
        break;

      case "disguise":
        str += args.get(0) + " puts on the clothes in " + args.get(2);
        break;

      case "escape":
        str += args.get(0) + " runs down the highway and escapes";
        break;

      case "go":
        str += args.get(0) + " goes to the hall";
        break;

      case "kill":
        str += args.get(0) + " kills " + args.get(1) + " in " + args.get(2);
        break;

      case "lock_gym":
        str += args.get(0) + " locks the door to the gym";
        break;

      case "next_day":
        str += "The prisoners go to sleep in their cells and wake up the next day";
        break;

      case "recreation":
        str += args.get(0) + " goes to the gym for recreation";
        break;

      case "revenge":
        str += args.get(0) + " kills the bully in the gym";
        break;

      case "steal":
        str += args.get(0) + " steals " + args.get(1) + " from " + args.get(2);
        if (args.get(1).equals("Cigarettes"))
          str += ". This angers the bully, who threatens to kill both Roy and Ernest";
        break;

      case "thwart":
        str += args.get(0) + " is thwarted in the hall";
        break;

      case "vent":
        str += args.get(0) + " sneaks through the vent to " + args.get(1);
        break;

      default:
        return "";
    }

    return clean(str).replaceAll(" A ", " a ") + ".";
  }

  @Override
  public String characterGoals() {
    return "Ernest wants to have the cigarettes and not be threatened or dead. "
        + "Roy wants to have the cigarettes and not be threatened or dead. "
        + "The bully wants to be in the gym and to have killed anyone he has threatened. ";
  }

  @Override
  public String actionTypes() {
    return "Characters can steal items, have items get confiscated, do chores, put on clothes, escape, kill, lock doors, go to the hall, be thwarted, and sneak through vents. ";
  }

  @Override
  public String goal() {
    String text = "The story must end with ";
    switch (goal) {
      case 2:
        return text + "Roy or Ernest either escaping onto the highway or killing the bully. ";
      case 1:
        return text + "Roy or Ernest either being thwarted, escaping onto the highway, or killing the bully. ";
      default:
        return "";
    }
  }
}
