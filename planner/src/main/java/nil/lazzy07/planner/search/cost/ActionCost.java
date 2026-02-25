package nil.lazzy07.planner.search.cost;

import nil.lazzy07.common.search.GenericSearchNode;

public class ActionCost extends CostType {
  @Override
  public float calculateCost(GenericSearchNode node) {
    return 5.0f - node.getConfidence();
  }
}
