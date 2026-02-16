/*
* File name: Prompt.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-02 23:52:47
// Date modified: 2026-02-16 17:02:08
* ------
*/

package nil.lazzy07.llm.prompt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import edu.uky.cs.nil.sabre.Action;
import edu.uky.cs.nil.sabre.Plan;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import edu.uky.cs.nil.sabre.logic.Assignment;
import nil.lazzy07.domain.converters.DomainConverter;

public class SearchPrompt {
  private static String systemPrompt;
  private static String promptTemplate;
  private static String promptFolder;
  private static String promptVersion;
  private static DomainConverter domainConverter;

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
      This is the final description
      """;

  public static void Init(String promptFolder, String promptVersion, DomainConverter domainConverter) {
    SearchPrompt.promptFolder = promptFolder;
    SearchPrompt.promptVersion = promptVersion;
    SearchPrompt.domainConverter = domainConverter;
  }

  public static String GetSystemPrompt() {
    if (SearchPrompt.systemPrompt == null) {
      SearchPrompt.systemPrompt = ReadSystemPrompt();
    }

    return SearchPrompt.systemPrompt;
  }

  private static String ReadSystemPrompt() {
    File systemPrompt = new File(promptFolder + promptVersion + "/system-prompt.txt");
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
    File promptTemplate = new File(promptFolder + promptVersion + "/prompt-template.txt");
    try {
      return Files.readString(promptTemplate.toPath());
    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }

  private static String planAsStr(Plan<Action> plan) {
    StringBuilder strBuilder = new StringBuilder();

    for (Action action : plan) {
      String actionStr = SearchPrompt.domainConverter.action((CompiledAction) action);
      strBuilder.append(actionStr).append("\n");
    }

    if (strBuilder.isEmpty()) {
      strBuilder.append("This is the initial state of the story, nothing have happened yet.");
    }

    return strBuilder.toString();
  }

  private static String availableActionAsStr(ArrayList<CompiledAction> availableActions) {
    StringBuilder strBuilder = new StringBuilder();

    int i = 1;
    for (CompiledAction action : availableActions) {
      String actionStr = SearchPrompt.domainConverter.action(action);
      strBuilder.append(i).append(") ").append(actionStr).append("\n");
      i++;
    }

    return strBuilder.toString();
  }

  private static String stateToStr(List<Assignment> state) {
    StringBuilder strBuilder = new StringBuilder();

    for (Assignment assignment : state) {
      String stateVal = SearchPrompt.domainConverter.fluent(assignment.fluent, assignment.value);
      strBuilder.append(stateVal);
    }

    return strBuilder.toString();
  }

  public static String GetPrompt(Plan<Action> plan, ArrayList<CompiledAction> availableActions,
      List<Assignment> state) {

    return SearchPrompt.GetPromptTemplate()
        .replace("<general_description>", SearchPrompt.generalDescription)
        .replace("<domain_description>", SearchPrompt.domainConverter.domainDescription)
        .replace("<plan_description>", SearchPrompt.beforePlanDescription)
        .replace("<available_actions_description>", SearchPrompt.allActionDescription)
        .replace("<current_state_description>", SearchPrompt.beforeCurrentStateDescription)
        .replace("<final_description>", SearchPrompt.finalDescription)
        .replace("<current_state>", stateToStr(state)).replace("<plan>", planAsStr(plan))
        .replace("<available_actions>", availableActionAsStr(availableActions));
  }
}
