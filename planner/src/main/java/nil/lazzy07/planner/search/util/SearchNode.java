/*
* File name: SearchNode.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-02 23:48:40
// Date modified: 2026-03-06 17:11:31
* ------
*/

package nil.lazzy07.planner.search.util;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
  private String llmResponse;

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

  public void setLLMResponse(String llmResponse) {
    this.llmResponse = llmResponse;
  }

  public String getLLMResponse() {
    return this.llmResponse;
  }

  public Plan<Action> getCurrentPlan() {
    return SearchNode.treeMap.getPlan(this.nodeId);
  }

  public void setExplaination(String explaination) {
    this.explaination = explaination;
  }

  private List<String> availableActionsToStr() {
    List<String> planStr = new ArrayList<>();

    for (Action action : this.availableActions) {
      planStr.add(action.toString());
    }

    return planStr;
  }

  public String toJsonString() {
    ObjectMapper objMapper = new ObjectMapper();
    ObjectNode node = objMapper.createObjectNode();

    node.put("nodeId", this.nodeId);
    node.put("prompt", this.prompt);
    node.put("explaination", this.explaination);
    node.putPOJO("availableActions", availableActionsToStr());

    if (this.llmResponse != null) {
      node.put("llmResponse", this.llmResponse);
    }

    try {
      return objMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Cannot generate the final report: JSON parse error");
    }
  }
}
