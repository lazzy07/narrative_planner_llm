/*
* File name: PlannerCore.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-02 22:01:17
// Date modified: 2026-02-04 02:07:00
* ------
*/

package nil.lazzy07.planner.core;

import edu.uky.cs.nil.sabre.Session;
import edu.uky.cs.nil.sabre.comp.CompiledProblem;
import edu.uky.cs.nil.sabre.io.ParseException;
import edu.uky.cs.nil.sabre.prog.ProgressionSearch;
import edu.uky.cs.nil.sabre.ptree.ProgressionTree;
import edu.uky.cs.nil.sabre.ptree.ProgressionTreeSpace;
import nil.lazzy07.planner.config.ConfigFile;
import nil.lazzy07.planner.config.ConfigFile.Search.Cost;
import nil.lazzy07.planner.config.ConfigFile.Search.Type;
import nil.lazzy07.planner.search.SearchSession;
import nil.lazzy07.planner.search.cost.CostType;
import nil.lazzy07.planner.search.cost.CostTypeFactory;
import nil.lazzy07.planner.search.type.SearchType;
import nil.lazzy07.planner.search.type.SearchTypeFactory;
import nil.lazzy07.planner.search.util.ProgressionTreeMap;
import nil.lazzy07.planner.search.util.SearchNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

public class PlannerCore {
  private static final Logger log = LoggerFactory.getLogger(PlannerCore.class);

  private ConfigFile configs;
  private Session session;
  private ProgressionSearch search;
  private CompiledProblem compiledProblem;
  private ProgressionTreeMap progressionTreeMap;

  public PlannerCore(ConfigFile configs) {
    this.configs = configs;
    log.trace("Configurations added to the planner core");

    initSession();
    initProgressionTreeMap();
    initSearchNodeRef();
  }

  public ConfigFile getConfigs() {
    return configs;
  }

  public Session getSession() {
    return session;
  }

  public ProgressionSearch getSearch() {
    return search;
  }

  public CompiledProblem getCompiledProblem() {
    return compiledProblem;
  }

  public ProgressionTreeMap getProgressionTreeMap() {
    return progressionTreeMap;
  }

  public void runSearchSession() {
    Type searchTypeConfigs = this.configs.search().type();
    Cost costTypeConfigs = this.configs.search().cost();

    CostType costType = CostTypeFactory.CreateCostType(costTypeConfigs.type());
    SearchType searchType = SearchTypeFactory.CreateSearchType(searchTypeConfigs.name(), costType);

    SearchSession searchSession = new SearchSession(this.configs.search().plan(), this.progressionTreeMap, searchType);
    searchSession.initSearch();
  }

  private void initSession() {
    this.session = new Session();
    ConfigFile.Domain domain = this.configs.domain();
    try {
      session.setProblem(new File(this.configs.domain().file()));
      this.search = (ProgressionSearch) session.getSearch();
      log.trace("Session created and problem set to: {}", domain.name());
    } catch (IOException e) {
      log.error("Error reading the sabre problem file: {}", domain.file());
      throw new RuntimeException(e);
    } catch (ParseException e) {
      log.error("Error parsing the sabre problem file: {}", domain.file());
      throw new RuntimeException(e);
    }
  }

  private void initSearchNodeRef() {
    SearchNode.SetProgressionTreeMap(this.getProgressionTreeMap());
  }

  private void initProgressionTreeMap() {
    try {
      Field spaceField = ProgressionSearch.class.getDeclaredField("space");
      spaceField.setAccessible(true);

      ProgressionTreeSpace space = (ProgressionTreeSpace) spaceField.get(this.search);
      Field treeField = ProgressionTreeSpace.class.getDeclaredField("tree");
      treeField.setAccessible(true);
      ProgressionTree tree = (ProgressionTree) treeField.get(space);

      this.compiledProblem = this.search.problem;
      this.progressionTreeMap = new ProgressionTreeMap(tree, this.compiledProblem);
      log.trace("Progression treemap created");

    } catch (NoSuchFieldException | IllegalAccessException e) {
      log.error("Error creating the progression tree");
      throw new RuntimeException(e);
    }
  }

}
