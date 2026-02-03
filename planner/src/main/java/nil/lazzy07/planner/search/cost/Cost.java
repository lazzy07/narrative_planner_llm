/*
* File name: Cost.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-02 23:39:55
// Date modified: 2026-02-02 23:40:24
* ------
*/
package nil.lazzy07.planner.search.cost;

import nil.lazzy07.planner.search.util.SearchNode;

public abstract class Cost {
  public abstract long calculateCost(SearchNode node);
}
