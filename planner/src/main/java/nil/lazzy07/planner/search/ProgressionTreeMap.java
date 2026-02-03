/*
* File name: ProgressionTreeMap.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-02 22:51:18
// Date modified: 2026-02-02 23:06:24
* ------
*/

package nil.lazzy07.planner.search;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import edu.uky.cs.nil.sabre.*;
import edu.uky.cs.nil.sabre.Character;
import edu.uky.cs.nil.sabre.Fluent;
import edu.uky.cs.nil.sabre.HeadPlan;
import edu.uky.cs.nil.sabre.Plan;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import edu.uky.cs.nil.sabre.comp.CompiledProblem;
import edu.uky.cs.nil.sabre.logic.Assignment;
import edu.uky.cs.nil.sabre.logic.Comparison;
import edu.uky.cs.nil.sabre.logic.False;
import edu.uky.cs.nil.sabre.logic.Value;
import edu.uky.cs.nil.sabre.ptree.ProgressionTree;
import edu.uky.cs.nil.sabre.Action;

public class ProgressionTreeMap {
  public final ProgressionTree tree;
  public final CompiledProblem problem;
  private final TreeMap<Long, Integer> distance = new TreeMap<>();

  public ProgressionTreeMap(ProgressionTree tree, CompiledProblem problem) {
    this.problem = problem;
    this.tree = tree;

    findExplained(0);

    for (long node : distance.keySet()) {
      if (isSolution(node)) {
        if (tree.getEvent(node) instanceof Action)
          setDistance(tree.getBefore(node), 1);
        else
          setDistance(tree.getBefore(node), 0);
      }

    }
    for (long node : distance.keySet()) {
      if (isSolution(node) && distance.get(node) == Integer.MAX_VALUE)
        distance.put(node, 0);
    }
  }

  private final void findExplained(long node) {
    if (distance.put(node, Integer.MAX_VALUE) == null) {
      long child = tree.getLastChild(node);
      while (child != -1) {
        if (isExplained(child)) {
          findExplained(tree.getAfterTriggers(child));
          CompiledAction action = tree.getAction(child);
          for (Character consenting : action.consenting)
            findExplained(tree.getBranch(child, consenting));
        }
        child = tree.getPreviousSibling(child);
      }
    }
  }

  private final void setDistance(long node, int distance) {
    if (this.distance.containsKey(node))
      this.distance.put(node, Math.min(this.distance.get(node), distance));
    if (tree.getBefore(node) != node)
      setDistance(tree.getBefore(node), distance + (tree.getEvent(node) instanceof Action ? 1 : 0));
  }

  private final boolean isExplained(long node) {
    return tree.isExplained(node) && tree.getExplanation(node, tree.getCharacter(node)) != -1;
  }

  public List<Assignment> getState(long node) {
    List<Assignment> state = new ArrayList<>(tree.problem.fluents.size());
    for (Fluent fluent : tree.problem.fluents)
      state.add(new Assignment(fluent, tree.getValue(node, fluent)));
    return state;
  }

  public Plan<Action> getPlan(long node) {
    HeadPlan<Action> plan = HeadPlan.EMPTY;
    while (tree.getBefore(node) != node) {
      Event event = tree.getEvent(node);
      if (event instanceof Action)
        plan = plan.prepend((Action) event);
      node = tree.getBefore(node);
    }

    return plan;
  }

  public Integer getDistance(long node) {
    return distance.get(node);
  }

  public ArrayList<CompiledAction> getAvailableActions(long node) {
    State state = fluent -> tree.getValue(node, fluent);
    ArrayList<CompiledAction> actions = new ArrayList<>();

    problem.actions.forEvery(state, compiledAction -> {
      long nodeAfter = tree.getAfter(node, compiledAction);
      CompiledAction action = tree.getAction(nodeAfter);
      actions.add(action);
    });

    return actions;
  }

  public long getNextNode(long node, CompiledAction action) {
    return tree.getAfter(node, action);
  }

  public double getUtility(long node) {
    Value utility = tree.getUtility(node);
    if (utility instanceof edu.uky.cs.nil.sabre.Number)
      return ((edu.uky.cs.nil.sabre.Number) utility).value;
    else
      return Double.NaN;
  }

  public boolean isSolution(long node) {
    return distance.containsKey(node) && isSolution(node, false, node);
  }

  private final boolean isSolution(long before, boolean action, long after) {
    if (distance.containsKey(before) && action)
      return Comparison.LESS_THAN.test(tree.getUtility(before), tree.getUtility(after));
    else if (tree.getBefore(before) == before)
      return false;
    else {
      Event event = tree.getEvent(before);
      return isSolution(tree.getBefore(before),
          action || (event instanceof Action && !event.getPrecondition().equals(False.FALSE)), after);
    }
  }
}
