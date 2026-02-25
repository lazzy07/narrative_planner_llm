/*
* File name: ActionEvaluationParser.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-17 15:49:54
// Date modified: 2026-02-25 03:36:48
* ------
*/

package nil.lazzy07.common.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

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
}
