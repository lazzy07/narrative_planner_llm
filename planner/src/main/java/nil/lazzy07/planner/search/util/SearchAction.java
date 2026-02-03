package nil.lazzy07.planner.search.util;

public class SearchAction {
  private final float cost;
  private final SearchNode childNode;
  private final SearchNode parentNode;

  public SearchAction(SearchNode parentNode, SearchNode childNode, float cost){
    this.cost = cost;
    this.childNode = childNode;
    this.parentNode = parentNode;
  }

  public float getCost() {
    return cost;
  }

  public SearchNode getChildNode() {
    return childNode;
  }

  public SearchNode getParentNode() {
    return parentNode;
  }
}
