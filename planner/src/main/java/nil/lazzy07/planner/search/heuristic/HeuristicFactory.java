/*
* File name: HeuristicFactory.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-03 22:49:34
// Date modified: 2026-03-04 01:17:19
* ------
*/

package nil.lazzy07.planner.search.heuristic;

import nil.lazzy07.planner.search.util.ProgressionTreeMap;

public class HeuristicFactory {
  public static HeuristicType CreateHeuristic(String heuristicType, ProgressionTreeMap treeMap) {
    switch (heuristicType) {
      case "none":
        return new NoneHeuristic();
      case "rp":
        return new RelaxedPlan(treeMap);
      default:
        return new NoneHeuristic();
    }
  }
}
