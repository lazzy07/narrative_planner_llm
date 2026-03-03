/*
* File name: LLMApi.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-01-25 22:44:36
// Date modified: 2026-03-02 20:49:06
* ------
*/

package nil.lazzy07.llm;

import nil.lazzy07.llm.model.ChatGPT5MiniSelectImproved;
import nil.lazzy07.llm.model.LLAMA8BSelectImproved;
import nil.lazzy07.llm.model.LLMApi;

public class LLMApiFactory {
  public static LLMApi GetLLMApi(String type, boolean useCache, String cacheDirectory, String domain) {
    switch (type) {
      case "chatgpt-5-mini":
        return new ChatGPT5MiniSelectImproved(useCache, cacheDirectory, domain);
      case "llama-8b":
        return new LLAMA8BSelectImproved(useCache, cacheDirectory, domain);
      default:
        return null;
    }
  }
}
