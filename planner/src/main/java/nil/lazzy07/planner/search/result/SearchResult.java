/*
* File name: SearchResult.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-06 15:19:45
// Date modified: 2026-03-06 15:21:28
* ------
*/

package nil.lazzy07.planner.search.result;

import nil.lazzy07.planner.search.util.SearchNode;

public class SearchResult {
  private SearchNode finalNode;
  private boolean solutionFound;

  public SearchResult(boolean solutionFound, SearchNode finalNode) {
    this.solutionFound = solutionFound;
    this.finalNode = finalNode;
  }

  public SearchNode getFinalNode() {
    return finalNode;
  }

  public boolean isSolutionFound() {
    return solutionFound;
  }
}
