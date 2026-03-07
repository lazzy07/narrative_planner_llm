/*
* File name: LLAMA8BSelectImproved.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-02 20:46:40
// Date modified: 2026-03-06 17:52:50
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
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * llama.cpp / OpenAI-like chat-completions client that:
 * - calls your local endpoint
 * - extracts assistant content
 * - then extracts a JSON OBJECT (actionId -> reason) even if the model added
 * prose
 * - validates/repairs against ActionSelectMap schema ( "actionId": "reason" )
 *
 * Cached values are always normalized JSON objects (compact).
 */
public class LLAMA8BSelectImproved extends LLMApi {
  private static final Logger log = LoggerFactory.getLogger(LLAMA8BSelect.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final URI endpoint; // e.g. http://127.0.0.1:8080/v1/chat/completions
  private final Duration timeout;

  // deterministic defaults
  private final int seed;
  private final int maxTokens;

  // repair defaults
  private final int maxRepairAttempts;

  private volatile HttpClient client;

  public LLAMA8BSelectImproved(boolean useCache, String cacheDirectory, String domain) {
    this(
        useCache,
        cacheDirectory,
        domain,
        URI.create("http://127.0.0.1:8080/v1/chat/completions"),
        Duration.ofSeconds(60),
        42,
        4096,
        2);
  }

  public LLAMA8BSelectImproved(
      boolean useCache,
      String cacheDirectory,
      String domain,
      URI endpoint,
      Duration timeout,
      int seed,
      int maxTokens,
      int maxRepairAttempts) {
    super("LLAMA-8B", useCache, cacheDirectory, domain);
    this.endpoint = endpoint;
    this.timeout = timeout;
    this.seed = seed;
    this.maxTokens = maxTokens;
    this.maxRepairAttempts = maxRepairAttempts;
  }

  @Override
  public void init() {
    this.client = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build();
  }

  /** Convenience wrapper. */
  public String queryActionSelectJson(String systemPrompt, String userPrompt) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("endpoint", endpoint.toString());
    params.put("temperature", 0.0);
    params.put("top_p", 1.0);
    params.put("seed", seed);
    params.put("max_tokens", maxTokens);
    params.put("output_schema", "ActionSelectV1");
    return query(systemPrompt, userPrompt, params);
  }

  @Override
  protected String callModel(String systemPrompt, String userPrompt, Map<String, Object> parameters) {

    Map<String, Object> req = new LinkedHashMap<>();
    req.put("model", "local");
    req.put(
        "messages",
        new Object[] {
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        });

    // deterministic
    req.put("temperature", 0.0);
    req.put("top_p", 1.0);
    req.put("seed", seed);

    // budget
    req.put("max_tokens", maxTokens);

    final String body;
    try {
      body = MAPPER.writeValueAsString(req);
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize llama.cpp request JSON", e);
    }

    HttpRequest httpReq = HttpRequest.newBuilder()
        .uri(endpoint)
        .timeout(timeout)
        .header("Content-Type", "application/json; charset=utf-8")
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build();

    final HttpResponse<String> resp;
    try {
      resp = client.send(httpReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Failed to call llama.cpp server at " + endpoint, e);
    }

    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
      throw new RuntimeException("llama.cpp server error " + resp.statusCode() + ": " + resp.body());
    }

    return extractAssistantContent(resp.body());
  }

  @Override
  protected String postProcessResponse(
      String rawResponse,
      String systemPrompt,
      String userPrompt,
      Map<String, Object> parameters) {

    String extracted = tryExtractJsonObjectString(rawResponse);

    if (isValidActionSelectMapJson(extracted)) {
      return normalizeJson(extracted);
    }

    String output = rawResponse;

    for (int attempt = 1; attempt <= maxRepairAttempts; attempt++) {
      log.debug("Invalid ActionSelect MAP JSON. Attempting repair {}/{}", attempt, maxRepairAttempts);

      String repairUserPrompt = "Your previous output did not match the required JSON object schema.\n"
          + "Fix it now.\n"
          + "Rules:\n"
          + "- Return ONLY valid JSON (no markdown, no commentary).\n"
          + "- Must be a JSON object mapping actionId -> reason.\n"
          + "- Keys must be action IDs as digits in strings (example: \"12\").\n"
          + "- Values must be strings (1 short sentence, character belief-driven).\n"
          + "- Include ONLY selected actions; do not include unselected actions.\n"
          + "- Do not include any other fields.\n\n"
          + "Here is the invalid output to repair:\n"
          + output;

      Map<String, Object> repairParams = new LinkedHashMap<>();
      if (parameters != null)
        repairParams.putAll(parameters);
      repairParams.put("repairAttempt", attempt);

      output = callModel(systemPrompt, userPrompt + "\n\n" + repairUserPrompt, repairParams);

      String repairedExtracted = tryExtractJsonObjectString(output);
      if (isValidActionSelectMapJson(repairedExtracted)) {
        return normalizeJson(repairedExtracted);
      }
    }

    // last resort: if it looks like an object, try normalizing it
    String last = tryExtractJsonObjectString(output);
    if (looksLikeJsonObject(last)) {
      try {
        return normalizeJson(last);
      } catch (RuntimeException ignore) {
        // fall through
      }
    }

    return output.trim();
  }

