/*
* File name: LLMApi.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-01-25 22:44:36
// Date modified: 2026-03-09 16:30:58
* ------
*/

package nil.lazzy07.llm;

import nil.lazzy07.llm.model.ChatGPTMiniJson;
import nil.lazzy07.llm.model.LLAMA8BJson;
import nil.lazzy07.llm.model.LLMApi;

public class LLMApiFactory {
  public static LLMApi GetLLMApi(String type, boolean useCache, String cacheDirectory, String domain) {
    switch (type) {
      case "chatgpt-5-mini":
        return new ChatGPTMiniJson(useCache, cacheDirectory, domain);
      case "llama-8b":
        return new LLAMA8BJson(useCache, cacheDirectory, domain);
      default:
        return null;
    }
  }
}
