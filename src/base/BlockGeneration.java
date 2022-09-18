/*
 * Created on Nov 6, 2020 by Spyros Voulgaris
 *
 */
package base;

import java.util.Random;

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
  long blockCompletionTime = -1;

  // Skip every 'skip' blocks
  int skip;
  int count=0;

  /** 
   *  We are using a local random number generator, so that the same
   *  miners (and in the same order) are picked to generate blocks for
   *  every single experiment. 
   */
  private static Random rng = new Random(0);

  public BlockGeneration(String prefix)
  {
    disseminationPid = Configuration.getPid(prefix + ".protocol");
    blocks = Configuration.getInt(prefix+"."+PAR_BLOCKS);
    skip = Configuration.getInt(prefix + ".skip", -1);
  }


  protected boolean generateBlock()
  {
    BaseDissemination d = null;

    int r;
    do
    {
      // Pick a random node as miner
      r = rng.nextInt(Network.size());

      System.out.print("Mining block "+blockId+" at node "+r+" time "+CommonState.getTime());

      // Get that node's BaseDissemination protocol
      d = (BaseDissemination) Network.get(r).getProtocol(disseminationPid);

      // Make sure that node has at least two downstream peers
    } while (d.downstreamPeers.size() < 2);
//    System.out.print("\r");
    System.out.println();

    Stats.reportMiner(blockId, r);

    // Generate a block on it
    d.generateBlock(blockId++);

    //System.err.print("\r#block "+blockId);

    return false;
  }



  /**
   *  Allow 10 simulation seconds after the last block has been generated,
   *  and then end the simulation.
   */
  @Override
  public boolean execute()
  {
    // End experiment 10000ms after the number of blocks has been reached
    if (blockId == blocks)
    {
      if (blockCompletionTime == -1)
        blockCompletionTime = CommonState.getTime();
      
      if (CommonState.getTime() - blockCompletionTime > 10000)  //XXX ugly: Should be higher than the max anticipated dissemination time
        return true;
      else
        return false;
    }

    // Skip one every 'skip' blocks.
    if (skip > 0 && count++ % skip == 0)
      return false;

    return generateBlock();
  }
}
