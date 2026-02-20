/*
* File name: SearchResults.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-20 09:53:17
// Date modified: 2026-02-20 11:35:59
* ------
*/
package nil.lazzy07.planner.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import edu.uky.cs.nil.sabre.Action;
import edu.uky.cs.nil.sabre.Plan;
import nil.lazzy07.planner.config.ConfigFile;

public class SearchResults {
  private ConfigFile configs;

  private long nodesVisited;
  private long nodesExpanded;

  private Plan<Action> plan;
  private boolean planFound = true;

  public SearchResults(ConfigFile configs, long nodesVisited, long nodesExpanded, Plan<Action> plan) {
    this.configs = configs;
    this.nodesVisited = nodesVisited;
    this.nodesExpanded = nodesExpanded;
    this.plan = plan;

    if (plan == null) {
      planFound = false;
    }
  }

  @Override
  public String toString() {
    return "SearchResults [nodesVisited=" + nodesVisited + ", nodesExpanded=" + nodesExpanded + ", planFound="
        + plan + "]";
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

    node.put("llm", configs.llm().model().name());
    node.put("useCache", configs.llm().cache().enabled());

    node.put("promptVersion", configs.llm().prompt().version());

    node.put("planFound", this.planFound);

    try {
      return objMapper.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Cannot generate the final report: JSON parse error");
    }
  }
}
