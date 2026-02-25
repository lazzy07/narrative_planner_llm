/*
* File name: Cost.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-02 23:39:55
// Date modified: 2026-02-20 18:07:32
* ------
*/
package nil.lazzy07.planner.search.cost;

import nil.lazzy07.common.search.GenericSearchNode;

public abstract class CostType {
  public abstract float calculateCost(GenericSearchNode node);
}
