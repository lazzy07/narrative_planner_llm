/*
* File name: SearchTypeFactory.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-04 01:36:55
// Date modified: 2026-02-04 01:42:28
* ------
*/

package nil.lazzy07.planner.search.type;

import nil.lazzy07.planner.search.cost.CostType;

public class SearchTypeFactory {
  public static SearchType CreateSearchType(String name, CostType costType) {
    switch (name) {
      case "best-first":
        return new BestFirstSearch(costType);
      default:
        return null;
    }
  }
}
