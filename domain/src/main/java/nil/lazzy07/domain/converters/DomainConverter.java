package nil.lazzy07.domain.converters;

import edu.uky.cs.nil.sabre.Fluent;
import edu.uky.cs.nil.sabre.HeadPlan;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import edu.uky.cs.nil.sabre.logic.Effect;
import edu.uky.cs.nil.sabre.logic.Expression;
import edu.uky.cs.nil.sabre.logic.Unknown;

import java.util.LinkedHashMap;

public abstract class DomainConverter {
  public static final String NO_ACTION = "Action not implemented: ";
  public static final String NO_FLUENT = "Fluent not implemented: ";
  public static final String NO_METHOD = "Not implemented.";

  public static final boolean GRAMMA_SCRAMBLED = false;
  public final LinkedHashMap<String, String> agents = new LinkedHashMap<>();
  public final LinkedHashMap<String, String> places = new LinkedHashMap<>();
  public final LinkedHashMap<String, String> containers = new LinkedHashMap<>();
  public final LinkedHashMap<String, String> others = new LinkedHashMap<>();
  public final int goal;
  public final Expression initial;

  public String domainDescription;
  public String allAvailableActions;

  public abstract String characterGoals();
  public abstract String actionTypes();
  public abstract String goal();
  public abstract String action(CompiledAction action);
  public abstract String fluent(Fluent fluent, Expression value);
  public abstract String authorGoal();

  public DomainConverter(Expression initial, int goal) {
    this.initial = initial;
    this.goal = goal;
  }

  public String believes(Fluent fluent, Expression value) {
    StringBuilder str = new StringBuilder();
    for(int i=0; i<fluent.characters.size(); i++) {
      if(!value.equals(Unknown.UNKNOWN) || (i+1 < fluent.characters.size()))
        str.append(fluent.characters.get(i)).append(" believes that ");
      else
        str.append(fluent.characters.get(i)).append(" does not know ");
    }
    return str.toString();
  }

  public String believesStr(Fluent fluent, String value) {
    StringBuilder str = new StringBuilder();
    for(int i=0; i<fluent.characters.size(); i++) {
      if(!value.equals("?") || (i+1 < fluent.characters.size()))
        str.append(fluent.characters.get(i)).append(" believes that ");
      else
        str.append(fluent.characters.get(i)).append(" does not know ");
    }
    return str.toString();
  }

  public String initialState() {
    return state(this.initial);
  }

  public String plan(HeadPlan<CompiledAction> plan) {
    StringBuilder str = new StringBuilder();
    for(CompiledAction action : plan)
      str.append(action(action)).append(" ");
    return str.toString();
  }

  public String state(Expression expression) {
    StringBuilder str = new StringBuilder();

    if(expression == null) {
      return str.toString();
    }

    for(Effect effect : expression.toEffect().arguments)
      str.append(fluent(effect.fluent, effect.value));
    return str.toString();
  }

  public String standardLocation(String arg0, String value, String prefix){
    if(places.containsKey(value))
      return arg0 + " is at " + value;
    else if(containers.containsKey(value))
      return arg0 + " is in " + value;
    else if(value.equals("?")) {
      if(!prefix.isEmpty()) {
        return "where " + arg0 + " is";
      }

      return "Location of " + arg0 + " is unknown";
    }
    return value + " has " + arg0;
  }

  public String clean(String str) {
    for(String agent : agents.keySet())
      str = str.replaceAll(agent, agents.get(agent));
    for(String place : places.keySet())
      str = str.replaceAll(place, places.get(place));
    for(String other : others.keySet())
      str = str.replaceAll(other, others.get(other));
    return str.replaceAll(" The ", " the ");
  }

  @Override
  public String toString() {
    return initialState()
            + characterGoals()
            + actionTypes()
            + goal();
  }
}
