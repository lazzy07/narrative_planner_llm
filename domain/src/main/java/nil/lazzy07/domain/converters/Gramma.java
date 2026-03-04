/*
* File name: Gramma.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-04 02:54:34
// Date modified: 2026-03-04 02:56:22
* ------
*/

package nil.lazzy07.domain.converters;

import edu.uky.cs.nil.sabre.Fluent;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import edu.uky.cs.nil.sabre.logic.Expression;
import edu.uky.cs.nil.sabre.logic.Parameter;
import edu.uky.cs.nil.sabre.logic.True;
import edu.uky.cs.nil.sabre.logic.Unknown;
import edu.uky.cs.nil.sabre.util.ImmutableArray;

public class Gramma extends DomainConverter {

  public Gramma(Expression initial, int goal) {
    super(initial, goal);

    // agents
    agents.put("Merchant", "The merchant");
    agents.put("Guard", "The guard");
    agents.put("Bandit", "The bandit");
    agents.put("Tom", "The hero");

    // places
    places.put("Cottage", "The cottage");
    places.put("Crossroads", "The crossroads");
    places.put("Market", "The market");
    places.put("Camp", "The camp");

    // items / others
    others.put("MerchantSword", "The merchant's sword");
    others.put("GuardSword", "The guard's sword");
    others.put("BanditSword", "The bandit's sword");
    others.put("TomCoin", "The hero's coin");
    others.put("BanditCoin", "The bandit's coin");
    others.put("Medicine", "The medicine");

    this.domainDescription = """
        There are four locations in this story: the cottage, the market, the bandit camp, and a crossroads.
        There is a path from the cottage to the crossroads.
        There is a path from the market to the crossroads.
        There is a path from the bandit camp to the crossroads.

        There are six items in this story: three swords, two coins, and some medicine.
        There are four characters in this story.
        The hero is named Tom. Tom wants to be at the cottage carrying the medicine.
        There is also a merchant character. The merchant wants to get as many coins as she can without becoming a criminal, and she prefers to be at the market.
        There is also a guard character. The guard wants to kill criminals, and he prefers to be at the market.
        There is also a bandit character. The bandit is a criminal. The bandit wants to be carrying as many coins as she can or to have coins in the chest at the bandit camp, and she prefers to be at the bandit camp.

        There are seven kinds of actions characters can take in the story.
        A character can walk from one location to another if there is a path.
        A character can buy an item from the merchant by giving the merchant a coin they are carrying.
        One character who is carrying a sword can rob a second character and take one of the items the second character is carrying as long as the second character is not carrying a sword.
        One character who is carrying a sword can attack and kill a second character as long as the second character is not carrying a sword.
        A character can loot an item from the corpse of a dead character.
        A character can report the location of the bandit to the guard.

        Tell me a story using only these locations, items, characters, and actions.
        Do not invent new locations, items, characters, or actions.
        The characters should try to achieve their goals.
        """;
  }

  @Override
  public String characterGoals() {
    return "The hero wants to bring the medicine to the cottage. "
        + "The bandit wants to collect valuable items in the chest. "
        + "The merchant wants coins and is not willing to commit crimes. "
        + "The guard wants to attack criminals. ";
  }

  @Override
  public String authorGoal() {
    return " that ends with Tom at the cottage with the medicine.";
  }

  @Override
  public String actionTypes() {
    return "Characters can walk, buy, loot, attack, rob, report, and take. ";
  }

  @Override
  public String goal() {
    switch (goal) {
      case 2:
        return "The story must end with the hero having the medicine at the cottage. ";
      case 1:
        return "The story must end with the hero either being attacked or having the medicine at the cottage. ";
      default:
        return "";
    }
  }

  @Override
  public String fluent(Fluent fluent, Expression value) {
    String str = believes(fluent, value);
    String arg0 = fluent.signature.arguments.get(0).toString();

    switch (fluent.signature.name) {
      case "alive":
        if (value.equals(True.TRUE))
          str += arg0 + " is alive";
        else
          str += arg0 + " is dead";
        break;

      case "criminal":
        if (value.equals(True.TRUE))
          str += arg0 + " is a criminal";
        else
          str += arg0 + " is not a criminal";
        break;

      case "location":
        if (isItem(arg0)) {
          if (value.equals(Unknown.UNKNOWN))
            str += "where " + theItem(arg0) + " is";
          else if (value.toString().equals("Chest"))
            str += theItem(arg0) + " is in the chest";
          else
            str += value + " has " + theItem(arg0);
        } else {
          if (value.equals(Unknown.UNKNOWN))
            str += "where " + arg0 + " is";
          else
            str += arg0 + " is at " + value;
        }
        break;

      case "path":
        if (value.equals(True.TRUE))
          str += "There is a path between " + arg0 + " and " + fluent.signature.arguments.get(1);
        break;

      default:
        str += fluent + " = " + value;
    }

    return clean(str) + ". ";
  }

  private boolean isItem(String str) {
    if (str.contains("Coin"))
      return true;
    if (str.contains("Sword"))
      return true;
    return str.equals("Medicine");
  }

  private String theItem(String item) {
    if (item.contains("Coin")) {
      if (item.contains("Bandit"))
        return "Bandit's coin";
      else
        return "Tom's coin";
    } else if (item.contains("Sword")) {
      if (item.contains("Bandit"))
        return "Bandit's sword";
      else if (item.contains("Guard"))
        return "Guard's sword";
      else
        return "Merchant's sword";
    } else {
      return "the medicine";
    }
  }

  private String reflexivePronoun(String agent) {
    switch (agent) {
      case "Merchant":
        return "herself";
      default:
        return "himself";
    }
  }

  @Override
  public String action(CompiledAction action) {
    String name = action.signature.name;
    ImmutableArray<Parameter> args = action.signature.arguments;
    String arg0 = args.size() > 0 ? args.get(0).toString() : "";
    String str = "";

    switch (name) {
      case "attack":
        if (args.get(0).equals(args.get(1)))
          str = arg0 + " attacks " + reflexivePronoun(args.get(1).toString());
        else
          str = arg0 + " attacks " + args.get(1);
        break;

      case "buy":
        str = arg0 + " buys " + theItem(args.get(1).toString()) + " from Merchant";
        break;

      case "loot":
        str = arg0 + " loots " + theItem(args.get(1).toString()) + " from " + args.get(2);
        break;

      case "report":
        if (arg0.equals("Guard"))
          str = arg0 + " reports seeing Bandit at " + args.get(1) + " to himself";
        else if (arg0.equals("Bandit"))
          str = arg0 + " reports seeing himself at " + args.get(1) + " to Guard";
        else
          str = arg0 + " reports seeing Bandit at " + args.get(1) + " to Guard";
        break;

      case "rob":
        str = arg0 + " robs " + theItem(args.get(1).toString()) + " from " + args.get(2);
        break;

      case "take":
        str = arg0 + " takes " + theItem(args.get(1).toString()) + " from the chest";
        break;

      case "walk":
        str = arg0 + " walks from " + args.get(1) + " to " + args.get(2);
        break;

      default:
        return "";
    }

    return clean(str) + ".";
  }
}
