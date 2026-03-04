/*
* File name: BestFirstSearch.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-03 19:39:18
// Date modified: 2026-03-04 01:23:51
* ------
*/

package nil.lazzy07.planner.search.type;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

import nil.lazzy07.planner.search.cost.CostType;
import nil.lazzy07.planner.search.heuristic.HeuristicType;
import nil.lazzy07.planner.search.util.SearchNode;

public class BestFirstSearch extends SearchType {
  private final PriorityQueue<SearchNode> queue;

  // cache f(n) so comparator doesn't constantly recompute
  private final Map<Long, Float> fScore = new HashMap<>();
  private final Map<Long, Float> gScore = new HashMap<>();
  private final Map<Long, Float> hScore = new HashMap<>();

  public BestFirstSearch(CostType cost, HeuristicType heuristic) {
    super(cost, heuristic);

    this.queue = new PriorityQueue<>((n1, n2) -> {
      float f1 = fScore.getOrDefault(n1.getNodeId(), computeAndCache(n1));
      float f2 = fScore.getOrDefault(n2.getNodeId(), computeAndCache(n2));

      int cmp = Float.compare(f1, f2);
      if (cmp != 0)
        return cmp;

      // tie-break: prefer smaller h, then smaller g (optional but useful)
      float h1 = hScore.getOrDefault(n1.getNodeId(), 0f);
      float h2 = hScore.getOrDefault(n2.getNodeId(), 0f);
      cmp = Float.compare(h1, h2);
      if (cmp != 0)
        return cmp;

      float g1 = gScore.getOrDefault(n1.getNodeId(), 0f);
      float g2 = gScore.getOrDefault(n2.getNodeId(), 0f);
      return Float.compare(g1, g2);
    });
  }

  private float computeAndCache(SearchNode n) {
    float g = this.getCost().calculateCost(n);
    float h = (this.getHeuristic() == null) ? 0.0f : (float) this.getHeuristic().evaluate(n);
    float f = g + h;

    long id = n.getNodeId();
    gScore.put(id, g);
    hScore.put(id, h);
    fScore.put(id, f);

    return f;
  }

  public void removeNode(SearchNode node) {
    queue.remove(node);
    long id = node.getNodeId();
    fScore.remove(id);
    gScore.remove(id);
    hScore.remove(id);
  }

  @Override
  public void addNode(SearchNode node) {
    computeAndCache(node);
    this.queue.add(node);
  }

  @Override
  public SearchNode getNextNode() {
    SearchNode n = this.queue.poll();
    if (n != null) {
      long id = n.getNodeId();
      fScore.remove(id);
      gScore.remove(id);
      hScore.remove(id);
    }
    return n;
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
