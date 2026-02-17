/*
 * File name: LLMRequest.java
 * Project:
 * Author: Lasantha M Senanayake
 * Date created: 2026-02-17
 * ------
 */

package nil.lazzy07.llm.request;

import java.util.Map;

/**
 * A provider-agnostic request envelope so your planner can call any LLMApi the
 * same way.
 */
public record LLMRequest(
    String systemPrompt,
    String userPrompt,
    Map<String, Object> parameters) {
}
