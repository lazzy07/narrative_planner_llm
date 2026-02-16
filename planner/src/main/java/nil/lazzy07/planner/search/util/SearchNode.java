/*
* File name: SearchNode.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-02 23:48:40
// Date modified: 2026-02-16 14:43:57
* ------
*/

package nil.lazzy07.planner.search.util;

import java.util.ArrayList;
import java.util.List;

import nil.lazzy07.common.search.GenericSearchNode;
import nil.lazzy07.llm.prompt.SearchPrompt;

public class SearchNode implements GenericSearchNode {
  private final long nodeId;
  private GenericSearchNode parentNode;
  private List<GenericSearchNode> childNodes;
  private String prompt;

  private float confidence;
  private String explaination;

  public SearchNode(long nodeId) {
    this.childNodes = new ArrayList<>();
    this.nodeId = nodeId;
    this.prompt = SearchPrompt.GetPrompt();
  }

  public long getNodeId() {
    return nodeId;
  }

  public float getConfidence() {
    return this.confidence;
  }

  public String getPrompt() {
    return prompt;
  }

  public GenericSearchNode getParentNode() {
    return parentNode;
  }

  public List<GenericSearchNode> getChildNodes() {
    return childNodes;
  }

  public String getExplaination() {
    return explaination;
  }

  public void addChildNode(GenericSearchNode node) {
    this.childNodes.add(node);
  }
}
