/*
* File name: PlanLengthCost.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-04 01:54:47
// Date modified: 2026-03-04 01:59:58
* ------
*/

package nil.lazzy07.planner.search.cost;

import nil.lazzy07.common.search.GenericSearchNode;
import nil.lazzy07.planner.search.util.SearchNode;

public class PlanLengthCost extends CostType {

  @Override
  public float calculateCost(GenericSearchNode node) {
    return SearchNode.getTreeMap().getPlan(node.getNodeId()).size();
  }

}
