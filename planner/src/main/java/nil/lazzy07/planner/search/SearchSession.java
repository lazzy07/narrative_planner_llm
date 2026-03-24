/*
* File name: Search.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-02 22:16:07
// Date modified: 2026-03-23 11:30:10
* ------
*/

package nil.lazzy07.planner.search;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uky.cs.nil.sabre.Action;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import nil.lazzy07.common.datetime.DateTimeGenerator;
import nil.lazzy07.common.llm.ActionEvaluation;
import nil.lazzy07.common.llm.ActionEvaluationParser;
import nil.lazzy07.common.llm.ActionEvaluationSelect;
import nil.lazzy07.llm.model.LLMApi;
import nil.lazzy07.llm.prompt.SearchPrompt;
import nil.lazzy07.planner.config.ConfigFile;
import nil.lazzy07.planner.report.JsonUtils;
import nil.lazzy07.planner.search.result.SearchResult;
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
  private ConfigFile planConfigs;
  private LLMApi llmApi;

  public SearchSession(ConfigFile planConfigs, ProgressionTreeMap treeMap, SearchType searchType, LLMApi api) {
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

  private void expandSearchSelect(SearchNode currentNode, List<ActionEvaluationSelect> selectedEvaluations) {
    int i = 0;

    ArrayList<CompiledAction> availableActions = this.treeMap.getAvailableActions(currentNode.getNodeId());

    for (ActionEvaluationSelect selected : selectedEvaluations) {
      int actionId = selected.actionId() - 1;

      try {
        CompiledAction currentAction = availableActions.get(actionId);
        long nextNodeId = this.treeMap.getNextNode(currentNode.getNodeId(), currentAction);

        SearchNode newNode = new SearchNode(nextNodeId);
        newNode.setParentNode(currentNode);

        currentNode.addChildNode(newNode);
        newNode.setConfidence(1.0f - ((float) i / selectedEvaluations.size()));
        newNode.setExplaination(selected.reason());
        this.searchType.addNode(newNode);
        this.noOfGeneratedNodes++;
        i++;
      } catch (IndexOutOfBoundsException e) {
        continue;
      }
    }
  }

  private void saveNodeData(SearchNode node) {
    if (this.planConfigs.output().save()) {
      Path directoryName = Path.of(this.planConfigs.output().directory(), this.planConfigs.domain().name(),
          DateTimeGenerator.GetTimeStamp(),
          this.planConfigs.llm().model().name());

      JsonUtils.saveToJson(node.getNodeId() + ".json", directoryName, node.toJsonString());
    }
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
      newNode.setParentNode(currentNode);

      currentNode.addChildNode(newNode);
      newNode.setConfidence((float) selected.confidence());

      this.searchType.addNode(newNode);
      this.noOfGeneratedNodes++;
    }
  }

  public SearchResult startSearch() {
    log.info("Search started with LLMApi: {}", this.llmApi.getType());

    SearchNode currentNode = null;

    while (!this.searchType.isEmpty()) {
      // Get the next node
      currentNode = this.searchType.getNextNode();
      long currentNodeId = currentNode.getNodeId();

      ArrayList<CompiledAction> availableActions = currentNode.getAvailableActions();

      if (availableActions.size() == 0) {
        log.debug("No available actions for node: {}", currentNodeId);
        continue;
      }

      // Check if the utility achieved
      if (this.treeMap.getUtility(currentNodeId) >= this.planConfigs.search().plan().utility()) {
        edu.uky.cs.nil.sabre.Plan<Action> plan = this.treeMap.getPlan(currentNodeId);
        log.info("Planner achieved the utility: \n{} \n Nodes visited: {} \n Nodes expanded: {}",
            plan, this.noOfVisitedNodes, this.noOfGeneratedNodes);
        return new SearchResult(true, currentNode);
      }

      if (noOfVisitedNodes >= this.planConfigs.search().plan().maxNodes()) {
        log.info("Planner exhaused the search space, max # of nodes visited: {}",
            this.planConfigs.search().plan().maxNodes());
        return new SearchResult(false, currentNode);
      }

      long planLength = this.treeMap.getPlan(currentNodeId).size();

      if (planLength >= this.planConfigs.search().plan().maxLength()) {
        log.info("Node removed since node {}'s length is larger than the maxLength {} node's plan length: {}",
            currentNodeId,
            this.planConfigs.search().plan().maxLength(), planLength);
        continue;
      }

      // Check if random response available through random generator (Available only
      // if planner is random).
      String response = this.llmApi.query(currentNode.getAvailableActions());

      if (response == null) {
        response = this.llmApi.query(SearchPrompt.GetSystemPrompt(), currentNode.getPrompt());
      }

      currentNode.setLLMResponse(response);
      List<ActionEvaluationSelect> selectedEvaluations = ActionEvaluationParser
          .parseActionEvaluationSelectsImproved(response);

      this.expandSearchSelect(currentNode, selectedEvaluations);

      log.info("Evaluation completed: NodeID: {} Selected actions: {} Visited: {}", currentNodeId,
          selectedEvaluations.size(), this.noOfVisitedNodes);
      saveNodeData(currentNode);
      this.noOfVisitedNodes++;
    }

    log.warn("Planner finished without finding any solution. (Search queue is empty)");
    return new SearchResult(false, currentNode);
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
