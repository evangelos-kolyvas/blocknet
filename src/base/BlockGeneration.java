/*
 * Created on Nov 6, 2020 by Spyros Voulgaris
 *
 */
package base;

import peernet.config.Configuration;
import peernet.core.CommonState;
import peernet.core.Control;
import peernet.core.Network;

public class BlockGeneration implements Control
{
  private static final String PAR_BLOCKS = "blocks";

  int disseminationPid;
  int blockId = 0;
  int blocks;

  public BlockGeneration(String prefix)
  {
    disseminationPid = Configuration.getPid(prefix + ".protocol");
    blocks = Configuration.getInt(prefix+"."+PAR_BLOCKS);
  }



  @Override
  public boolean execute()
  {
    // End experiment when #blocks has been reached
    if (blockId == blocks)
      return true;

    BaseDissemination d = null;

    do
    {
      // Pick a random node as miner
      int r = CommonState.r.nextInt(Network.size());

      r = blockId;
      System.out.print("Mining block at node "+r);

      // Get that node's BaseDissemination protocol
      d = (BaseDissemination) Network.get(r).getProtocol(disseminationPid);

      // Make sure that node has at least two downstream peers
    } while (d.downstreamPeers.size() < 2);
    System.out.println();

    // Generate a block on it
    d.generateBlock(blockId++);

    //System.err.print("\r#block "+blockId);

    return false;
  }
}