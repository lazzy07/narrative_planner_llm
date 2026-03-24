/*
* File name: LLMApi.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-11 03:09:57
// Date modified: 2026-03-23 11:31:37
* ------
*/
package nil.lazzy07.llm.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import edu.uky.cs.nil.sabre.comp.CompiledAction;
import nil.lazzy07.llm.request.LLMRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class LLMApi {
  private static final Logger log = LoggerFactory.getLogger(LLMApi.class);

  /**
   * IMPORTANT: Stable ordering is critical for deterministic cache keys.
   */
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

  private final String type;
  private final boolean useCache;
  private final String cacheDirectory;
  private final String domain;

  private volatile boolean initialized = false;

  public LLMApi(String type, boolean useCache, String cacheDirectory, String domain) {
    this.type = type;
    this.useCache = useCache;
    this.cacheDirectory = cacheDirectory;
    this.domain = domain;

    log.info("LLM Api created: Type: {} use cache?: {} cache path: {} domain: {}",
        this.type, this.useCache, this.cacheDirectory, this.domain);
  }

  /**
   * Provider-specific initialization (HTTP client, auth, connection pool, etc).
   * Called lazily by ensureInit().
   */
  public abstract void init();

  /**
   * Ensure init() is called exactly once, even if the planner uses the API from
   * multiple places.
   */
  protected synchronized void ensureInit() {
    if (!initialized) {
      init();
      initialized = true;
    }
  }

  public String query(LLMRequest request) {
    return query(request.systemPrompt(), request.userPrompt(), request.parameters());
  }

  // For random api
  public String query(ArrayList<CompiledAction> actions) {
    return null;
  }

  public String query(String systemPrompt, String userPrompt) {
    return query(systemPrompt, userPrompt, null);
  }

  public String query(String systemPrompt,
      String userPrompt,
      Map<String, Object> parameters) {

    // Cache key MUST include provider identity and parameters in a deterministic
    // form
    String payload = buildPayload(systemPrompt, userPrompt, parameters);
    String cacheKey = hash(payload);

    if (useCache) {
      String cached = readFromCache(cacheKey);
      if (cached != null) {
        log.debug("Cache hit for key: {}", cacheKey);
        return cached;
      }
    }

    // Only init when we actually need to hit the model
    ensureInit();

    log.debug("Cache miss. Querying model...");
    String raw = callModel(systemPrompt, userPrompt, parameters);

    // Give subclasses a chance to extract/validate/repair, and ONLY cache the final
    // result.
    String response = postProcessResponse(raw, systemPrompt, userPrompt, parameters);

    if (useCache) {
      writeToCache(cacheKey, response);
    }

    return response;
  }

  // =========================
  // SUBCLASS EXTENSION HOOKS
  // =========================

  /**
   * Subclasses should implement the actual model call and return the raw
   * response.
   * Prefer returning the model's "final content" (not a full envelope) if
   * possible.
   */
  protected abstract String callModel(String systemPrompt,
      String userPrompt,
      Map<String, Object> parameters);

  /**
   * Optional hook: subclasses can normalize/extract content, validate JSON,
   * retry/repair, etc.
   * Default: no-op.
   *
   * IMPORTANT: postProcessResponse runs BEFORE caching so you don't cache invalid
   * junk.
   */
  protected String postProcessResponse(String rawResponse,
      String systemPrompt,
      String userPrompt,
      Map<String, Object> parameters) {
    return rawResponse;
  }

  // =========================
  // CACHE KEY BUILDING
  // =========================

  protected String buildPayload(String systemPrompt,
      String userPrompt,
      Map<String, Object> parameters) {

    StringBuilder sb = new StringBuilder();

    // Include provider identity & domain so caches never collide across different
    // APIs/domains
    sb.append("API_TYPE:\n").append(type).append("\n\n");
    sb.append("DOMAIN:\n").append(domain).append("\n\n");

    sb.append("SYSTEM:\n").append(systemPrompt).append("\n\n");
    sb.append("USER:\n").append(userPrompt).append("\n\n");

    if (parameters != null) {
      sb.append("PARAMETERS_JSON:\n");
      try {
        // stable serialization -> stable cache keys
        sb.append(MAPPER.writeValueAsString(parameters));
      } catch (Exception e) {
        log.warn("Failed to serialize parameters to JSON; falling back to toString()", e);
        sb.append(parameters.toString());
      }
    }

    return sb.toString();
  }

  protected String hash(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }

  // =========================
  // CACHE IO
  // =========================

  protected String readFromCache(String key) {
    Path path = getCachePath(key);

    if (!Files.exists(path)) {
      return null;
    }

    try {
      return Files.readString(path);
    } catch (IOException e) {
      log.warn("Failed reading cache file {}", path, e);
      return null;
    }
  }

  protected void writeToCache(String key, String response) {
    Path path = getCachePath(key);

    try {
      Files.createDirectories(path.getParent());
      Files.writeString(path, response, StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException e) {
      log.warn("Failed writing cache file {}", path, e);
    }
  }

  protected Path getCachePath(String key) {
    return Paths.get(cacheDirectory, domain, key + ".json");
  }

  // =========================
  // GETTERS
  // =========================

  public String getCacheDirectory() {
    return this.cacheDirectory;
  }

  public String getDomain() {
    return this.domain;
  }

  public boolean getUseCache() {
    return this.useCache;
  }

  public String getType() {
    return type;
  }
}
