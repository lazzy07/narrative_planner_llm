/*
* File name: ActionEvaluationParser.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-17 15:49:54
// Date modified: 2026-03-02 20:45:18
* ------
*/

package nil.lazzy07.common.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ActionEvaluationParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static List<ActionEvaluation> parseActionEvaluations(String json) {
    try {
      return MAPPER.readValue(
          json,
          new TypeReference<List<ActionEvaluation>>() {
          });
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse LLM JSON response: " + json, e);
    }
  }

  public static List<ActionEvaluationSelect> parseActionEvaluationSelects(String json) {
    try {
      return MAPPER.readValue(
          json,
          new TypeReference<List<ActionEvaluationSelect>>() {
          });
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse LLM JSON response: " + json, e);
    }
  }

  public static List<ActionEvaluationSelect> parseActionEvaluationSelectsImproved(String json) {
    try {
      // JSON: { "12": "reason", "44": "reason" }
      Map<String, String> map = MAPPER.readValue(json, new TypeReference<Map<String, String>>() {
      });

      List<ActionEvaluationSelect> out = new ArrayList<>(map.size());
      for (var e : map.entrySet()) {
        int actionId = Integer.parseInt(e.getKey()); // keys are digits
        out.add(new ActionEvaluationSelect(actionId, e.getValue()));
      }

      out.sort(Comparator.comparingInt(ActionEvaluationSelect::actionId));
      return out;

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse LLM JSON response: " + json, e);
    }
  }
}
