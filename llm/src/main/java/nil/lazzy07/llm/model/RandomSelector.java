/*
* File name: Random.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-23 09:31:37
// Date modified: 2026-03-23 11:25:48
* ------
*/

package nil.lazzy07.llm.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.stream.Collectors;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import edu.uky.cs.nil.sabre.comp.CompiledAction;

public class RandomSelector extends LLMApi {
  private final Random rand = new Random(1000);

  public RandomSelector(String type, boolean useCache, String cacheDirectory, String domain) {
    super(type, useCache, cacheDirectory, domain);
  }

  public RandomSelector(boolean useCache, String cacheDirectory, String domain) {
    super("random", useCache, cacheDirectory, domain);
  }

  @Override
  protected String callModel(String systemPrompt, String userPrompt, Map<String, Object> parameters) {
    return null;
  }

  @Override
  public String query(ArrayList<CompiledAction> actions) {
    int[] randomSelections = generate(actions.size());

    ObjectMapper mapper = new ObjectMapper();
    ObjectNode root = mapper.createObjectNode();

    for (int selection : randomSelections) {
      root.put(String.valueOf(selection), "This is a random selection");
    }

    try {
      return mapper.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      return "";
    }
  }

  public int[] generate(int n) {
    List<Integer> list = IntStream.rangeClosed(1, n)
        .boxed()
        .collect(Collectors.toList());
    Collections.shuffle(list, rand);
    return list.stream().mapToInt(Integer::intValue).toArray();
  }

  @Override
  public void init() {

  }
}
