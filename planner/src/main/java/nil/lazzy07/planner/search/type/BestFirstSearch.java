/*
* File name: BestFirstSearch.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-03 19:39:18
// Date modified: 2026-02-17 21:25:06
* ------
*/

package nil.lazzy07.planner.search.type;

import java.util.PriorityQueue;

import nil.lazzy07.planner.search.cost.CostType;
import nil.lazzy07.planner.search.util.SearchNode;

public class BestFirstSearch extends SearchType {
  private PriorityQueue<SearchNode> queue;

  public BestFirstSearch(CostType cost) {
    super(cost);

    this.queue = new PriorityQueue<>(
        (n1, n2) -> Float.compare(
            this.getCost().calculateCost(n1),
            this.getCost().calculateCost(n2)));
  }

  public void removeNode(SearchNode node) {
    queue.remove(node);
  }

  @Override
  public void addNode(SearchNode node) {
    this.queue.add(node);
  }

  @Override
  public SearchNode getNextNode() {
    return this.queue.poll();
  }

  @Override
  public boolean isEmpty() {
    return queue.isEmpty();
  }

  @Override
  public SearchNode peekNextNode() {
    return this.queue.peek();
  }
}
