/*
* File name: GenericSearchNode.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-02-10 23:49:26
// Date modified: 2026-03-04 01:57:53
* ------
*/

package nil.lazzy07.common.search;

import java.util.List;

public interface GenericSearchNode {
  public long getNodeId();

  public float getConfidence();

  public GenericSearchNode getParentNode();

  public List<GenericSearchNode> getChildNodes();

  public void addChildNode(GenericSearchNode node);
}
