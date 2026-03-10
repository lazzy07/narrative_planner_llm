/*
* File name: ChatGPTMiniJson.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-09 16:22:07
// Date modified: 2026-03-09 18:07:52
* ------
*/

package nil.lazzy07.llm.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
 * OpenAI Responses client (gpt-5-mini) using JSON mode.
 *
 * Expected model output shape:
 * {
 * "12": "brief belief-based justification",
 * "44": "brief belief-based justification"
 * }
 *
 * Why JSON mode instead of json_schema?
 * - The prompt already asks for a dynamic-key JSON object.
 * - json_schema is a poor fit for dynamic-key maps.
 * - JSON mode guarantees valid JSON while letting us validate the exact shape
 * ourselves.
 */
public class ChatGPTMiniJson extends LLMApi {
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

  public ChatGPTMiniJson(boolean useCache, String cacheDirectory, String domain) {
    this(
        useCache,
        cacheDirectory,
        domain,
        URI.create("https://api.openai.com/v1/responses"),
        Duration.ofSeconds(30), // connect timeout
        Duration.ofSeconds(240), // request timeout
        System.getenv("OPENAI_API_KEY"),
        3);
  }

  public ChatGPTMiniJson(
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
    params.put("output_schema", "ActionSelectMap");
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

    body.put("reasoning", Map.of("effort", "medium"));

    // JSON mode: valid JSON object output, no fixed wrapper schema.
    body.put("text", Map.of(
        "format", Map.of("type", "json_object")));

    Object promptCacheKey = parameters != null ? parameters.get("prompt_cache_key") : null;
    if (promptCacheKey instanceof String s && !s.isBlank()) {
      body.put("prompt_cache_key", s);
      body.put("prompt_cache_retention", "24h");
    }

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

    return extractJsonObjectText(resp.body()).trim();
  }

  @Override
  protected String postProcessResponse(
      String rawResponse,
      String systemPrompt,
      String userPrompt,
      Map<String, Object> parameters) {

    String normalized = compactJsonObject(rawResponse);

    if (isValidActionSelectMapJson(normalized)) {
      return normalized;
    }

    throw new RuntimeException("Model returned invalid ActionSelect JSON: " + rawResponse);
  }

  // ================================
  // Responses parsing
  // ================================

  /**
   * Extracts the JSON object string from the Responses API payload.
   *
   * We try, in order:
   * 1) response.output_text if present and parseable as a JSON object
   * 2) output[].content[] text blobs that parse as a JSON object
   *
   * Also checks top-level response status and surfaces incomplete responses
   * clearly.
   */
  private String extractJsonObjectText(String responseJson) {
    try {
      JsonNode root = MAPPER.readTree(responseJson);

      String status = root.path("status").asText("");
      if ("incomplete".equals(status)) {
        String reason = root.path("incomplete_details").path("reason").asText("unknown");
        throw new RuntimeException(
            "Responses API returned incomplete result. reason=" + reason + ", body=" + responseJson);
      }

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

            if ("output_text".equals(type) || "text".equals(type)) {
              String text = c.path("text").asText("");
              if (!text.isBlank()) {
                JsonNode parsed = tryParseJson(text);
                if (parsed != null && parsed.isObject()) {
                  return MAPPER.writeValueAsString(parsed);
                }
              }
            }

            if ("refusal".equals(type)) {
              throw new RuntimeException("Model refused JSON output: " + c.toString());
            }
          }
        }
      }

      throw new RuntimeException("No JSON object payload found in Responses output: " + responseJson);

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

  private String compactJsonObject(String json) {
    try {
      JsonNode root = MAPPER.readTree(json);
      if (!root.isObject()) {
        throw new RuntimeException("Expected JSON object");
      }
      return MAPPER.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to parse model JSON object", e);
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
          log.warn("OpenAI transient HTTP {} on attempt {}/{}; retrying.", code, attempt, maxAttempts);
          sleepMs(backoffMs(attempt));
          continue;
        }

        return resp;

      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        if (attempt >= maxAttempts) {
          throw new RuntimeException("Failed after retries", ie);
        }
        log.warn("Interrupted during OpenAI request on attempt {}/{}; retrying.", attempt, maxAttempts);
        sleepMs(backoffMs(attempt));

      } catch (IOException ioe) {
        if (attempt >= maxAttempts) {
          throw new RuntimeException("Failed after retries", ioe);
        }
        log.warn("I/O error during OpenAI request on attempt {}/{}; retrying: {}",
            attempt, maxAttempts, ioe.toString());
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
  // Validation
  // ================================

  /**
   * Validates normalized ActionSelectMap schema:
   * JSON object with digit-only keys and non-empty string values.
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

    @SuppressWarnings("deprecation")
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

      String reason = val.asText().trim();
      if (reason.isEmpty()) {
        return false;
      }

      String lower = reason.toLowerCase();
      if (lower.equals("n/a") || lower.equals("na") || lower.equals("none")) {
        return false;
      }
    }

    return true;
  }
}
