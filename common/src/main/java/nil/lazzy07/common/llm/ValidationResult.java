/*
* File name: ValidationResult.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-11 03:33:51
// Date modified: 2026-02-11 03:34:47
* ------
*/

package nil.lazzy07.common.llm;

public record ValidationResult(boolean ok, String normalizedJson, String errorMessage) {

}
