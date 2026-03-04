/*
* File name: SearchType.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-03 19:05:17
// Date modified: 2026-03-04 01:23:21
* ------
*/

package nil.lazzy07.planner.search.type;

import nil.lazzy07.planner.search.cost.CostType;
import nil.lazzy07.planner.search.heuristic.HeuristicType;
import nil.lazzy07.planner.search.util.SearchNode;

public abstract class SearchType {
  private CostType cost;
  private HeuristicType heuristic;

  public SearchType(CostType cost, HeuristicType heuristic) {
    this.cost = cost;
    this.heuristic = heuristic;
  }

  CostType getCost() {
    return this.cost;
  }

  HeuristicType getHeuristic() {
    return this.heuristic;
  }

  public abstract SearchNode peekNextNode();

  public abstract SearchNode getNextNode();

  public abstract void removeNode(SearchNode node);

  public abstract void addNode(SearchNode node);

  public abstract boolean isEmpty();
}
