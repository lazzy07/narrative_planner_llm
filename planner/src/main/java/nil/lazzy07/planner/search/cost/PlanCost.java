/*
* File name: PlanCost.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-20 18:02:16
// Date modified: 2026-02-20 18:11:39
* ------
*/

package nil.lazzy07.planner.search.cost;

import nil.lazzy07.common.search.GenericSearchNode;

public class PlanCost extends CostType {
  @Override
  public float calculateCost(GenericSearchNode node) {
    return nodeCostRecursive(node, 0.0f);
  }

  private float nodeCostRecursive(GenericSearchNode node, float currentCost) {
    if (node == null) {
      return currentCost;
    } else {
      return nodeCostRecursive(node.getParentNode(), currentCost + (1.0f - node.getConfidence()));
    }
  }
}
