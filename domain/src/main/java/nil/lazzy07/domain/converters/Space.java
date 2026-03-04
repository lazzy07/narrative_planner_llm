/*
* File name: Space.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-04 02:45:47
// Date modified: 2026-03-04 02:46:58
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

public class Space extends DomainConverter {

  public Space(Expression initial, int goal) {
    super(initial, goal);

    agents.put("Zoe", "Zoe");
    agents.put("Lizard", "The lizard");

    places.put("Cave", "The cave");
    places.put("Surface", "The surface");
    places.put("Ship", "The ship");

    others.put("Safe", "safe");
    others.put("Dangerous", "dangerous");
    others.put("Uninhabitable", "uninhabitable");
    others.put("Healthy", "healthy");
    others.put("Stunned", "stunned");
    others.put("Dead", "dead");

    this.domainDescription = """
        There are three locations in this story: a spaceship orbiting a planet, the surface of the planet, and a cave on the planet. There are two characters in the story. Zoe is a space explorer and captain of the spaceship. Zoe wants to be healthy and safe and to make friends with other creatures. The Lizard is an alien creature that is the guardian of the planet. The Lizard wants to be healthy and safe and to make friends with other creatures. There are ten kinds of actions characters can take in the story. Zoe can teleport from the spaceship to the surface of the planet. Zoe can teleport from the surface of the planet to the spaceship, but this makes the Lizard angry. A character can walk between locations on the planet. Two characters who are in the same location can begin a fight, which causes them to be enemies. A fight automatically ends if a character is stunned, killed, or leaves the location where the fight is happening. One character can stun another character that they are fighting. A stunned character cannot take any actions except to break free. A stunned character can break free from being stunned, and after breaking free can act again. One character can kill another character that they are fighting. Two characters can become friends if they are not fighting. A volcano on the surface of the planet can start erupting at any time. A volcano on the surface which has started erupting can finish erupting, which kills any characters that are on the surface of the planet.
        """;
  }

  @Override
  public String authorGoal() {
    return "that ends with the volcano on the surface erupting, a character dying, two characters making friends, or any combination of these things.";
  }

  @Override
  public String fluent(Fluent fluent, Expression value) {
    String str = believes(fluent, value);
    ArrayList<String> args = new ArrayList<>();

    for (Parameter arg : fluent.signature.arguments)
      args.add(arg.toString());

    String arg0 = args.get(0);

    switch (fluent.signature.name) {
      case "path":
        if (value.equals(True.TRUE))
          str += "There is a path from " + arg0 + " to " + args.get(1);
        else
          str += "There is not a path from " + arg0 + " to " + args.get(1);
        break;

      case "status":
        str += arg0 + " is " + value;
        break;

      case "captain":
        if (value.equals(True.TRUE))
          str += arg0 + " is the captain of " + args.get(1);
        else
          str += arg0 + " is not the captain of " + args.get(1);
        break;

      case "guardian":
        if (value.equals(True.TRUE))
          str += arg0 + " is the guardian of " + args.get(1);
        else
          str += arg0 + " is not the guardian of " + args.get(1);
        break;

      case "at":
        str += arg0 + " is at " + value;
        break;

      case "safe":
        if (value.equals(True.TRUE))
          str += arg0 + " is safe";
        else
          str += arg0 + " is not safe";
        break;

      case "fighting":
        if (value.equals(True.TRUE))
          str += arg0 + " and " + args.get(1) + " are fighting";
        else
          str += arg0 + " and " + args.get(1) + " are not fighting";
        break;

      case "relationship":
        if (value.equals(Number.get(1)))
          str += arg0 + " and " + args.get(1) + " are friends";
        else if (value.equals(Number.get(-1)))
          str += arg0 + " and " + args.get(1) + " are enemies";
        else
          str += arg0 + " and " + args.get(1) + " have no relationship";
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

    String arg0 = args.size() > 0 ? args.get(0) : "";
    String str = "";

    switch (name) {
      case "teleport_from_ship":
        str += arg0 + " teleports from " + args.get(1) + " to " + args.get(2);
        break;

      case "teleport_to_ship":
        str += arg0 + " teleports to " + args.get(2) + " from " + args.get(1);
        break;

      case "walk":
        str += arg0 + " walks from " + args.get(1) + " to " + args.get(2);
        break;

      case "attack":
        str += arg0 + " attacks " + args.get(1);
        break;

      case "stun":
        str += arg0 + " stuns " + args.get(1);
        break;

      case "kill":
        str += arg0 + " kills " + args.get(1);
        break;

      case "break_free":
        str += arg0 + " breaks free";
        break;

      case "make_peace":
        str += arg0 + " makes peace with " + args.get(1);
        break;

      case "begin_erupt":
        str += arg0 + " begins to erupt";
        break;

      case "erupt":
        str += arg0 + " erupts";
        break;

      default:
        return "";
    }

    return clean(str) + ".";
  }

  @Override
  public String characterGoals() {
    return "Zoe and the lizard each want to have friends and be healthy and safe. ";
  }

  @Override
  public String actionTypes() {
    return "Characters can teleport to and from the ship, walk, attack, stun, kill, break free, and make peace. "
        + "Landforms can begin to erupt and erupt. ";
  }

  @Override
  public String goal() {
    String text = "The story must end with ";
    switch (goal) {
      case 4:
        return text
            + "Zoe and the lizard being friends and either one of them being dead or the surface being uninhabitable. ";
      case 1:
        return text
            + "either the surface being uninhabitable, Zoe and the lizard being friends, or one of them being dead. ";
      default:
        return "";
    }
  }
}
