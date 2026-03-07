/*
* File name: ChatGPT5MiniSelectStructured.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-06 14:14:58
// Date modified: 2026-03-06 14:16:21
* ------
*/

package nil.lazzy07.llm.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI Responses client (gpt-5-mini) using text.format json_schema.
 *
 * Model output schema on the wire:
 * {
 * "selections": [
 * { "actionId": "12", "reason": "..." },
 * { "actionId": "44", "reason": "..." }
 * ]
 * }
 *
 * Public normalized return value stays:
 * { "12": "reason", "44": "reason" }
 *
 * Why this shape?
 * - Strict structured outputs fit fixed schemas much better than dynamic-key
 * maps.
 * - This avoids repair loops and reduces timeout frequency.
 */
public class ChatGPT5MiniSelectStructured extends LLMApi {
  private static final Logger log = LoggerFactory.getLogger(ChatGPT5MiniSelectStructured.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String MODEL = "gpt-5-mini";

  private final URI endpoint;
  private final Duration connectTimeout;
  private final Duration requestTimeout;
  private final String apiKey;

  // transport/server retry defaults
  private final int maxAttempts;

  private volatile HttpClient client;

  public ChatGPT5MiniSelectStructured(boolean useCache, String cacheDirectory, String domain) {
    this(
        useCache,
        cacheDirectory,
        domain,
        URI.create("https://api.openai.com/v1/responses"),
        Duration.ofSeconds(30), // connect timeout
        Duration.ofSeconds(240), // request timeout: much larger than before
        System.getenv("OPENAI_API_KEY"),
        3);
  }

  public ChatGPT5MiniSelectStructured(
      boolean useCache,
      String cacheDirectory,
      String domain,
      URI endpoint,
      Duration connectTimeout,
      Duration requestTimeout,
      String apiKey,
      int maxAttempts) {
    super("GPT-5-MINI", useCache, cacheDirectory, domain);
    this.endpoint = endpoint;
    this.connectTimeout = connectTimeout;
    this.requestTimeout = requestTimeout;
    this.apiKey = apiKey;
    this.maxAttempts = Math.max(1, maxAttempts);
  }

  @Override
  public void init() {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("OPENAI_API_KEY is missing/blank.");
    }

    this.client = HttpClient.newBuilder()
        .connectTimeout(connectTimeout)
        .build();
  }

