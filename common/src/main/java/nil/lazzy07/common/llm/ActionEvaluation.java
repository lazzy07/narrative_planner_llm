/*
* File name: ActionEvaluation.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-17 15:49:07
// Date modified: 2026-02-17 15:49:26
* ------
*/

package nil.lazzy07.common.llm;

public record ActionEvaluation(
    int actionId,
    double confidence,
    boolean isExplained,
    String explanation) {
}
