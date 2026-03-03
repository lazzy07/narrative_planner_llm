/*
* File name: ChatGPT5MiniSelectImproved.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-02 20:39:46
// Date modified: 2026-03-02 23:10:51
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

public class ChatGPT5MiniSelectImproved extends LLMApi {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final URI endpoint;
  private final Duration timeout;
  private final String apiKey;
  private final int maxAttempts;

  private volatile HttpClient client;

  public ChatGPT5MiniSelectImproved(boolean useCache, String cacheDirectory, String domain) {
    this(
        useCache,
        cacheDirectory,
        domain,
        URI.create("https://api.openai.com/v1/responses"),
        Duration.ofSeconds(60),
        System.getenv("OPENAI_API_KEY"),
        3);
  }

  public ChatGPT5MiniSelectImproved(
      boolean useCache,
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
      throw new IllegalStateException("OPENAI_API_KEY is missing/blank.");
    }

    this.client = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build();
  }

  /**
   * Planner convenience wrapper.
   *
   * Output shape:
   * {
   * "12": "reason",
   * "44": "reason"
   * }
   * (only selected actions included)
   */
  public String queryActionSelectJson(String systemPrompt, String userPrompt) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("output_schema", "ActionSelectV1");
    return query(systemPrompt, userPrompt, params);
  }

  @Override
  protected String callModel(
      String systemPrompt,
      String userPrompt,
      Map<String, Object> parameters) {

    Map<String, Object> body = new LinkedHashMap<>();

    body.put("model", "gpt-5-mini");

    body.put(
        "input",
        new Object[] {
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        });

    // Strict JSON schema (top-level must be object)
    body.put(
        "text",
        Map.of(
            "format",
            Map.of(
                "type", "json_schema",
                "name", "action_select_map",
                "strict", true,
                "schema", buildActionSelectMapSchema())));

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
      throw new RuntimeException("Failed to serialize Responses request JSON", e);
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

    String raw = extractOutputText(resp.body()).trim();

    // Validate it is an object and (optionally) normalize formatting.
    return validateAndNormalizeActionSelectMap(raw);
  }

  // ================================
  // Schema
  // ================================

  /*
   * Output schema (OpenAI-supported subset):
   * {
   * "<actionId>": "<reason>",
   * ...
   * }
   *
   * We cannot enforce digit-only keys in schema (propertyNames is not permitted),
   * so we enforce it in Java validation after extraction.
   */
  private Map<String, Object> buildActionSelectMapSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");

    // REQUIRED by OpenAI schema subset (even if empty)
    schema.put("properties", new LinkedHashMap<String, Object>());

    // Allow dynamic keys, enforce value type
    schema.put("additionalProperties", Map.of("type", "string"));

    // Optional (but good): disallow null
    // schema.put("additionalProperties", Map.of("type", "string"));

    return schema;
  }

  // ================================
  // Response parsing
  // ================================

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
        if (attempt >= maxAttempts)
          throw new RuntimeException("Failed after retries", ie);
        sleepMs(250L * attempt * attempt);

      } catch (IOException ioe) {
        if (attempt >= maxAttempts)
          throw new RuntimeException("Failed after retries", ioe);
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
          if ("output_text".equals(c.path("type").asText(""))) {
            sb.append(c.path("text").asText(""));
          }
        }
      }

      String text = sb.toString();
      if (text.isBlank()) {
        throw new RuntimeException("No output_text found.");
      }

      return text;

    } catch (Exception e) {
      throw new RuntimeException("Failed parsing Responses JSON: " + responseJson, e);
    }
  }

  private String validateAndNormalizeActionSelectMap(String raw) {
    try {
      JsonNode node = MAPPER.readTree(raw);
      if (!node.isObject()) {
        throw new RuntimeException("Expected JSON object (actionId->reason map). Got: " + raw);
      }

      node.fields().forEachRemaining(e -> {
        String k = e.getKey();
        if (!k.matches("^[0-9]+$")) {
          throw new RuntimeException("Invalid action id key: '" + k + "'. Raw: " + raw);
        }
        if (!e.getValue().isTextual()) {
          throw new RuntimeException("Reason must be a string for key '" + k + "'. Raw: " + raw);
        }
        String r = e.getValue().asText().trim();
        if (r.isEmpty()) {
          throw new RuntimeException("Empty reason for key '" + k + "'. Raw: " + raw);
        }
      });

      return MAPPER.writeValueAsString(node);

    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException("Failed to validate/normalize action select map: " + raw, e);
    }
  }
}
