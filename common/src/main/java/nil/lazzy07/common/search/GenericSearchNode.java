/*
* File name: GenericSearchNode.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-10 23:49:26
// Date modified: 2026-03-11 20:52:27
* ------
*/

package nil.lazzy07.common.search;

import java.util.List;

import edu.uky.cs.nil.sabre.comp.CompiledAction;

public interface GenericSearchNode {
  public long getNodeId();

  public float getConfidence();

  public GenericSearchNode getParentNode();

  public List<GenericSearchNode> getChildNodes();

  public void addChildNode(GenericSearchNode node);

  public String getExplaination();

  public CompiledAction getCurrentAction();
}
