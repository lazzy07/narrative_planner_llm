/*
* File name: ConfigFile.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-02 20:56:55
// Date modified: 2026-03-03 22:49:02
* ------
*/

package nil.lazzy07.planner.config;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigFile(Domain domain, Search search, LLM llm, Output output) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Domain(String name, String file) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Search(Type type, Cost cost, Heuristic heuristic, Plan plan) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Type(String name, Map<String, Object> config) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cost(String type, Map<String, Object> config) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Heuristic(String type, Map<String, Object> config) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Plan(@JsonProperty("max-length") int maxLength, int utility,
        @JsonProperty("max-nodes") int maxNodes) {
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record LLM(Prompt prompt, Cache cache, Model model) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Prompt(String version, String directory, boolean explanation) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cache(boolean enabled, String directory) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Model(String name, Map<String, Object> config) {
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Output(boolean save, String directory) {
  }
}
