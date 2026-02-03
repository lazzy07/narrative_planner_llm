package nil.lazzy07.domain.converters;

import edu.uky.cs.nil.sabre.Fluent;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import edu.uky.cs.nil.sabre.logic.Expression;
import edu.uky.cs.nil.sabre.logic.Parameter;
import edu.uky.cs.nil.sabre.logic.True;

import java.util.ArrayList;

public class DeerHunter extends DomainConverter {
  public DeerHunter(Expression initial, int goal) {
    super(initial, goal);
    agents.put("Bubba","Bubba");
    agents.put("Clerk","The clerk");
    agents.put("Bambi","Bambi");
    places.put("House","The house");
    places.put("Bank","The bank");
    places.put("Forest","The forest");
    containers.put("Rifle", "The rifle");
    others.put("Rifle","The rifle");
    others.put("Ammo","The ammunition");

    this.domainDescription = """
                There are three locations in this story: the house, the bank, and the forest. There are three items in this story: a rifle, some ammunition, and some money.
                There are three characters in this story: Bubba is a human. Bubba wants to be alive. The bank clerk is a human. The bank clerk wants to be alive. Bambi is a deer. Bambi wants to be alive.
                """;

    this.allAvailableActions = """
                There are eight kinds of actions characters can take in the story:
                A character can become greedy, and then they will want to have money.
                A character can become hungry, and then they will want to eat. A character can pick up an item at their location.
                A character can load ammunition into a rifle.
                A character can go from one location to another.
                One character can steal an item from a second character if the first character has a loaded gun.
                One character can shoot and kill another character if the first character has a loaded gun.
                One character can eat an animal if the animal is dead.
                """;
  }

  public String authorGoal() {
    return "that ends with Bubba having the money or Bambi being dead or both.";
  }

  @Override
  public String fluent(Fluent fluent, Expression value) {
    String str = believes(fluent, value);
    ArrayList<String> args = new ArrayList<>();
    for(Parameter arg : fluent.signature.arguments)
      args.add(arg.toString());
    String arg0 = args.get(0);
    switch(fluent.signature.name) {
      case "path":
        if(value.equals(True.TRUE))
          str += "There is a path from " + arg0 + " to " + args.get(1);
        else str += "There is not a path from " + arg0 + " to " + args.get(1);
        break;
      case "alive":
        if(value.equals(True.TRUE))
          str += arg0 + " is alive";
        else str += arg0 + " is not alive";
        break;
      case "at":
        str += standardLocation(arg0, value.toString(), str);
        break;
      case "rich":
        if(value.equals(True.TRUE))
          str += arg0 + " is rich";
        else str += arg0 + " is not rich";
        break;
      case "hungry":
        if(value.equals(True.TRUE))
          str += arg0 + " is hungry";
        else str += arg0 + " is not hungry";
        break;
      case "money":
        str += arg0 + " has " + value + " money";
        break;
      case "greed":
        str += arg0 + " wants to have " + value + " money";
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
    for(Parameter arg : action.signature.arguments)
      args.add(arg.toString());
    String arg0 = args.get(0);
    String str = "";
    switch(name) {
      case "decide_to_get_money":
        str += arg0 + " decides to get money";
        break;
      case "decide_to_eat":
        str += arg0 + " decides to eat";
        break;
      case "pickup":
        str += arg0 + " picks up " + args.get(1);
        break;
      case "load":
        str += arg0 + " loads " + args.get(2) + " into " + args.get(1);
        break;
      case "go":
        str += arg0 + " goes from " + args.get(1) + " to " + args.get(2);
        break;
      case "steal":
        str += arg0 + " steals money from " + args.get(1) +  " at gunpoint";
        break;
      case "shoot":
        str += arg0 + " shoots " + args.get(1) + " with " + args.get(2);
        break;
      case "eat":
        str += arg0 + " eats " + args.get(1);
        break;
      default:
        throw new RuntimeException(NO_ACTION + action);
    }
    return clean(str) + ".";
  }

  @Override
  public String characterGoals() {
    return "Bubba and the clerk want to be alive and rich, and do not want to be hungry. " +
            "Bambi wants to be alive. ";
  }

  @Override
  public String actionTypes() {
    return "Characters can eat, shoot, steal, go, load, pick up, decide to eat, and decide to get money. ";
  }


  @Override
  public String goal() {
    String text = "The story must end with ";
    return switch (goal) {
      case 2 -> text + "Bubba being rich and Bambi having been eaten. ";
      case 1 -> text + "either Bubba being rich or Bambi having been eaten. ";
      default -> "";
    };
  }
}
