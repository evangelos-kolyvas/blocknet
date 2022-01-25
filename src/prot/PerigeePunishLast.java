/*
 * Created on Jan 14, 2022 by Spyros Voulgaris
 *
 */
package prot;

import peernet.core.Peer;
import peernet.transport.Address;

/**
 * This class extends the {@link Perigee} abstract class, implementing the
 * scoring function that rewards all but the last selected neighbor to deliver
 * a given header by one score point.
 * 
 * For each new block header, each time a selected neighbor delivers it, the
 * selected neighbor that has delivered it just before get a score point.
 * This way, all neighbors get one score point each, except for the last one
 * to deliver that specific block header.
 * 
 * @author spyros
 *
 */
public class PerigeePunishLast extends Perigee
{
  private int prevId = -1;
  private int currentBlockId = -1;

  public PerigeePunishLast(String prefix)
  {
    super(prefix);
  }



  @Override
  protected void hookReceivedHeader(int blockId, long relativeTime, int hops, Address from)
  {
    // Check if we have a new block ID, and if so, reset prevId.
    if (blockId != currentBlockId)
    {
      currentBlockId = blockId;
      prevId = -1;
    }

    // Check if the sender is one of my selected (aka, outgoing) peers.
    Peer upstreamPeer = addr2peer(from);
    if (!outgoingSelections.contains(upstreamPeer))
      return;

    // If some other outgoing peer has previously delivered this block, increase its score.
    if (prevId != -1)
    {
      int score = scoring.getOrDefault(prevId, 0);
      scoring.put(prevId, score+1);
    }

    // And now put this node's ID into prevId, to prepare to get a score point
    // if it's not the last one to deliver the header.
    prevId = (int) upstreamPeer.getID();
  }
}
