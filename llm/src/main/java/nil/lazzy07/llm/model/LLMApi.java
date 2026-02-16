/*
* File name: LLMApi.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-11 03:09:57
// Date modified: 2026-02-15 17:08:49
* ------
*/

package nil.lazzy07.llm.model;

import nil.lazzy07.domain.converters.DomainConverter;

public abstract class LLMApi {
  private String type;
  private DomainConverter domainConverter;

  public LLMApi(String type) {
    this.type = type;
  }

  public void init(DomainConverter domainConverter) {
    this.domainConverter = domainConverter;
  }

  public String getType() {
    return type;
  }
}
