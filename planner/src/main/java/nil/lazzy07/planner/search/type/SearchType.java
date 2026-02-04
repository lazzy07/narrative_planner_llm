/*
* File name: SearchType.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-03 19:05:17
// Date modified: 2026-02-03 21:06:06
* ------
*/

package nil.lazzy07.planner.search.type;

import nil.lazzy07.planner.search.cost.CostType;
import nil.lazzy07.planner.search.util.SearchNode;

public abstract class SearchType {
  private CostType cost;

  public SearchType(CostType cost) {
    this.cost = cost;
  }

  CostType getCost() {
    return this.cost;
  }

  public abstract SearchNode peekNextNode();

  public abstract SearchNode getNextNode();

  public abstract void addNode(SearchNode node);

  public abstract boolean isEmpty();
}
