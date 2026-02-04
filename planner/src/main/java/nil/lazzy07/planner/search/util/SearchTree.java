package nil.lazzy07.planner.search.util;

import nil.lazzy07.planner.search.type.SearchType;

public class SearchTree {
  private final ProgressionTreeMap treeMap;
  private SearchNode head;
  private long noOfVisitedNodes;
  private SearchType searchType;

  public SearchTree(ProgressionTreeMap treeMap, SearchType searchType) {
    this.treeMap = treeMap;
    this.searchType = searchType;
  }

  public void initSearchTree(long initialNodeId) {
    SearchNode newNode = new SearchNode(initialNodeId);

    this.head = newNode;

    this.searchType.addNode(newNode);
  }

  public ProgressionTreeMap getTreeMap() {
    return treeMap;
  }

  public SearchNode getHead() {
    return head;
  }

  public long getNoOfVisitedNodes() {
    return noOfVisitedNodes;
  }

  public SearchType getSearchType() {
    return searchType;
  }
}
