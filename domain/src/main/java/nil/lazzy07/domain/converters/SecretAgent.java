/*
* File name: SecretAgent.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-17 23:44:45
// Date modified: 2026-02-18 00:18:56
* ------
*/

package nil.lazzy07.domain.converters;

import java.util.ArrayList;

import edu.uky.cs.nil.sabre.Fluent;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import edu.uky.cs.nil.sabre.logic.Expression;
import edu.uky.cs.nil.sabre.logic.Parameter;
import edu.uky.cs.nil.sabre.logic.True;

public class SecretAgent extends DomainConverter {
  public SecretAgent(Expression initial, int goal) {
    super(initial, goal);

    agents.put("SecretAgent", "The secret agent");
    agents.put("Mastermind", "The mastermind");
    places.put("Headquarters", "The headquarters");
    places.put("Dropbox", "The dropbox");
    places.put("Lobby", "The lobby");
    places.put("Office", "The office");
    places.put("Cache", "The cache");
    places.put("Courtyard", "The courtyard");
    others.put("Gun", "The gun");
    others.put("Papers", "The papers");
    others.put("Guarded", "guarded");
    others.put("Unguarded", "unguarded");

    this.domainDescription = """
        There are six locations in this story: the headquarters, the dropbox, the lobby, the office, the cache, and the courtyard.
        There is a path between the headquarters and the dropbox. There is a path between the dropbox and courtyard.
        There is a path between the courtyard and the lobby. There is a path between the lobby and the cache.
        There is a path between the lobby and the office. There is a path between the cache and the office.

        There are two items: some papers and a gun. There are two characters in this story. The secret agent wants the mastermind to be dead.
        The mastermind wants to be alive. There are four kinds of actions characters can take in the story.
        A character can move from one location to another.

        If a character moves from the courtyard to the lobby, they must be holding the papers and not holding the gun.
        A character can pick up an item at their current location.

        If an item is missing, a character can discover it at their current location.
        One character with a gun can kill a second character at their current location.
                """;

    this.allAvailableActions = """
        """;
  }

  @Override
  public String authorGoal() {
    return "that ends with the mastermind being dead.";
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
        str += "The path between " + arg0 + " and " + args.get(1) + " is " + value;
        break;
      case "alive":
        if (value.equals(True.TRUE))
          str += arg0 + " is alive";
        else
          str += arg0 + " is not alive";
        break;
      case "at":
        str += standardLocation(arg0, value.toString(), str);
        break;
      default:
        str += fluent + " = " + value;
    }
    return clean(str)
        .replaceAll("papers is", "papers are") + ". ";
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
      case "move":
        str += arg0 + " moves from " + args.get(1) + " to " + args.get(2);
        break;
      case "pickup":
        str += arg0 + " picks up " + args.get(1);
        break;
      case "kill":
        str += arg0 + " kills " + args.get(1);
        break;
      case "find":
        str += arg0 + " finds " + args.get(1) + " in " + args.get(2);
        break;
      default:
        throw new RuntimeException(NO_ACTION + action);
    }
    return clean(str) + ".";
  }

  @Override
  public String characterGoals() {
    return "The secret agent wants the mastermind to be dead. " +
        "The mastermind wants to be alive. ";
  }

  @Override
  public String actionTypes() {
    return "Characters can move, pick up, kill, and find. ";
  }

  @Override
  public String goal() {
    return "The story must end with "
        + "the mastermind being dead. ";
  }
}
