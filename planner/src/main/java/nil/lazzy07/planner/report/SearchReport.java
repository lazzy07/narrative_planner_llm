/*
* File name: SearchResults.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-20 09:53:17
// Date modified: 2026-03-06 19:06:45
* ------
*/
package nil.lazzy07.planner.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import edu.uky.cs.nil.sabre.Action;
import nil.lazzy07.common.datetime.DateTimeGenerator;
import nil.lazzy07.common.search.GenericSearchNode;
import nil.lazzy07.planner.config.ConfigFile;
import nil.lazzy07.planner.search.result.SearchResult;

public class SearchReport {
  private ConfigFile configs;

  private long nodesVisited;
  private long nodesExpanded;

  private SearchResult searchReport;
  private boolean planFound = false;

  public SearchReport(ConfigFile configs, long nodesVisited, long nodesExpanded, SearchResult searchResult) {
    this.configs = configs;
    this.nodesVisited = nodesVisited;
    this.nodesExpanded = nodesExpanded;
    this.searchReport = searchResult;

    if (this.searchReport.isSolutionFound()) {
      planFound = true;
    }
  }

  @Override
  public String toString() {
    return "SearchResults [nodesVisited=" + nodesVisited + ", nodesExpanded=" + nodesExpanded + ", planFound="
        + this.searchReport.getFinalNode().getCurrentPlan() + "]";
  }

  private List<String> planToStr() {
    List<String> planStr = new ArrayList<>();

    for (Action action : this.searchReport.getFinalNode().getCurrentPlan()) {
      planStr.add(action.toString());
    }

    return planStr;
  }

  private List<String> generateExplainations() {
    List<String> explainations = new ArrayList<>();

    if (this.searchReport.getFinalNode() == null) {
      return null;
    }

    GenericSearchNode currentNode = this.searchReport.getFinalNode();

    while (currentNode != null) {
      String explaination = currentNode.getExplaination();

      if (explaination != null) {
        explainations.add(explaination);
      }

      currentNode = currentNode.getParentNode();
    }

    Collections.reverse(explainations);
    return explainations;
  }

  public String toJsonString() {
    ObjectMapper objMapper = new ObjectMapper();
    ObjectNode node = objMapper.createObjectNode();

    node.put("domain", configs.domain().name());

    node.put("nodesVisited", this.nodesVisited);
    node.put("nodesExpanded", this.nodesExpanded);

    node.put("maxPlanLength", configs.search().plan().maxLength());
    node.put("maxNodes", configs.search().plan().maxNodes());
    node.put("utility", configs.search().plan().utility());

    node.put("search", configs.search().type().name());
    node.put("cost", configs.search().cost().type());

    node.put("heuristic", configs.search().heuristic().type());

    node.put("llm", configs.llm().model().name());
    node.put("useCache", configs.llm().cache().enabled());

    node.put("promptVersion", configs.llm().prompt().version());

    node.put("planFound", this.planFound);
    node.put("saveNodeResults", this.configs.output().save());
    node.put("nodeResultDirectory", this.configs.output().directory() + this.configs.domain().name() + "/"
        + DateTimeGenerator.GetTimeStamp() + "/" + this.configs.llm().model().name() + "/");

    node.putPOJO("plan", planToStr());
    node.putPOJO("explainations", generateExplainations());

    try {
      return objMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Cannot generate the final report: JSON parse error");
    }
  }
}
