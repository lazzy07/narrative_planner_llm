/*
* File name: CostTypeFactory.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-04 01:30:19
// Date modified: 2026-03-04 02:00:33
* ------
*/

package nil.lazzy07.planner.search.cost;

public class CostTypeFactory {
  public static CostType CreateCostType(String name) {
    switch (name) {
      case "action-cost":
        return new ActionCost();
      case "plan-cost":
        return new PlanCost();
      case "plan-length":
        return new PlanLengthCost();
      default:
        return null;
    }
  }
}
