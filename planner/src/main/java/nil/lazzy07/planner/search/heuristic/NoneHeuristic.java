/*
* File name: NoneHeuristic.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-03 22:43:36
// Date modified: 2026-03-03 22:48:20
* ------
*/

package nil.lazzy07.planner.search.heuristic;

import nil.lazzy07.common.search.GenericSearchNode;

public class NoneHeuristic extends HeuristicType {
  public double evaluate(GenericSearchNode node) {
    return 0.0;
  }
}
