package nil.lazzy07.planner.search.util;

public class SearchTree {
  private long visited = 0;
  private long expanded = 0;

  private void incrementVisited(){
    this.visited += 1;
  }

  private void incrementExpanded(){
    this.expanded += 1;
  }
}
