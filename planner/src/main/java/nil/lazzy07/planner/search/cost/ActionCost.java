package nil.lazzy07.planner.search.cost;

import nil.lazzy07.planner.search.util.SearchNode;

public class ActionCost extends CostType {
  @Override
  public float calculateCost(SearchNode node) {
    return Float.MAX_VALUE - node.getConfidence();
  }
}
