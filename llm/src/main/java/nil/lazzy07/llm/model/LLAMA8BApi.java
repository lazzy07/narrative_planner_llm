/*
 * File name: LLAMA8BApi.java
 * Project:
 * Author: Lasantha M Senanayake
 * Date created: 2026-02-17
 * ------
 *
 * This implementation:
 * - calls a local llama.cpp server (OpenAI-compatible endpoint)
 * - enforces deterministic outputs (temperature=0, top_p=1, fixed seed)
 * - validates your required JSON array schema and auto-repairs if needed
 * - leverages LLMApi caching and only caches the final post-processed JSON
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
 *  - calls your local endpoint
 *  - extracts assistant content
 *  - then extracts a JSON ARRAY from that content even if the model added prose
 *  - validates/repairs against the ActionSense schema
 *
 * Notes:
 *  - We intentionally DO NOT cache junk: postProcessResponse returns a normalized JSON array string.
 *  - If the model returns: "Here is ...\n[ {...} ]", we will locate the [ ... ] and parse it.
 */
public class LLAMA8BApi extends LLMApi {
  private static final Logger log = LoggerFactory.getLogger(LLAMA8BApi.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final URI endpoint; // e.g. http://127.0.0.1:8080/v1/chat/completions
  private final Duration timeout;

  // deterministic defaults
  private final int seed;
  private final int maxTokens;

  // repair defaults
  private final int maxRepairAttempts;

  private volatile HttpClient client;

  public LLAMA8BApi(boolean useCache, String cacheDirectory, String domain) {
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

  public LLAMA8BApi(boolean useCache,
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

  /** Convenience wrapper for your planner. */
  public String queryActionSenseJson(String systemPrompt, String userPrompt) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("endpoint", endpoint.toString());
    params.put("temperature", 0.0);
    params.put("top_p", 1.0);
    params.put("seed", seed);
    params.put("max_tokens", maxTokens);
    params.put("output_schema", "ActionSenseV1");

    return query(systemPrompt, userPrompt, params);
  }

  @Override
  protected String callModel(String systemPrompt,
      String userPrompt,
      Map<String, Object> parameters) {

    final String strictJsonContract =
        "Return a JSON array of objects with exactly these keys:\n" +
        "\"actionId\" (number), \"confidence\" (float 0..1), \"isExplained\" (boolean), \"explanation\" (string).\n" +
        "Use double quotes for all keys/strings.\n" +
        "Return ONLY the JSON array (no markdown).";

    Map<String, Object> req = new LinkedHashMap<>();
    req.put("model", "local");
    req.put("messages", new Object[] {
        Map.of("role", "system", "content", systemPrompt + "\n\n" + strictJsonContract),
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

    // Return the assistant content only (not the whole envelope).
    return extractAssistantContent(resp.body());
  }

  /**
   * Post-process before caching:
   *  1) Try to extract a JSON array from raw response (even with prose around it)
   *  2) Validate the schema
   *  3) If invalid, attempt repair (and re-extract again)
   *
   * This guarantees you cache only the final normalized JSON array string.
   */
  @Override
  protected String postProcessResponse(String rawResponse,
      String systemPrompt,
      String userPrompt,
      Map<String, Object> parameters) {

    // 1) Extract array if possible (handles: prose + array)
    String extracted = tryExtractJsonArrayString(rawResponse);

    // 2) Validate schema
    if (isValidActionSenseJson(extracted)) {
      return normalizeJson(extracted);
    }

    // If the raw itself was a clean array but failed validation, try repair;
    // if raw had prose and we failed to find an array, repair will ask model to output only JSON.
    String output = rawResponse;

    for (int attempt = 1; attempt <= maxRepairAttempts; attempt++) {
      log.debug("Invalid ActionSense JSON. Attempting repair {}/{}", attempt, maxRepairAttempts);

      String repairUserPrompt =
          "Your previous output did not match the required JSON array schema.\n" +
          "Fix it now.\n" +
          "Rules:\n" +
          "- Return ONLY valid JSON (no markdown, no commentary).\n" +
          "- Must be a JSON array.\n" +
          "- Each element must include: actionId(number), confidence(float 0..1), isExplained(boolean), explanation(string).\n" +
          "- explanation must be one short sentence.\n\n" +
          "Here is the invalid output to repair:\n" + output;

      Map<String, Object> repairParams = new LinkedHashMap<>();
      if (parameters != null) repairParams.putAll(parameters);
      repairParams.put("repairAttempt", attempt);

      // Re-call model; it may still add prose, so we extract again.
      output = callModel(systemPrompt, userPrompt + "\n\n" + repairUserPrompt, repairParams);

      String repairedExtracted = tryExtractJsonArrayString(output);
      if (isValidActionSenseJson(repairedExtracted)) {
        return normalizeJson(repairedExtracted);
      }
    }

    // Best-effort: try extraction one last time and return whatever we got (normalized if parseable)
    String last = tryExtractJsonArrayString(output);
    if (looksLikeJsonArray(last)) {
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

      // Some servers return text instead
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
   * Attempts to locate and return a JSON array substring from arbitrary text.
   * Works for common cases:
   *  - "Here is ...\n[ {...} ]"
   *  - "```json\n[...]\n```"
   *
   * If no array is found, returns the original trimmed input.
   */
  private String tryExtractJsonArrayString(String s) {
    if (s == null) return "";
    String text = s.trim();
    if (text.isEmpty()) return text;

    // Fast path: already a JSON array
    if (looksLikeJsonArray(text)) return text;

    // Remove obvious code fences (non-destructive, keeps content)
    text = stripCodeFences(text).trim();
    if (looksLikeJsonArray(text)) return text;

    // Find first '[' that begins a valid JSON array, using bracket counting while respecting strings.
    int start = findFirstJsonArrayStart(text);
    if (start < 0) return s.trim();

    int end = findMatchingBracketEnd(text, start);
    if (end < 0) return s.trim();

    String candidate = text.substring(start, end + 1).trim();

    // Confirm it parses as JSON array (not just brackets)
    try {
      JsonNode node = MAPPER.readTree(candidate);
      if (node != null && node.isArray()) return candidate;
    } catch (Exception ignore) {
      // fall through
    }

    return s.trim();
  }

  private boolean looksLikeJsonArray(String s) {
    String t = s.trim();
    return t.startsWith("[") && t.endsWith("]");
  }

  private String stripCodeFences(String text) {
    // naive fence stripping is fine here; we still do real extraction afterwards.
    // Removes ```json ... ``` and ``` ... ```
    String t = text;
    t = t.replaceAll("(?s)```(?:json|JSON)?\\s*", "");
    t = t.replaceAll("(?s)```\\s*", "");
    return t;
  }

  /**
   * Find a '[' position that likely starts a JSON array.
   * We scan and return the first '[' such that the substring from it can be parsed as JSON array
   * after we find a matching closing bracket.
   *
   * This method returns the first '[' and relies on findMatchingBracketEnd + parse check.
   */
  private int findFirstJsonArrayStart(String text) {
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '[') {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns index of the matching ']' for the '[' at startIdx,
   * using bracket depth counting and respecting JSON string quotes/escapes.
   */
  private int findMatchingBracketEnd(String text, int startIdx) {
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

      // not in string
      if (c == '"') {
        inString = true;
        continue;
      }

      if (c == '[') {
        depth++;
      } else if (c == ']') {
        depth--;
        if (depth == 0) return i;
        if (depth < 0) return -1;
      }
    }
    return -1;
  }

  /**
   * Validates ActionSense schema:
   * array of objects with actionId(number), confidence(0..1), isExplained(boolean), explanation(string).
   */
  private boolean isValidActionSenseJson(String s) {
    if (s == null) return false;
    String trimmed = s.trim();
    if (trimmed.isEmpty()) return false;

    JsonNode root;
    try {
      root = MAPPER.readTree(trimmed);
    } catch (Exception e) {
      return false;
    }

    if (!root.isArray()) return false;

    for (JsonNode node : root) {
      if (!node.isObject()) return false;

      JsonNode actionId = node.get("actionId");
      JsonNode confidence = node.get("confidence");
      JsonNode isExplained = node.get("isExplained");
      JsonNode explanation = node.get("explanation");

      if (actionId == null || !actionId.isNumber()) return false;
      if (confidence == null || !confidence.isNumber()) return false;

      double c = confidence.asDouble();
      if (Double.isNaN(c) || c < 0.0 || c > 1.0) return false;

      if (isExplained == null || !isExplained.isBoolean()) return false;
      if (explanation == null || !explanation.isTextual()) return false;
    }

    return true;
  }

  /**
   * Canonicalize JSON output (stable formatting) so cache values are consistent.
   * This also removes any leading/trailing whitespace/prose if we extracted properly.
   */
  private String normalizeJson(String json) {
    try {
      JsonNode node = MAPPER.readTree(json);
      return MAPPER.writeValueAsString(node); // compact canonical JSON
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to normalize JSON", e);
    }
  }
}
