/*
* File name: Prompt.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-02 23:52:47
// Date modified: 2026-02-11 02:53:43
* ------
*/

package nil.lazzy07.llm.prompt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import nil.lazzy07.common.search.GenericSearchNode;
import nil.lazzy07.common.search.GenericTreeMap;

public class SearchPrompt {
  private static String systemPrompt;
  private static String promptTemplate;
  private static String promptFolder;

  public static final String generalDescription = """
      I will describe the setting of a story and give you a list of actions characters can take next. Your job is to tell me which of the actions makes sense based on what the characters want.
      """;

  public static final String beforePlanDescription = """
      These are the events that have happened so far in the story:
      """;

  public static final String beforeCurrentStateDescription = """
      This is the current situation after those events:
      """;

  public static final String allActionDescription = """
      These are all of the actions that could possibly happen next in the story:
      """;

  public static final String finalDescription = """
      """;

  public static String GetSystemPrompt() {
    if (SearchPrompt.systemPrompt == null) {
      SearchPrompt.systemPrompt = ReadSystemPrompt();
    }

    return SearchPrompt.systemPrompt;
  }

  private static String ReadSystemPrompt() {
    File systemPrompt = new File(promptFolder + "system-prompt.txt");
    try {
      return Files.readString(systemPrompt.toPath());
    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }

  public static String GetPromptTemplate() {
    if (SearchPrompt.promptTemplate == null) {
      SearchPrompt.promptTemplate = ReadPromptTemplate();
    }

    return SearchPrompt.promptTemplate;
  }

  private static String ReadPromptTemplate() {
    File promptTemplate = new File(promptFolder + "prompt-template.txt");
    try {
      return Files.readString(promptTemplate.toPath());
    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }

  public static String GetPrompt(GenericSearchNode genericSearchNode) {
    return SearchPrompt.promptTemplate
        .replace("<general_description", SearchPrompt.generalDescription)
        .replace("<plan_description>", SearchPrompt.beforePlanDescription)
        .replace("<available_actions_description>", SearchPrompt.allActionDescription)
        .replace("<final_description>", SearchPrompt.finalDescription);
  }
}
