/*
* File name: SearchNode.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-02 23:48:40
// Date modified: 2026-02-20 14:45:23
* ------
*/

package nil.lazzy07.planner.search.util;

import java.util.ArrayList;
import java.util.List;

import edu.uky.cs.nil.sabre.Action;
import edu.uky.cs.nil.sabre.Plan;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import edu.uky.cs.nil.sabre.logic.Assignment;
import nil.lazzy07.common.search.GenericSearchNode;
import nil.lazzy07.llm.prompt.SearchPrompt;

public class SearchNode implements GenericSearchNode {
  private static ProgressionTreeMap treeMap;
  private final long nodeId;
  private GenericSearchNode parentNode;
  private List<GenericSearchNode> childNodes;
  private String prompt;
  private ArrayList<CompiledAction> availableActions;

  private float confidence;
  private String explaination;

  public static void SetProgressionTreeMap(ProgressionTreeMap treeMap) {
    SearchNode.treeMap = treeMap;
  }

  public SearchNode(long nodeId) {
    this.childNodes = new ArrayList<>();
    this.nodeId = nodeId;
    this.availableActions = SearchNode.treeMap.getAvailableActions(this.nodeId);
    this.generatePrompt();
  }

  private void generatePrompt() {
    Plan<Action> currentPlan = SearchNode.treeMap.getPlan(this.nodeId);
    List<Assignment> currentState = SearchNode.treeMap.getState(this.nodeId);

    this.prompt = SearchPrompt.GetPrompt(currentPlan, this.availableActions, currentState);
  }

  public void setParentNode(GenericSearchNode node) {
    this.parentNode = node;
  }

  public void setConfidence(float confidence) {
    this.confidence = confidence;
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

  public static ProgressionTreeMap getTreeMap() {
    return treeMap;
  }

  public ArrayList<CompiledAction> getAvailableActions() {
    return availableActions;
  }
}
