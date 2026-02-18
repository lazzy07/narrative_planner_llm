/*
* File name: ChatGPT5MiniApi.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-17 18:15:41
// Date modified: 2026-02-17 18:17:03
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

  // Keep retries modest; planner can also retry at a higher level if desired
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

  /**
   * Convenience wrapper for your planner nodes.
   * Returns the JSON array text that matches your schema.
   *
   * You can pass extra parameters if you want (e.g., max_output_tokens,
   * reasoning.effort),
   * but by default we keep it "clean" and rely on Structured Outputs strict JSON
   * Schema.
   */
  public String queryActionSenseJson(String systemPrompt, String userPrompt) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("output_schema", "ActionSenseV1");
    // Optional knobs you MAY want later:
    // params.put("max_output_tokens", 600);
    // params.put("reasoning_effort", "minimal");
    return query(systemPrompt, userPrompt, params);
  }

  @Override
  protected String callModel(String systemPrompt, String userPrompt, Map<String, Object> parameters) {
    // LLMApi.ensureInit() already called before this
    Map<String, Object> body = new LinkedHashMap<>();

    // Required
    body.put("model", "gpt-5-mini");

    // Input messages (Responses API accepts an array of {role, content})
    body.put("input", new Object[] {
        Map.of("role", "system", "content", systemPrompt),
        Map.of("role", "user", "content", userPrompt)
    });

    // Structured Outputs: strict JSON schema
    body.put("text", Map.of(
        "format", Map.of(
            "type", "json_schema",
            "name", "action_sense",
            "strict", true,
            "schema", buildActionSenseSchema())));

    // Optional request fields (only set if present)
    if (parameters != null) {
      Object maxOut = parameters.get("max_output_tokens");
      if (maxOut instanceof Number n) {
        body.put("max_output_tokens", n.intValue());
      }

      Object effort = parameters.get("reasoning_effort");
      if (effort instanceof String s && !s.isBlank()) {
        body.put("reasoning", Map.of("effort", s));
      }

      // NOTE: You asked to follow latest guidance: we do NOT set temperature/top_p by
      // default here.
      // If you later want them, you can add:
      // body.put("temperature", ...); body.put("top_p", ...);
      // but keep in mind some reasoning models may restrict/ignore those settings.
    }

    // You likely want no server-side storage for planner runs (optional):
    body.put("store", false);

    String json;
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

    // Extract the model-produced text (this should be your JSON array string)
    return extractOutputText(resp.body()).trim();
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

          // simple backoff
          sleepMs(250L * attempt * attempt);
          continue;
        }

        return resp;

      } catch (IOException | InterruptedException e) {
        Thread.currentThread().interrupt();
        if (attempt >= maxAttempts) {
          throw new RuntimeException("Failed calling OpenAI after " + attempt + " attempts", e);
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

  /**
   * Responses API returns an object with "output": [...]
   * We collect all content items of type "output_text" and concatenate their
   * "text".
   */
  private String extractOutputText(String responseJson) {
    try {
      JsonNode root = MAPPER.readTree(responseJson);
      JsonNode output = root.get("output");
      if (output == null || !output.isArray()) {
        throw new RuntimeException("Unexpected Responses shape: missing output[]");
      }

      StringBuilder sb = new StringBuilder();

      for (JsonNode item : output) {
        // We look for message items (type: "message") that contain content[]
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
        // If empty, dump a helpful error
        throw new RuntimeException("No output_text found in response output[]. Raw: " + responseJson);
      }

      return text;

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse Responses API JSON: " + responseJson, e);
    }
  }

  /**
   * JSON Schema for your desired output:
   * [
   * {
   * actionId: number,
   * confidence: number (0..1),
   * isExplained: boolean,
   * explanation: string
   * }
   * ]
   */
  private Map<String, Object> buildActionSenseSchema() {
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

    Map<String, Object> arraySchema = new LinkedHashMap<>();
    arraySchema.put("type", "array");
    arraySchema.put("items", itemSchema);

    return arraySchema;
  }
}
