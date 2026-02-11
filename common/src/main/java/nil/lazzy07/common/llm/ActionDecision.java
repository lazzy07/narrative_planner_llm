/*
* File name: ActionDecision.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-11 03:35:55
// Date modified: 2026-02-11 03:37:14
* ------
*/

package nil.lazzy07.common.llm;

public record ActionDecision(int actionID, double confidence, boolean shouldSelect, String reason) {
}
