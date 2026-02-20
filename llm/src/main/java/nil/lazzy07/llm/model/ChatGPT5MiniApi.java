/*
* File name: ChatGPT5MiniApi.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-17 18:15:41
// Date modified: 2026-02-18 01:26:13
* ------
*/

package nil.lazzy07.llm.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class ChatGPT5MiniApi extends LLMApi {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final URI endpoint;
  private final Duration timeout;
  private final String apiKey;

  private final int maxAttempts;

  private volatile HttpClient client;

  public ChatGPT5MiniApi(boolean useCache, String cacheDirectory, String domain) {
    this(useCache, cacheDirectory, domain,
        URI.create("https://api.openai.com/v1/responses"),
        Duration.ofSeconds(60),
        System.getenv("OPENAI_API_KEY"),
        3);
  }

  public ChatGPT5MiniApi(boolean useCache,
      String cacheDirectory,
      String domain,
      URI endpoint,
      Duration timeout,
      String apiKey,
      int maxAttempts) {
    super("GPT-5-MINI", useCache, cacheDirectory, domain);
    this.endpoint = endpoint;
    this.timeout = timeout;
    this.apiKey = apiKey;
    this.maxAttempts = Math.max(1, maxAttempts);
  }

  @Override
  public void init() {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("OPENAI_API_KEY is missing/blank. Set it in your environment.");
    }
    this.client = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build();
  }

  public String queryActionSenseJson(String systemPrompt, String userPrompt) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("output_schema", "ActionSenseV1");
    return query(systemPrompt, userPrompt, params);
  }

  @Override
  protected String callModel(String systemPrompt, String userPrompt, Map<String, Object> parameters) {
    Map<String, Object> body = new LinkedHashMap<>();

    body.put("model", "gpt-5-mini");

    body.put("input", new Object[] {
        Map.of("role", "system", "content", systemPrompt),
        Map.of("role", "user", "content", userPrompt)
    });

    // IMPORTANT: top-level schema MUST be type: "object"
    body.put("text", Map.of(
        "format", Map.of(
            "type", "json_schema",
            "name", "action_sense",
            "strict", true,
            "schema", buildActionSenseSchemaObjectWrapped())));

    if (parameters != null) {
      Object maxOut = parameters.get("max_output_tokens");
      if (maxOut instanceof Number n) {
        body.put("max_output_tokens", n.intValue());
      }

      Object effort = parameters.get("reasoning_effort");
      if (effort instanceof String s && !s.isBlank()) {
        body.put("reasoning", Map.of("effort", s));
      }
    }

    body.put("store", false);

    final String json;
    try {
      json = MAPPER.writeValueAsString(body);
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize OpenAI Responses request JSON", e);
    }

    HttpRequest req = HttpRequest.newBuilder()
        .uri(endpoint)
        .timeout(timeout)
        .header("Content-Type", "application/json; charset=utf-8")
        .header("Authorization", "Bearer " + apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
        .build();

    HttpResponse<String> resp = sendWithRetries(req);

    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
      throw new RuntimeException("OpenAI error " + resp.statusCode() + ": " + resp.body());
    }

    // Model returns JSON text that matches our schema:
    // { "items": [ ... ] }
    String raw = extractOutputText(resp.body()).trim();

    // Keep your existing downstream expectation: return ONLY the array string "[
    // ... ]"
    return unwrapItemsArrayAsString(raw);
  }

  private HttpResponse<String> sendWithRetries(HttpRequest req) {
    int attempt = 0;

    while (true) {
      attempt++;

      try {
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = resp.statusCode();

        if (code == 429 || (code >= 500 && code <= 599)) {
          if (attempt >= maxAttempts)
            return resp;
          sleepMs(250L * attempt * attempt);
          continue;
        }

        return resp;

      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        if (attempt >= maxAttempts) {
          throw new RuntimeException("Failed calling OpenAI after " + attempt + " attempts", ie);
        }
        sleepMs(250L * attempt * attempt);

      } catch (IOException ioe) {
        // DO NOT interrupt the thread on IOException
        if (attempt >= maxAttempts) {
          throw new RuntimeException("Failed calling OpenAI after " + attempt + " attempts", ioe);
        }
        sleepMs(250L * attempt * attempt);
      }
    }
  }

  private void sleepMs(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  private String extractOutputText(String responseJson) {
    try {
      JsonNode root = MAPPER.readTree(responseJson);
      JsonNode output = root.get("output");
      if (output == null || !output.isArray()) {
        throw new RuntimeException("Unexpected Responses shape: missing output[]");
      }

      StringBuilder sb = new StringBuilder();

      for (JsonNode item : output) {
        JsonNode content = item.get("content");
        if (content == null || !content.isArray())
          continue;

        for (JsonNode c : content) {
          String type = c.path("type").asText("");
          if ("output_text".equals(type)) {
            sb.append(c.path("text").asText(""));
          }
        }
      }

      String text = sb.toString();
      if (text.isBlank()) {
        throw new RuntimeException("No output_text found in response output[]. Raw: " + responseJson);
      }

      return text;

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse Responses API JSON: " + responseJson, e);
    }
  }

  /**
   * The API requires top-level type "object".
   * We'll return:
   * {
   * "items": [ { actionId, confidence, isExplained, explanation }, ... ]
   * }
   */
  private Map<String, Object> buildActionSenseSchemaObjectWrapped() {
    Map<String, Object> itemProps = new LinkedHashMap<>();
    itemProps.put("actionId", Map.of("type", "integer"));
    itemProps.put("confidence", Map.of("type", "number", "minimum", 0.0, "maximum", 1.0));
    itemProps.put("isExplained", Map.of("type", "boolean"));
    itemProps.put("explanation", Map.of("type", "string"));

    Map<String, Object> itemSchema = new LinkedHashMap<>();
    itemSchema.put("type", "object");
    itemSchema.put("properties", itemProps);
    itemSchema.put("required", new String[] { "actionId", "confidence", "isExplained", "explanation" });
    itemSchema.put("additionalProperties", false);

    Map<String, Object> itemsArraySchema = new LinkedHashMap<>();
    itemsArraySchema.put("type", "array");
    itemsArraySchema.put("items", itemSchema);

    Map<String, Object> rootProps = new LinkedHashMap<>();
    rootProps.put("items", itemsArraySchema);

    Map<String, Object> rootSchema = new LinkedHashMap<>();
    rootSchema.put("type", "object");
    rootSchema.put("properties", rootProps);
    rootSchema.put("required", new String[] { "items" });
    rootSchema.put("additionalProperties", false);

    return rootSchema;
  }

  /**
   * raw is expected to be a JSON object: { "items": [ ... ] }
   * Return the array portion as a JSON string: [ ... ]
   */
  private String unwrapItemsArrayAsString(String raw) {
    try {
      JsonNode node = MAPPER.readTree(raw);
      JsonNode items = node.get("items");
      if (items == null || !items.isArray()) {
        throw new RuntimeException("Expected top-level object with array field 'items'. Got: " + raw);
      }
      return MAPPER.writeValueAsString(items);
    } catch (Exception e) {
      throw new RuntimeException("Failed to unwrap 'items' array from model output: " + raw, e);
    }
  }
}