  // =========================
  // Helpers
  // =========================

  private String extractAssistantContent(String openAiLikeJson) {
    try {
      JsonNode root = MAPPER.readTree(openAiLikeJson);
      JsonNode choices = root.get("choices");
      if (choices == null || !choices.isArray() || choices.isEmpty()) {
        throw new RuntimeException("Unexpected llama.cpp response: missing choices[]");
      }

      JsonNode msg = choices.get(0).get("message");
      if (msg != null && msg.get("content") != null) {
        return msg.get("content").asText();
      }

      JsonNode text = choices.get(0).get("text");
      if (text != null && text.isTextual()) {
        return text.asText();
      }

      throw new RuntimeException("Unexpected llama.cpp response: missing message.content/text");
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse llama.cpp response JSON: " + openAiLikeJson, e);
    }
  }

  private String tryExtractJsonObjectString(String s) {
    if (s == null)
      return "";
    String text = s.trim();
    if (text.isEmpty())
      return text;

    if (looksLikeJsonObject(text))
      return text;

    text = stripCodeFences(text).trim();
    if (looksLikeJsonObject(text))
      return text;

    int start = findFirstJsonObjectStart(text);
    if (start < 0)
      return s.trim();

    int end = findMatchingBraceEnd(text, start);
    if (end < 0)
      return s.trim();

    String candidate = text.substring(start, end + 1).trim();

    try {
      JsonNode node = MAPPER.readTree(candidate);
      if (node != null && node.isObject())
        return candidate;
    } catch (Exception ignore) {
    }

    return s.trim();
  }

  private boolean looksLikeJsonObject(String s) {
    String t = s.trim();
    return t.startsWith("{") && t.endsWith("}");
  }

  private String stripCodeFences(String text) {
    String t = text;
    t = t.replaceAll("(?s)```(?:json|JSON)?\\s*", "");
    t = t.replaceAll("(?s)```\\s*", "");
    return t;
  }

  private int findFirstJsonObjectStart(String text) {
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '{')
        return i;
    }
    return -1;
  }

  private int findMatchingBraceEnd(String text, int startIdx) {
    int depth = 0;
    boolean inString = false;
    boolean escape = false;

    for (int i = startIdx; i < text.length(); i++) {
      char c = text.charAt(i);

      if (inString) {
        if (escape) {
          escape = false;
        } else if (c == '\\') {
          escape = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }

      if (c == '"') {
        inString = true;
        continue;
      }

      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0)
          return i;
        if (depth < 0)
          return -1;
      }
    }
    return -1;
  }

  /**
   * Validates ActionSelectMap schema:
   * JSON object with digit-only keys and string values (reasons).
   *
   * Example:
   * { "12": "reason", "44": "reason" }
   */
  private boolean isValidActionSelectMapJson(String s) {
    if (s == null)
      return false;
    String trimmed = s.trim();
    if (trimmed.isEmpty())
      return false;

    JsonNode root;
    try {
      root = MAPPER.readTree(trimmed);
    } catch (Exception e) {
      return false;
    }

    if (!root.isObject())
      return false;

    // allow empty object if you want "select none"; if not, enforce at least 1
    // field:
    // if (root.size() == 0) return false;

    @SuppressWarnings("deprecation")
    var fields = root.fields();
    while (fields.hasNext()) {
      var e = fields.next();
      String key = e.getKey();
      JsonNode val = e.getValue();

      if (key == null || !key.matches("^[0-9]+$"))
        return false;
      if (val == null || !val.isTextual())
        return false;

      String r = val.asText().trim();
      if (r.isEmpty())
        return false;

      String lower = r.toLowerCase();
      if (lower.equals("n/a") || lower.equals("na") || lower.equals("none"))
        return false;
    }

    return true;
  }

  private String normalizeJson(String json) {
    try {
      JsonNode node = MAPPER.readTree(json);
      return MAPPER.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to normalize JSON", e);
    }
  }
}
