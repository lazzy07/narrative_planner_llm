/*
* File name: GenericTreeMap.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-11 00:04:37
// Date modified: 2026-02-11 00:08:43
* ------
*/

package nil.lazzy07.common.search;

import java.util.ArrayList;
import java.util.List;

import edu.uky.cs.nil.sabre.Action;
import edu.uky.cs.nil.sabre.Plan;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import edu.uky.cs.nil.sabre.logic.Assignment;

public interface GenericTreeMap {
  public Plan<Action> getPlan(long node);

  public ArrayList<CompiledAction> getAvailableActions(long node);

  public List<Assignment> getState(long node);

  public long getNextNode(long node, CompiledAction action);

  public double getUtility(long node);
}
