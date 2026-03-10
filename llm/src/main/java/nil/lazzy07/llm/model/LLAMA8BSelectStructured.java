/*
* File name: LLAMA8BSelectStructured.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-07 13:02:43
// Date modified: 2026-03-07 13:20:03
* ------
*/

package nil.lazzy07.llm.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * llama.cpp / OpenAI-like chat-completions client that:
 * - calls your local endpoint
 * - extracts assistant content
 * - then extracts a JSON OBJECT in structured wire format:
 *
 * {
 * "selections": [
 * { "actionId": "12", "reason": "..." },
 * { "actionId": "44", "reason": "..." }
 * ]
 * }
 *
 * - validates/repairs against ActionSelectV2 structured schema
 * - normalizes final cached/public output to:
 *
 * { "12": "...", "44": "..." }
 *
 * Cached values are always normalized compact JSON objects.
 */
public class LLAMA8BSelectStructured extends LLMApi {
  private static final Logger log = LoggerFactory.getLogger(LLAMA8BSelectStructured.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final URI endpoint; // e.g. http://127.0.0.1:8080/v1/chat/completions
  private final Duration timeout;

  // deterministic defaults
  private final int seed;
  private final int maxTokens;

  // repair defaults
  private final int maxRepairAttempts;

  private volatile HttpClient client;

  public LLAMA8BSelectStructured(boolean useCache, String cacheDirectory, String domain) {
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

  public LLAMA8BSelectStructured(
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
    params.put("output_schema", "ActionSelectV2");
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
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while calling llama.cpp server at " + endpoint, e);
    } catch (IOException e) {
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

    String extracted = tryExtractStructuredSelectionJson(rawResponse);

    if (isValidStructuredSelectionJson(extracted)) {
      String normalized = normalizeStructuredSelectionsToMap(extracted);
      if (isValidActionSelectMapJson(normalized)) {
        return normalized;
      }
    }

    String output = rawResponse;

    for (int attempt = 1; attempt <= maxRepairAttempts; attempt++) {
      log.debug("Invalid ActionSelect structured JSON. Attempting repair {}/{}", attempt, maxRepairAttempts);

      String repairUserPrompt = "Your previous output did not match the required JSON schema.\n"
          + "Fix it now.\n"
          + "Rules:\n"
          + "- Return ONLY valid JSON (no markdown, no commentary).\n"
          + "- Must be exactly one JSON object.\n"
          + "- Top-level object must contain only the field \"selections\".\n"
          + "- \"selections\" must be an array with at most 5 items.\n"
          + "- Each item must be an object with exactly these fields:\n"
          + "  - \"actionId\": string containing digits only, like \"12\"\n"
          + "  - \"reason\": non-empty string\n"
          + "- Include ONLY selected actions; do not include unselected actions.\n"
          + "- Do not include any other fields.\n\n"
          + "Required format example:\n"
          + "{\n"
          + "  \"selections\": [\n"
          + "    { \"actionId\": \"12\", \"reason\": \"...\" },\n"
          + "    { \"actionId\": \"44\", \"reason\": \"...\" }\n"
          + "  ]\n"
          + "}\n\n"
          + "Here is the invalid output to repair:\n"
          + output;

      Map<String, Object> repairParams = new LinkedHashMap<>();
      if (parameters != null) {
        repairParams.putAll(parameters);
      }
      repairParams.put("repairAttempt", attempt);

      output = callModel(systemPrompt, userPrompt + "\n\n" + repairUserPrompt, repairParams);

      String repairedExtracted = tryExtractStructuredSelectionJson(output);
      if (isValidStructuredSelectionJson(repairedExtracted)) {
        String normalized = normalizeStructuredSelectionsToMap(repairedExtracted);
        if (isValidActionSelectMapJson(normalized)) {
          return normalized;
        }
      }
    }

    // last resort: if it at least looks like the structured object, try normalizing
    String last = tryExtractStructuredSelectionJson(output);
    if (looksLikeJsonObject(last)) {
      try {
        String normalized = normalizeStructuredSelectionsToMap(last);
        if (isValidActionSelectMapJson(normalized)) {
          return normalized;
        }
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

  /**
   * Tries to extract a structured JSON object of the form:
   * {
   * "selections": [...]
   * }
   */
  private String tryExtractStructuredSelectionJson(String s) {
    if (s == null) {
      return "";
    }

    String text = s.trim();
    if (text.isEmpty()) {
      return text;
    }

    // raw whole response already is JSON object
    if (looksLikeJsonObject(text)) {
      try {
        JsonNode node = MAPPER.readTree(text);
        if (node != null && node.isObject() && node.has("selections")) {
          return MAPPER.writeValueAsString(node);
        }
      } catch (Exception ignore) {
      }
    }

    text = stripCodeFences(text).trim();

    if (looksLikeJsonObject(text)) {
      try {
        JsonNode node = MAPPER.readTree(text);
        if (node != null && node.isObject() && node.has("selections")) {
          return MAPPER.writeValueAsString(node);
        }
      } catch (Exception ignore) {
      }
    }

    int start = findFirstJsonObjectStart(text);
    while (start >= 0) {
      int end = findMatchingBraceEnd(text, start);
      if (end < 0) {
        break;
      }

      String candidate = text.substring(start, end + 1).trim();
      try {
        JsonNode node = MAPPER.readTree(candidate);
        if (node != null && node.isObject() && node.has("selections")) {
          return MAPPER.writeValueAsString(node);
        }
      } catch (Exception ignore) {
      }

      start = findFirstJsonObjectStart(text.substring(start + 1));
      if (start >= 0) {
        start = start + 1;
      }
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
      if (text.charAt(i) == '{') {
        return i;
      }
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
        if (depth == 0) {
          return i;
        }
        if (depth < 0) {
          return -1;
        }
      }
    }
    return -1;
  }

  // =========================
  // Structured validation + normalization
  // =========================

  /**
   * Validates structured wire format:
   * {
   * "selections": [
   * { "actionId": "12", "reason": "..." }
   * ]
   * }
   */
  private boolean isValidStructuredSelectionJson(String s) {
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

    JsonNode selections = root.get("selections");
    if (selections == null || !selections.isArray()) {
      return false;
    }

    if (root.size() != 1) {
      return false;
    }

    if (selections.size() > 5) {
      return false;
    }

    for (JsonNode item : selections) {
      if (!item.isObject()) {
        return false;
      }

      if (item.size() != 2) {
        return false;
      }

      JsonNode actionIdNode = item.get("actionId");
      JsonNode reasonNode = item.get("reason");

      if (actionIdNode == null || !actionIdNode.isTextual()) {
        return false;
      }
      if (reasonNode == null || !reasonNode.isTextual()) {
        return false;
      }

      String actionId = actionIdNode.asText().trim();
      String reason = reasonNode.asText().trim();

      if (!actionId.matches("^[0-9]+$")) {
        return false;
      }
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

  /**
   * Convert structured wire format:
   * {
   * "selections": [
   * {"actionId":"12","reason":"..."},
   * {"actionId":"44","reason":"..."}
   * ]
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

        // last write wins if duplicates occur
        out.put(actionId, reason);
      }

      return MAPPER.writeValueAsString(out);

    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to normalize structured selections JSON", e);
    }
  }

  // =========================
  // Final normalized-map validation
  // =========================

  /**
   * Validates normalized ActionSelectMap schema:
   * JSON object with digit-only keys and string values.
   *
   * Example:
   * { "12": "reason", "44": "reason" }
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
