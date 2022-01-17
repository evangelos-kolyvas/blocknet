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

  // Skip every 'skip' blocks
  int skip;
  int count=0;

  public BlockGeneration(String prefix)
  {
    disseminationPid = Configuration.getPid(prefix + ".protocol");
    blocks = Configuration.getInt(prefix+"."+PAR_BLOCKS);
    skip = Configuration.getInt(prefix + ".skip", -1);
  }



  @Override
  public boolean execute()
  {
    // End experiment when #blocks has been reached
    if (blockId == blocks)
      return true;

    // Skip one every 'skip' blocks.
    if (skip > 0 && count++ % skip == 0)
      return false;

    BaseDissemination d = null;

    do
    {
      // Pick a random node as miner
      int r = CommonState.r.nextInt(Network.size());

      System.out.print("Mining block "+blockId+" at node "+r+" time "+CommonState.getTime());

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