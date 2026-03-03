/*
* File name: Aladdin.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-25 04:33:08
// Date modified: 2026-02-25 09:10:29
* ------
*/

package nil.lazzy07.domain.converters;

import java.util.ArrayList;

import edu.uky.cs.nil.sabre.Fluent;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import edu.uky.cs.nil.sabre.logic.Expression;
import edu.uky.cs.nil.sabre.logic.Parameter;
import edu.uky.cs.nil.sabre.logic.True;

public class Aladdin extends DomainConverter {
  public Aladdin(Expression initial, int goal) {
    super(initial, goal);
    agents.put("Aladdin", "Aladdin");
    agents.put("Jafar", "Jafar");
    agents.put("Jasmine", "Jasmine");
    agents.put("Dragon", "The dragon");
    agents.put("Genie", "The genie");
    places.put("Castle", "The castle");
    places.put("Mountain", "The mountain");
    containers.put("Lamp", "The magic lamp");
    others.put("Lamp", "The magic lamp");

    this.domainDescription = """
        The problem is an alternative version of the tale of Aladdin, which was added to a later edition of One Thousand and One Nights and later adapted into a 1992 Disney film with some elements been changed. Jafar is king, not adviser to the king. A dragon has been added. The end of the story has been changed. Jasmine should be married to Jafar and the genie should be dead. Everyone wants to be alive and unafraid. Aladdin, Jafar, and Jasmine each want to be happy. Servants want to do what their masters command them to do. The story must end with either Jafar being married to Jasmine or the genie being dead. There are 5 characters: Aladdin, Jafar, Jasmine, Dragon and Genie. Also there are 2 places: Castle and Mountain.
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
      case "alive":
        if (value.equals(True.TRUE))
          str += arg0 + " is alive";
        else
          str += arg0 + " is not alive";
        break;
      case "path":
        if (value.equals(True.TRUE))
          str += "There is a path from " + arg0 + " to " + args.get(1);
        else
          str += "There is not a path from " + arg0 + " to " + args.get(1);
        break;
      case "master":
        str += value + " is the master of " + arg0;
        break;
      case "loves":
        if (value.equals(True.TRUE))
          str += arg0 + " loves " + args.get(1);
        else
          str += arg0 + " does not love " + args.get(1);
        break;
      case "spouse":
        str += value + " is the spouse of " + arg0;
        break;
      case "happy":
        if (value.equals(True.TRUE))
          str += arg0 + " is happy";
        else
          str += arg0 + " is not happy";
        break;
      case "fears":
        if (value.equals(True.TRUE))
          str += arg0 + " fears " + args.get(1);
        else
          str += arg0 + " does not fear " + args.get(1);
        break;
      case "afraid":
        if (value.equals(True.TRUE))
          str += arg0 + " is afraid";
        else
          str += arg0 + " is not afraid";
        break;
      case "tasks":
        str += arg0 + " has completed " + value + " tasks";
        break;
      case "task_kill":
        if (value.equals(True.TRUE))
          str += arg0 + " is tasked with killing " + args.get(2) + " for " + args.get(1);
        else
          str += arg0 + " is not tasked with killing " + args.get(2) + " for " + args.get(1);
        break;
      case "task_love":
        if (value.equals(True.TRUE))
          str += arg0 + " is tasked with making " + args.get(2) + " love " + args.get(1);
        else
          str += arg0 + " is not tasked with making " + args.get(2) + " love " + args.get(1);
        break;
      case "task_bring":
        if (value.equals(True.TRUE))
          str += arg0 + " is tasked with bringing " + args.get(2) + " to " + args.get(1);
        else
          str += arg0 + " is not tasked with bringing " + args.get(2) + " to " + args.get(1);
        break;
      default:
        str += fluent + " = " + value;
    }
    return clean(str).replaceAll(" There ", " there ") + ". ";
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
      case "travel":
        str += arg0 + " travels from " + args.get(1) + " to " + args.get(2);
        break;
      case "slay":
        str += arg0 + " slays " + args.get(1);
        break;
      case "pillage":
        str += arg0 + " pillages " + args.get(2) + " from " + args.get(1);
        break;
      case "give":
        str += arg0 + " gives " + args.get(2) + " to " + args.get(1);
        break;
      case "summon":
        str += arg0 + " summons " + args.get(1) + " from " + args.get(2);
        break;
      case "love_spell":
        str += arg0 + " casts a spell to make " + args.get(1) + " love " + args.get(2);
        break;
      case "marry":
        str += arg0 + " and " + args.get(1) + " get married";
        break;
      case "fall_in_love":
        str += arg0 + " falls in love with " + args.get(1);
        break;
      case "command_kill":
        str += arg0 + " commands " + args.get(1) + " to kill " + args.get(2);
        break;
      case "command_love":
        str += arg0 + " commands " + args.get(1) + " to make " + args.get(2) + " love " + arg0;
        break;
      case "command_bring":
        str += arg0 + " commands " + args.get(1) + " to bring " + args.get(2);
        break;
      case "appear_threatening":
        str += arg0 + " appears threatening to " + args.get(1);
        break;
      default:
        return "";
    }
    return clean(str) + ".";
  }

  @Override
  public String characterGoals() {
    return "Everyone wants to be alive and unafraid. "
        + "Aladdin, Jafar, and Jasmine each want to be happy. "
        + "Servants want to do what their masters command them to do. ";
  }

  @Override
  public String actionTypes() {
    return "Characters can travel, slay, pillage, give, summon, cast a love spell, marry, fall in love, and command. ";
  }

  @Override
  public String goal() {
    String text = "The story must end with ";
    switch (goal) {
      case 2:
        return text + "Jafar being married to Jasmine and the genie being dead. ";
      case 1:
        return text + "either Jafar being married to Jasmine or the genie being dead. ";
      default:
        return "";
    }
  }
}
