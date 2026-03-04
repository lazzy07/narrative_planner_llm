/*
* File name: SearchTypeFactory.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-04 01:36:55
// Date modified: 2026-03-04 01:21:10
* ------
*/

package nil.lazzy07.planner.search.type;

import nil.lazzy07.planner.search.cost.CostType;
import nil.lazzy07.planner.search.heuristic.HeuristicType;

public class SearchTypeFactory {
  public static SearchType CreateSearchType(String name, CostType costType, HeuristicType heuristicType) {
    switch (name) {
      case "best-first":
        return new BestFirstSearch(costType, heuristicType);
      default:
        return null;
    }
  }
}
