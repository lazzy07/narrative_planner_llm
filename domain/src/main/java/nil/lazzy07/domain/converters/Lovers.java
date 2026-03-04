/*
* File name: Lovers.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-04 03:02:22
// Date modified: 2026-03-04 03:02:27
* ------
*/

package nil.lazzy07.domain.converters;

import java.util.ArrayList;

import edu.uky.cs.nil.sabre.Fluent;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import edu.uky.cs.nil.sabre.logic.Expression;
import edu.uky.cs.nil.sabre.logic.Parameter;

public class Lovers extends DomainConverter {

  public Lovers(Expression initial, int goal) {
    super(initial, goal);

    // agents
    agents.put("C1", "Alex");
    agents.put("C2", "Blake");
    agents.put("C3", "Casey");

    // places
    places.put("R11", "The bedroom");
    places.put("R12", "The bathroom");
    places.put("R21", "The dining room");
    places.put("R22", "The living room");

    // items / others
    others.put("I1", "The flowers");
    others.put("I2", "The chocolates");
    others.put("I3", "The jewelry");

    this.domainDescription = """
        There are four locations in this story: the bedroom, the bathroom, the dining room, and the living room.
        The bedroom is connected to the bathroom and the dining room.
        The bathroom is connected to the bedroom and the living room.
        The dining room is connected to the bedroom and the living room.
        The living room is connected to the bathroom and the dining room.

        There are three items in this story: flowers, chocolates, and jewelry.

        There are three characters in this story.
        Alex wants to be happy and also wants Casey to be happy.
        Blake wants to be happy and also wants Alex to be happy.
        Casey wants to be happy and also wants Blake to be happy.
        A character is happy if they have the item they want.

        There are six kinds of actions characters can take in the story.
        One character can tell a second character what item the first character wants, but this can be a lie.
        A character can pick up an item that is in the same room.
        A character can put an item they are holding down in the room they are in.
        One character can give an item they have to a second character.
        Two characters can trade items they have.
        A character can move from one room to another if the rooms are connected.

        Tell me a story using only these locations, items, characters, and actions.
        Do not invent new locations, items, characters, or actions.
        The characters should try to achieve their goals.
        """;
  }

  @Override
  public String authorGoal() {
    // keep consistent with your Text class style (even though it mentions Tom
    // there)
    return "Give me the shortest story that ends with Tom at the cottage with both Alex and Casey being happy.";
  }

  @Override
  public String characterGoals() {
    return "Alex wants to be happy and wants Casey to be happy. "
        + "Blake wants to be happy and wants Alex to be happy. "
        + "Casey wants to be happy and wants Blake to be happy. "
        + "A character is happy if they have the item they want. ";
  }

  @Override
  public String actionTypes() {
    return "Characters can tell, pick up, put down, give, trade, and move. ";
  }

  @Override
  public String goal() {
    // You didn't provide LoversText.goal(); keep a minimal goal mapping (common in
    // your converters).
    // If your compiled domain uses a different goal mapping, update these strings
    // to match it.
    String text = "The story must end with ";
    switch (goal) {
      case 2:
        return text + "both Alex and Casey being happy. ";
      case 1:
        return text + "Alex being happy or Casey being happy. ";
      default:
        return "";
    }
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
      case "happy":
        // boolean-like (True/False) in most sabre domains
        if ("True".equals(value.toString()))
          str += arg0 + " is happy";
        else
          str += arg0 + " is not happy";
        break;

      case "at": {
        // overloaded: persons are "in room", items are "in room" or "with person"
        String v = value.toString();
        if (v.equals("?")) {
          str += "where " + arg0 + " are";
          break;
        }

        if (!isItem(arg0)) {
          str += arg0 + " is in " + v;
        } else {
          if (isPlace(v))
            str += arg0 + " are in " + v;
          else
            str += arg0 + " are with " + v;
        }
        break;
      }

      case "wants":
        str += arg0 + " wants " + value;
        break;

      case "loves":
        str += arg0 + " loves " + value;
        break;

      default:
        str += fluent + " = " + value;
        break;
    }

    return clean(str).replaceAll("\\?", "Unknown") + ". ";
  }

  @Override
  public String action(CompiledAction action) {
    String name = action.signature.name;

    ArrayList<String> args = new ArrayList<>();
    for (Parameter arg : action.signature.arguments) {
      args.add(arg.toString());
    }

    String str = "";
    switch (name) {
      case "tell":
        // tell(speaker, listener, item)
        str = args.get(0) + " tells " + args.get(1) + " that " + args.get(2) + " are wanted";
        break;

      case "pick_up":
        // pick_up(agent, item, room)
        str = args.get(0) + " picks up " + theItem(args.get(1)) + " in " + args.get(2);
        break;

      case "put_down":
        // put_down(agent, item, room)
        str = args.get(0) + " puts down " + theItem(args.get(1)) + " in " + args.get(2);
        break;

      case "give":
        // give(giver, item, receiver, room)
        str = args.get(0) + " gives " + theItem(args.get(1)) + " to " + args.get(2) + " in " + args.get(3);
        break;

      case "trade":
        // trade(a1, item1, a2, item2, room)
        str = args.get(0) + " trades " + theItem(args.get(1)) + " with " + args.get(2)
            + " for " + theItem(args.get(3)) + " in " + args.get(4);
        break;

      case "move":
        // move(agent, from, to)
        str = args.get(0) + " moves from " + args.get(1) + " to " + args.get(2);
        break;

      default:
        return "";
    }

    return clean(str) + ".";
  }

  private String theItem(String item) {
    switch (item) {
      case "I1":
        return "flowers";
      case "I2":
        return "chocolates";
      case "I3":
        return "jewelry";
      default:
        // sometimes item names might already be literal like "flowers"
        if (item.toLowerCase().contains("flower"))
          return "flowers";
        if (item.toLowerCase().contains("chocolate"))
          return "chocolates";
        if (item.toLowerCase().contains("jewel"))
          return "jewelry";
        return "an unknown item";
    }
  }

  private boolean isItem(String value) {
    String v = value.toLowerCase();
    return v.contains("i1") || v.contains("i2") || v.contains("i3")
        || v.contains("jewelry") || v.contains("chocolates") || v.contains("flowers")
        || v.contains("jwelry"); // to tolerate the misspelling you had in LoversText
  }

  private boolean isPlace(String value) {
    String v = value.toLowerCase();
    return v.contains("r11") || v.contains("r12") || v.contains("r21") || v.contains("r22")
        || v.contains("bedroom") || v.contains("bathroom") || v.contains("dining") || v.contains("living");
  }
}
