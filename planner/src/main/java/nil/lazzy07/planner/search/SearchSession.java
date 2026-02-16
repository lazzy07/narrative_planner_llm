/*
* File name: Search.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-02 22:16:07
// Date modified: 2026-02-16 14:56:27
* ------
*/

package nil.lazzy07.planner.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  }

  public void initSearch() {
    this.searchTree = new SearchTree(this.treeMap, this.searchType);
    this.searchTree.initSearchTree(0);
  }

  public void startSearch() {
    log.info("Search started with LLMApi: {}", this.llmApi.getType());
    while (!this.searchType.isEmpty()) {
      // Get the next node
      SearchNode currentNode = this.searchType.getNextNode();
      log.info("Current prompt: {}", currentNode.getPrompt());

      // Check if the utility achieved
      if (this.treeMap.getUtility(currentNode.getNodeId()) >= this.planConfigs.utility()) {
        log.info("Planner achieved the utility: \n{}", this.treeMap.getPlan(currentNode.getNodeId()));
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
