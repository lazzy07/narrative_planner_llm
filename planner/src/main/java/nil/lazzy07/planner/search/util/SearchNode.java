/*
* File name: SearchNode.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-02 23:48:40
// Date modified: 2026-02-03 00:18:23
* ------
*/

package nil.lazzy07.planner.search.util;

import java.util.List;

import nil.lazzy07.llm.prompt.Prompt;
import nil.lazzy07.planner.search.ProgressionTreeMap;

public class SearchNode {
  private static ProgressionTreeMap treeMap;

  private final long nodeId;
  private final Prompt prompt;

  private boolean isExplained;
  private List<SearchNode> explainedChildNodes;

  public SearchNode(long nodeId, Prompt prompt, boolean isExplained) {
    this.nodeId = nodeId;
    this.prompt = prompt;
    this.isExplained = isExplained;
  }

  public SearchNode(long nodeId, Prompt prompt) {
    this.nodeId = nodeId;
    this.prompt = prompt;
  }

  public static void SetProgressionTreeMap(ProgressionTreeMap treeMap) {
    SearchNode.treeMap = treeMap;
  }
}
