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

  /**
   * A nice convenience wrapper for your planner:
   * returns STRICT JSON (the schema you requested) or best-effort repaired JSON.
   *
   * The cache key will include these parameters because you pass them into
   * query().
   */
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

    // LLMApi ensures init() is called before callModel() via ensureInit()

    final String strictJsonContract = "Output MUST be valid JSON and NOTHING else.\n" +
        "Return a JSON array of objects with exactly these keys:\n" +
        "\"actionId\" (number), \"confidence\" (float 0..1), \"isExplained\" (boolean), \"explanation\" (string).\n" +
        "Use double quotes for all keys/strings.\n";

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

    String body;
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

    HttpResponse<String> resp;
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
   * Post-process before caching: validate schema and repair if needed.
   * This guarantees you don't cache invalid junk.
   */
  @Override
  protected String postProcessResponse(String rawResponse,
      String systemPrompt,
      String userPrompt,
      Map<String, Object> parameters) {

    if (isValidActionSenseJson(rawResponse)) {
      return rawResponse.trim();
    }

    String output = rawResponse;

    for (int attempt = 1; attempt <= maxRepairAttempts; attempt++) {
      log.debug("Invalid JSON output. Attempting repair {}/{}", attempt, maxRepairAttempts);

      String repairUserPrompt = "Your previous output was NOT valid according to the required JSON schema.\n" +
          "Fix it now.\n" +
          "Rules:\n" +
          "- Return ONLY valid JSON (no markdown, no commentary).\n" +
          "- Must be a JSON array.\n" +
          "- Each element must include: actionId(number), confidence(float 0..1), isExplained(boolean), explanation(string).\n"
          +
          "- explanation must be one short sentence.\n\n" +
          "Here is the invalid output to repair:\n" + output;

      Map<String, Object> repairParams = new LinkedHashMap<>();
      if (parameters != null) {
        repairParams.putAll(parameters);
      }

      repairParams.put("repairAttempt", attempt);

      // IMPORTANT:
      // This calls the base query() again, which could cache the repaired result
      // under a different key.
      // That's fine; the primary query caches the final returned value.
      output = callModel(systemPrompt, userPrompt + "\n\n" + repairUserPrompt, repairParams);

      if (isValidActionSenseJson(output)) {
        return output.trim();
      }
    }

    // Best effort: return last output (caller can hard-fail if they want)
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

  private boolean isValidActionSenseJson(String s) {
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

    if (!root.isArray())
      return false;

    for (JsonNode node : root) {
      if (!node.isObject())
        return false;

      JsonNode actionId = node.get("actionId");
      JsonNode confidence = node.get("confidence");
      JsonNode isExplained = node.get("isExplained");
      JsonNode explanation = node.get("explanation");

      if (actionId == null || !actionId.isNumber())
        return false;
      if (confidence == null || !confidence.isNumber())
        return false;

      double c = confidence.asDouble();
      if (Double.isNaN(c) || c < 0.0 || c > 1.0)
        return false;

      if (isExplained == null || !isExplained.isBoolean())
        return false;
      if (explanation == null || !explanation.isTextual())
        return false;

      if (explanation.asText().trim().isEmpty())
        return false;
    }

    return true;
  }
}
