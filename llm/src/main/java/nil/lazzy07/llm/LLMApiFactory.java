/*
* File name: LLMApi.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-01-25 22:44:36
// Date modified: 2026-02-11 03:32:16
* ------
*/

package nil.lazzy07.llm;

import nil.lazzy07.llm.model.LLMApi;

public class LLMApiFactory {
  public static LLMApi GetLLMApi(String type) {
    switch (type) {
      case "chatgpt-5-mini":
        return null;
      case "llama-8b":
        return null;
      default:
        return null;
    }
  }
}
