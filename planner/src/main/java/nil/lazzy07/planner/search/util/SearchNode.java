/*
* File name: SearchNode.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-02 23:48:40
// Date modified: 2026-02-06 15:08:11
* ------
*/

package nil.lazzy07.planner.search.util;

import java.util.List;

import nil.lazzy07.llm.prompt.SearchPrompt;

public class SearchNode {
  private static ProgressionTreeMap treeMap;

  private final long nodeId;
  private SearchNode parentNode;
  private List<SearchNode> childNodes;

  private float confidence;
  private String explaination;

  public SearchNode(long nodeId) {
    this.nodeId = nodeId;
  }

  public static void SetProgressionTreeMap(ProgressionTreeMap treeMap) {
    SearchNode.treeMap = treeMap;
  }

  public static ProgressionTreeMap getTreeMap() {
    return treeMap;
  }

  public long getNodeId() {
    return nodeId;
  }

  public float getConfidence() {
    return this.confidence;
  }

  // public SearchPrompt getPrompt() {
  // return prompt;
  // }

  public SearchNode getParentNode() {
    return parentNode;
  }

  public List<SearchNode> getChildNodes() {
    return childNodes;
  }

  public String getExplaination() {
    return explaination;
  }
}
