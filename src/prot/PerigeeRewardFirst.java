/*
 * Created on Jan 24, 2022 by Spyros Voulgaris
 *
 */
package prot;

import peernet.core.CommonState;
import peernet.core.Peer;
import peernet.transport.Address;


/**
 * This class extends the {@link PerigeeSingle} abstract class, implementing the
 * scoring function that rewards the first selected neighbor to deliver a
 * header by DT, where DT is the number of milliseconds after which another
 * selected neighbor delivers the same header too.
 * 
 * That is, the faster the first node is compared to the second one, the
 * more credits it gets. Neighbors delivering the block header second, third,
 * and so on, get no credit for that block.
 * 
 * @author spyros
 *
 */
public class PerigeeRewardFirst extends PerigeeSingle
{
  public PerigeeRewardFirst(String prefix)
  {
    super(prefix);
  }

  private int currentBlockId = -1;
  private int firstDeliveryId = -1;
  private long firstDeliveryTime = 0;
  private boolean alreadyRewarded = false;



  @Override
  protected void hookReceivedHeader(int blockId, long relativeTime, int hops, Address from)
  {
    // Check if we have a new block ID, and if so, reset firstId.
    if (blockId != currentBlockId)
    {
      currentBlockId = blockId;
      firstDeliveryId = -1;
      alreadyRewarded = false;
    }

    // Check if the sender is one of my selected (aka, outgoing) peers.
    Peer upstreamPeer = addr2peer(from);
    if (!outgoingSelections.contains(upstreamPeer))
      return;

    // If this is the first selected peer delivering the block, set firstDeliveryId and firstDeliveryTime
    if (firstDeliveryId == -1)
    {
      firstDeliveryId = (int) upstreamPeer.getID();
      firstDeliveryTime = CommonState.getTime();
    }
    else if (!alreadyRewarded)  // If some other outgoing peer was the first one to deliver this block, increase that peer's score.
    {
      int score = scoring.getOrDefault(firstDeliveryId, 0);
      score += CommonState.getTime() - firstDeliveryTime;
      scoring.put(firstDeliveryId, score);
      alreadyRewarded = true;  // to make sure the first peer doesn't receive the reward more than once.
    }
  }
}