  /** Planner convenience wrapper. Returns normalized JSON object string. */
  public String queryActionSelectJson(String systemPrompt, String userPrompt) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("output_schema", "ActionSelectV2");
    return query(systemPrompt, userPrompt, params);
  }

  @Override
  protected String callModel(String systemPrompt, String userPrompt, Map<String, Object> parameters) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", MODEL);

    body.put(
        "input",
        new Object[] {
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        });

    // Keep medium reasoning as requested.
    body.put("reasoning", Map.of("effort", "medium"));

    // Use Structured Outputs via Responses API text.format.
    body.put("text", Map.of("format", buildActionSelectSchema()));

    // Optional: stable cache key can help repeated similar prompts.
    // Safe to omit if you do not want it.
    Object promptCacheKey = parameters != null ? parameters.get("prompt_cache_key") : null;
    if (promptCacheKey instanceof String s && !s.isBlank()) {
      body.put("prompt_cache_key", s);
      body.put("prompt_cache_retention", "24h");
    }

    // Optional service tier pass-through if you want to configure it elsewhere.
    Object serviceTier = parameters != null ? parameters.get("service_tier") : null;
    if (serviceTier instanceof String s && !s.isBlank()) {
      body.put("service_tier", s);
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
        .timeout(requestTimeout)
        .header("Content-Type", "application/json; charset=utf-8")
        .header("Authorization", "Bearer " + apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
        .build();

    HttpResponse<String> resp = sendWithRetries(req);

    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
      throw new RuntimeException("OpenAI error " + resp.statusCode() + ": " + resp.body());
    }

    // Return parsed structured JSON payload, not free-form output_text.
    return extractStructuredSelectionJson(resp.body()).trim();
  }

  @Override
  protected String postProcessResponse(
      String rawResponse,
      String systemPrompt,
      String userPrompt,
      Map<String, Object> parameters) {

    // rawResponse here is already expected to be the structured JSON string
    // matching ActionSelectV2.
    String normalized = normalizeStructuredSelectionsToMap(rawResponse);

    if (isValidActionSelectMapJson(normalized)) {
      return normalized;
    }

    // No repair re-call: structured outputs should eliminate most bad-shape cases.
    // Failing fast here avoids multiplying timeout risk.
    throw new RuntimeException("Model returned invalid structured ActionSelect JSON: " + rawResponse);
  }

  // ================================
  // Structured Outputs schema
  // ================================

  private Map<String, Object> buildActionSelectSchema() {
    Map<String, Object> itemSchema = new LinkedHashMap<>();
    itemSchema.put("type", "object");
    itemSchema.put("properties", Map.of(
        "actionId", Map.of(
            "type", "string",
            "pattern", "^[0-9]+$"),
        "reason", Map.of(
            "type", "string",
            "minLength", 1)));
    itemSchema.put("required", new String[] { "actionId", "reason" });
    itemSchema.put("additionalProperties", false);

    Map<String, Object> rootSchema = new LinkedHashMap<>();
    rootSchema.put("type", "object");
    rootSchema.put("properties", Map.of(
        "selections", Map.of(
            "type", "array",
            "maxItems", 5,
            "items", itemSchema)));
    rootSchema.put("required", new String[] { "selections" });
    rootSchema.put("additionalProperties", false);

    Map<String, Object> format = new LinkedHashMap<>();
    format.put("type", "json_schema");
    format.put("name", "action_select_v2");
    format.put("strict", true);
    format.put("schema", rootSchema);

    return format;
  }

  // ================================
  // Responses parsing
  // ================================

  /**
   * Extracts the structured JSON string from the Responses API payload.
   *
   * We try, in order:
   * 1) response.output_text if present and parseable
   * 2) output[].content[] text blobs that look like JSON
   *
   * Also checks top-level response status and surfaces incomplete responses
   * clearly.
   */
  private String extractStructuredSelectionJson(String responseJson) {
    try {
      JsonNode root = MAPPER.readTree(responseJson);

      String status = root.path("status").asText("");
      if ("incomplete".equals(status)) {
        String reason = root.path("incomplete_details").path("reason").asText("unknown");
        throw new RuntimeException(
            "Responses API returned incomplete result. reason=" + reason + ", body=" + responseJson);
      }

      // First try convenience field if present.
      String outputText = root.path("output_text").asText("");
      if (!outputText.isBlank()) {
        JsonNode parsed = tryParseJson(outputText);
        if (parsed != null && parsed.isObject()) {
          return MAPPER.writeValueAsString(parsed);
        }
      }

      JsonNode output = root.path("output");
      if (output.isArray()) {
        for (JsonNode item : output) {
          JsonNode content = item.path("content");
          if (!content.isArray()) {
            continue;
          }

          for (JsonNode c : content) {
            String type = c.path("type").asText("");

            // Most text-bearing content comes through here.
            if ("output_text".equals(type) || "text".equals(type)) {
              String text = c.path("text").asText("");
              if (!text.isBlank()) {
                JsonNode parsed = tryParseJson(text);
                if (parsed != null && parsed.isObject()) {
                  return MAPPER.writeValueAsString(parsed);
                }
              }
            }

            // Refusal support: make it explicit instead of silently failing parsing.
            if ("refusal".equals(type)) {
              throw new RuntimeException("Model refused structured output: " + c.toString());
            }
          }
        }
      }

      throw new RuntimeException("No structured JSON payload found in Responses output: " + responseJson);

    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException("Failed parsing Responses JSON: " + responseJson, e);
    }
  }

  private JsonNode tryParseJson(String s) {
    try {
      return MAPPER.readTree(s);
    } catch (Exception e) {
      return null;
    }
  }

  // ================================
  // Retry/backoff
  // ================================

  private HttpResponse<String> sendWithRetries(HttpRequest req) {
    int attempt = 0;

    while (true) {
      attempt++;

      try {
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        int code = resp.statusCode();

        // Retry transient server/rate-limit cases only.
        if (code == 429 || code == 408 || (code >= 500 && code <= 599)) {
          if (attempt >= maxAttempts) {
            return resp;
          }
          sleepMs(backoffMs(attempt));
          continue;
        }

        return resp;

      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        if (attempt >= maxAttempts) {
          throw new RuntimeException("Failed after retries", ie);
        }
        sleepMs(backoffMs(attempt));

      } catch (IOException ioe) {
        if (attempt >= maxAttempts) {
          throw new RuntimeException("Failed after retries", ioe);
        }
        sleepMs(backoffMs(attempt));
      }
    }
  }

  private long backoffMs(int attempt) {
    long base = 500L * attempt * attempt;
    return Math.min(base, 5000L);
  }

  private void sleepMs(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  // ================================
  // Normalization
  // ================================

  /**
   * Convert structured wire format:
   * {
   * "selections": [{"actionId":"12","reason":"..."}, ...]
   * }
   *
   * into normalized compact map JSON:
   * {"12":"...","44":"..."}
   */
  private String normalizeStructuredSelectionsToMap(String json) {
    try {
      JsonNode root = MAPPER.readTree(json);
      JsonNode selections = root.path("selections");

      if (!selections.isArray()) {
        throw new RuntimeException("Missing selections[] in structured response");
      }

      ObjectNode out = MAPPER.createObjectNode();

      for (JsonNode item : selections) {
        String actionId = item.path("actionId").asText("").trim();
        String reason = item.path("reason").asText("").trim();

        if (actionId.isEmpty() || reason.isEmpty()) {
          throw new RuntimeException("Invalid selection item: " + item);
        }

        // last write wins if duplicates somehow occur
        out.put(actionId, reason);
      }

      return MAPPER.writeValueAsString(out);

    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to normalize structured selections JSON", e);
    }
  }

  // ================================
  // Validation
  // ================================

  /**
   * Validates normalized ActionSelectMap schema:
   * JSON object with digit-only keys and string values.
   */
  private boolean isValidActionSelectMapJson(String s) {
    if (s == null) {
      return false;
    }
    String trimmed = s.trim();
    if (trimmed.isEmpty()) {
      return false;
    }

    JsonNode root;
    try {
      root = MAPPER.readTree(trimmed);
    } catch (Exception e) {
      return false;
    }

    if (!root.isObject()) {
      return false;
    }

    if (root.size() > 5) {
      return false;
    }

    Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> e = fields.next();
      String key = e.getKey();
      JsonNode val = e.getValue();

      if (key == null || !key.matches("^[0-9]+$")) {
        return false;
      }
      if (val == null || !val.isTextual()) {
        return false;
      }

      String r = val.asText().trim();
      if (r.isEmpty()) {
        return false;
      }

      String lower = r.toLowerCase();
      if (lower.equals("n/a") || lower.equals("na") || lower.equals("none")) {
        return false;
      }
    }

    return true;
  }
}
