/*
* File name: LLMApi.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-11 03:09:57
// Date modified: 2026-02-11 03:23:01
* ------
*/

package nil.lazzy07.llm.model;

public abstract class LLMApi {
  private String type;

  public LLMApi(String type) {
    this.type = type;
  }

  public void init() {

  }

  public String getType() {
    return type;
  }
}
