/*
* File name: Search.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-02 22:16:07
// Date modified: 2026-02-17 19:12:51
* ------
*/

package nil.lazzy07.planner.search;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uky.cs.nil.sabre.comp.CompiledAction;
import nil.lazzy07.common.llm.ActionEvaluation;
import nil.lazzy07.common.llm.ActionEvaluationParser;
import nil.lazzy07.llm.model.LLMApi;
import nil.lazzy07.llm.prompt.SearchPrompt;
import nil.lazzy07.planner.config.ConfigFile.Search.Plan;
import nil.lazzy07.planner.search.type.SearchType;
import nil.lazzy07.planner.search.util.ProgressionTreeMap;
import nil.lazzy07.planner.search.util.SearchNode;
import nil.lazzy07.planner.search.util.SearchTree;

public class SearchSession {
  private static final Logger log = LoggerFactory.getLogger(SearchSession.class);

  private ProgressionTreeMap treeMap;
  private SearchTree searchTree;
  private SearchType searchType;
  private long noOfVisitedNodes = 0;
  private long noOfGeneratedNodes = 1;
  private Plan planConfigs;
  private LLMApi llmApi;

  public SearchSession(Plan planConfigs, ProgressionTreeMap treeMap, SearchType searchType, LLMApi api) {
    this.treeMap = treeMap;
    this.searchType = searchType;
    this.planConfigs = planConfigs;
    this.llmApi = api;

    SearchNode.SetProgressionTreeMap(treeMap);
  }

  public void initSearch() {
    this.searchTree = new SearchTree(this.treeMap, this.searchType);
    this.searchTree.initSearchTree(0);
  }

  private List<ActionEvaluation> getSelectedActions(List<ActionEvaluation> evaluations) {
    ArrayList<ActionEvaluation> selectedActions = new ArrayList<>();

    for (ActionEvaluation eval : evaluations) {
      if (eval.isExplained()) {
        selectedActions.add(eval);
      }
    }

    return selectedActions;
  }

  private void expandSearch(SearchNode currentNode, List<ActionEvaluation> selectedEvaluations) {
    for (ActionEvaluation selected : selectedEvaluations) {
      ArrayList<CompiledAction> availableActions = this.treeMap.getAvailableActions(currentNode.getNodeId());
      CompiledAction currentAction = availableActions.get(selected.actionId() - 1);

      if (currentAction == null) {
        throw new RuntimeException("An action that is not available has been selected by the LLM");
      }

      long nextNodeId = this.treeMap.getNextNode(currentNode.getNodeId(), currentAction);

      SearchNode newNode = new SearchNode(nextNodeId);
      newNode.setParentNode(newNode);

      currentNode.addChildNode(newNode);
      currentNode.setConfidence((float) selected.confidence());

      this.searchType.addNode(newNode);
      this.noOfGeneratedNodes++;
    }
  }

  public void startSearch() {
    log.info("Search started with LLMApi: {}", this.llmApi.getType());
    while (!this.searchType.isEmpty()) {
      // Get the next node
      SearchNode currentNode = this.searchType.getNextNode();
      String response = this.llmApi.query(SearchPrompt.GetSystemPrompt(), currentNode.getPrompt());

      List<ActionEvaluation> evaluations = ActionEvaluationParser.parseActionEvaluations(response);

      List<ActionEvaluation> selectedEvaluations = getSelectedActions(evaluations);

      this.expandSearch(currentNode, selectedEvaluations);

      log.info("Current plan: {}", this.treeMap.getPlan(currentNode.getNodeId()));

      log.info("Evaluation completed: NodeID: {} Selected actions: {} Visited: {}", currentNode.getNodeId(),
          selectedEvaluations.size(), this.noOfVisitedNodes);

      // Check if the utility achieved
      if (this.treeMap.getUtility(currentNode.getNodeId()) >= this.planConfigs.utility()) {
        log.info("Planner achieved the utility: \n{} \n Nodes visited: {} \n Nodes expanded: {}",
            this.treeMap.getPlan(currentNode.getNodeId()), this.noOfVisitedNodes, this.noOfGeneratedNodes);
        return;
      }

      if (noOfVisitedNodes >= this.planConfigs.maxNodes()) {
        log.info("Planner exhaused the search space, max # of nodes visited: {}", this.planConfigs.maxNodes());
        return;
      }

      if (this.treeMap.getPlan(currentNode.getNodeId()).size() >= this.planConfigs.maxLength()) {
        continue;
      }

      this.noOfVisitedNodes++;
    }

    log.warn("Planner finished without finding any solution. (Search queue is empty)");
  }

  public long getNoOfGeneratedNodes() {
    return noOfGeneratedNodes;
  }

  public ProgressionTreeMap getTreeMap() {
    return treeMap;
  }

  public SearchTree getSearchTree() {
    return searchTree;
  }

  public SearchType getSearchType() {
    return searchType;
  }

  public long getNoOfVisitedNodes() {
    return noOfVisitedNodes;
  }
}
