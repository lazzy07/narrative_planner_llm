/*
* File name: ChatGPT5MiniSelectImproved.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-02 20:39:46
// Date modified: 2026-03-02 23:47:52
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
 * OpenAI Responses client (gpt-5-mini) that:
 * - calls https://api.openai.com/v1/responses
 * - extracts output_text
 * - then extracts a JSON OBJECT from that text even if the model added prose
 * - validates/repairs into ActionSelectMap format:
 *
 * { "12": "reason", "44": "reason" }
 *
 * Notes:
 * - Does NOT use json_schema (no schema limitations / 400 errors).
 * - Uses text.format = json_object to reduce prose, but still extracts/repairs
 * defensively.
 * - Cached values are normalized JSON objects (compact).
 */
public class ChatGPT5MiniSelectImproved extends LLMApi {
  private static final Logger log = LoggerFactory.getLogger(ChatGPT5MiniSelectImproved.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final URI endpoint;
  private final Duration timeout;
  private final String apiKey;

  // retry defaults (transport/server throttling)
  private final int maxAttempts;

  // repair defaults (bad JSON / wrong shape)
  private final int maxRepairAttempts;

  private volatile HttpClient client;

  public ChatGPT5MiniSelectImproved(boolean useCache, String cacheDirectory, String domain) {
    this(
        useCache,
        cacheDirectory,
        domain,
        URI.create("https://api.openai.com/v1/responses"),
        Duration.ofSeconds(60),
        System.getenv("OPENAI_API_KEY"),
        3,
        2);
  }

  public ChatGPT5MiniSelectImproved(
      boolean useCache,
      String cacheDirectory,
      String domain,
      URI endpoint,
      Duration timeout,
      String apiKey,
      int maxAttempts,
      int maxRepairAttempts) {
    super("GPT-5-MINI", useCache, cacheDirectory, domain);
    this.endpoint = endpoint;
    this.timeout = timeout;
    this.apiKey = apiKey;
    this.maxAttempts = Math.max(1, maxAttempts);
    this.maxRepairAttempts = Math.max(0, maxRepairAttempts);
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

  /** Planner convenience wrapper. Returns normalized JSON object string. */
  public String queryActionSelectJson(String systemPrompt, String userPrompt) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("output_schema", "ActionSelectV1");
    return query(systemPrompt, userPrompt, params);
  }

  @Override
  protected String callModel(String systemPrompt, String userPrompt, Map<String, Object> parameters) {

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", "gpt-5-mini");

    body.put(
        "input",
        new Object[] {
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        });

    // JSON mode (NOT json_schema). This avoids schema subset limitations.
    body.put("text", Map.of("format", Map.of("type", "json_object")));

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

    // Return raw output_text (may still contain minor extra text in edge cases).
    return extractOutputText(resp.body()).trim();
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
          + "- Must be a JSON object mapping numeric actionId strings to reason strings.\n"
          + "- Example: {\"12\":\"reason\"}\n"
          + "- Include ONLY selected actions; omit rejected actions.\n"
          + "- Select at most 5 actions.\n"
          + "- Do NOT invent new facts or beliefs; use ONLY the provided state, beliefs, and plan prefix.\n"
          + "- Do NOT repeat the same action unless absolutely necessary.\n\n"
          + "Invalid output:\n"
          + output;

      Map<String, Object> repairParams = new LinkedHashMap<>();
      if (parameters != null)
        repairParams.putAll(parameters);
      repairParams.put("repairAttempt", attempt);

      // Re-call the model; callModel returns output_text
      output = callModel(systemPrompt, userPrompt + "\n\n" + repairUserPrompt, repairParams);

      String repairedExtracted = tryExtractJsonObjectString(output);
      if (isValidActionSelectMapJson(repairedExtracted)) {
        return normalizeJson(repairedExtracted);
      }
    }

    // Last resort: if it looks like an object, try normalizing it; otherwise return
    // extracted/raw.
    String last = tryExtractJsonObjectString(output);
    if (looksLikeJsonObject(last)) {
      try {
        if (isValidActionSelectMapJson(last))
          return normalizeJson(last);
      } catch (RuntimeException ignore) {
        // fall through
      }
    }

    return extracted.trim();
  }

  // ================================
  // Responses parsing
  // ================================

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

  // ================================
  // Extraction (LLAMA-style)
  // ================================

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

  // ================================
  // Validation + normalization
  // ================================

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

    // Enforce "select at most 5 actions"
    if (root.size() > 5)
      return false;

    var fields = root.fields();
    while (fields.hasNext()) {
      var e = fields.next();
      String key = e.getKey();
      JsonNode val = e.getValue();

      // numeric keys only
      if (key == null || !key.matches("^[0-9]+$"))
        return false;

      if (val == null || !val.isTextual())
        return false;

      String r = val.asText().trim();
      if (r.isEmpty())
        return false;

      // guard against junk
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
