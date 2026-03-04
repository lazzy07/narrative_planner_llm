/*
* File name: HeuristicType.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-03 22:44:03
// Date modified: 2026-03-03 22:46:58
* ------
*/

package nil.lazzy07.planner.search.heuristic;

import nil.lazzy07.common.search.GenericSearchNode;

public abstract class HeuristicType {
  public abstract double evaluate(GenericSearchNode node);
}
