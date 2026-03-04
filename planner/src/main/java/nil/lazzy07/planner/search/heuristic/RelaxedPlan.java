/*
* File name: RelaxedPlan.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-03 22:56:52
// Date modified: 2026-03-04 01:52:46
* ------
*/
package nil.lazzy07.planner.search.heuristic;

import edu.uky.cs.nil.sabre.comp.CompiledProblem;
import edu.uky.cs.nil.sabre.logic.Value;
import edu.uky.cs.nil.sabre.prog.ProgressionCost;
import edu.uky.cs.nil.sabre.prog.ProgressionCostFactory;
import edu.uky.cs.nil.sabre.prog.ProgressionNode;
import edu.uky.cs.nil.sabre.prog.RelaxedPlanHeuristic;
import edu.uky.cs.nil.sabre.prog.RepeatedRootHeuristic;
import edu.uky.cs.nil.sabre.ptree.ProgressionTree;
import edu.uky.cs.nil.sabre.ptree.ProgressionTreeSpace;
import edu.uky.cs.nil.sabre.util.Worker.Status;
import nil.lazzy07.common.search.GenericSearchNode;
import nil.lazzy07.planner.search.util.ProgressionTreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uky.cs.nil.sabre.Character;
import edu.uky.cs.nil.sabre.Fluent;

public class RelaxedPlan extends HeuristicType {
  private static final Logger log = LoggerFactory.getLogger(RelaxedPlan.class);

  private final ProgressionCostFactory factory;
  private final CompiledProblem problem;
  private final ProgressionTreeMap treeMap;

  private final ProgressionTreeSpace treeSpace;

  // built once (for this problem)
  private final ProgressionCost heuristic;

  public RelaxedPlan(ProgressionTreeMap map) {
    this.problem = map.problem;
    this.treeMap = map;

    // Sabre’s intended composition:
    // relaxed plan heuristic, but prevent repeating the same root state
    this.factory = new RepeatedRootHeuristic.Factory(RelaxedPlanHeuristic.FACTORY);

    // Build the actual heuristic cost function for this compiled problem
    this.heuristic = factory.getCost(problem, new Status());

    this.treeSpace = new ProgressionTreeSpace(this.treeMap.tree);
  }

  public double evaluate(GenericSearchNode node) {
    long nodeId = node.getNodeId();

    // whatever Sabre thinks the “current character” is at this ptree node:
    Character character = this.treeMap.tree.getCharacter(nodeId);

    ProgressionNode<Long> pn = new PTreeProgressionNode(
        problem, this.treeSpace, this.treeMap.tree, nodeId, character);

    double value = heuristic.evaluate(pn);
    log.trace("For node-id: {} heuristic value: {}", nodeId, value);
    return value;
  }

  private static final class PTreeProgressionNode implements ProgressionNode<Long> {
    private final CompiledProblem problem;
    private final ProgressionTreeSpace space;
    private final ProgressionTree tree;
    private final long nodeId;
    private final Character character;

    private PTreeProgressionNode(
        CompiledProblem problem,
        ProgressionTreeSpace space,
        ProgressionTree tree,
        long nodeId,
        Character character) {
      this.problem = problem;
      this.space = space;
      this.tree = tree;
      this.nodeId = nodeId;
      this.character = character;
    }

    @Override
    public CompiledProblem getProblem() {
      return problem;
    }

    @Override
    public edu.uky.cs.nil.sabre.prog.ProgressionSearch getSearch() {
      // You’re not using Sabre’s ProgressionSearch here.
      throw new UnsupportedOperationException("No ProgressionSearch context in custom planner.");
    }

    @Override
    public edu.uky.cs.nil.sabre.prog.ProgressionSpace<Long> getSpace() {
      return space;
    }

    @Override
    public Character getCharacter() {
      return character;
    }

    @Override
    public Long getTrunk() {
      return null;
    }

    @Override
    public Long getRoot() {
      // best-effort: treat this node as its own root in our custom wrapper
      return nodeId;
    }

    @Override
    public Long getNode() {
      return nodeId;
    }

    @Override
    public int getTemporalOffset() {
      return 0;
    }

    @Override
    public int getTemporalDepth() {
      // use 1 to avoid RepeatedRootHeuristic blocking you unexpectedly
      return 1;
    }

    @Override
    public int getEpistemicDepth() {
      return 0;
    }

    @Override
    public Value getValue(Fluent fluent) {
      return tree.getValue(nodeId, fluent);
    }
  }
}
