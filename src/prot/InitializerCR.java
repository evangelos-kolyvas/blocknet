/*
 * Created on May 13, 2021 by Spyros Voulgaris
 *
 */
package prot;

import peernet.config.Configuration;
import peernet.core.CommonState;
import peernet.core.Control;
import peernet.core.Linkable;
import peernet.core.Network;
import peernet.core.Node;
import peernet.core.Peer;
import peernet.transport.RouterNetwork;
import util.QuickSelect;



/**
 * Uses latencies in RouterNetwork to determine distances between nodes.
 * Picks for each node {@code c} close neighbors and {@code r}
 * random ones.
 * 
 * @author spyros
 *
 */
public class InitializerCR implements Control
{
  private static final String PAR_C = "c";
  private static final String PAR_R = "r";
  private static final String PAR_PROTOCOL = "protocol";
  private static final String PAR_CDOWNSTREAM = "c_down";
  private static final String PAR_RDOWNSTREAM = "r_down";
  private static final String PAR_BIDIRECTIONAL = "undir";

  private int paramC;  // close peers
  private int paramR;  // random peers
  private int paramCup;  // how many of the close peers should be upstream
  private int paramRup;  // how many of the random peers should be upstream
  private int pid;
  private boolean bidirectional;

  

  /**
   * Constructor.
   * 
   * @param prefix
   */
  public InitializerCR(String prefix)
  {
    paramC = Configuration.getInt(prefix+"."+PAR_C);
    paramR = Configuration.getInt(prefix+"."+PAR_R);
    int paramCdown = Configuration.getInt(prefix+"."+PAR_CDOWNSTREAM, 0);  // defaults to 0
    int paramRdown = Configuration.getInt(prefix+"."+PAR_RDOWNSTREAM, 0);  // defaults to 0
    bidirectional = Configuration.getBoolean(prefix+"."+PAR_BIDIRECTIONAL);
    paramCup = paramC - paramCdown;
    paramRup = paramR - paramRdown;
    pid = Configuration.getPid(prefix+"."+PAR_PROTOCOL);
  }



  /**
   * Convenience function, returning the one-way latency from node {@code i} to node {@code j}.
   * 
   * @param i
   * @param j
   * @return
   */
  private int latency(int i, int j)
  {
    return RouterNetwork.getLatency(i % RouterNetwork.getSize(), j % RouterNetwork.getSize());
  }



  /**
   * Visits all nodes of the network, and equips each node with the required
   * number of close neighbors, that is, neighbors having the lowest RTT to it.
   * 
   * @param bidirectional
   */
  private void setCloseLinks(boolean bidirectional)
  {
    if (paramC == 0)
      return;

    int[] distances = new int[Network.size()];

    // Go through each and every node
    for (int i=0; i<Network.size(); i++)
    {
      // Get hold of this node's Node, Peer, and Linkable
      Node thisNode = Network.get(i);
      Peer thisPeer = thisNode.getProtocol(pid).myPeer();
      Linkable thisLinkable = (Linkable) thisNode.getProtocol(pid);

      // Compute RTT distances to all other nodes
      for (int j=0; j<Network.size(); j++)
        distances[j] = latency(i,j) + latency(j,i);  // round-trip time

      /*
       * Use QuickSelect to efficiently find the 'topK' nodes with lowest latency.
       * 
       * For unidirectional, that's paramC+1: We need paramC, but we add +1
       * because self will float as the closest one.
       * 
       * For bidirectional, selecting the paramC closest one is not enough, as
       * some of these nodes may have already selected us as their closest,
       * therefore we will have to skip them and look for additional, a bit less
       * close ones. Putting the paramC*5 in a pool to pick nodes from is a
       * wild guess.
       */
      int topK = bidirectional ? paramC*5 : paramC+1;
      QuickSelect.quickSelect(distances, topK);
      if (bidirectional)
      {
        //dump(distances, topK);
        QuickSelect.sortFirstItems(topK);
        //dump(distances, topK);
      }


      // Set my closest peers to be my neighbors
      int upstreamCount = 0;    // #upstream neighbors set so far
      int downstreamCount = 0;  // #downstream neighbors set so far
      int j=0;   // iterator to go through the first few entries of ids[]

      while (upstreamCount+downstreamCount < paramC)
      {
        assert j < topK: "InitializerCR: Ran out of close neighbors -- Increase topK value (currently "+topK+")";
          
        int closeNeighborID = QuickSelect.ids[j++];
        if (closeNeighborID == thisNode.getID())
          continue;

        // Get hold of other node's Node, Peer, and Linkable
        Node otherNode = Network.get(closeNeighborID); 
        Peer otherPeer = otherNode.getProtocol(pid).myPeer();
        Linkable otherLinkable = (Linkable) otherNode.getProtocol(pid);

        if (bidirectional)
        {
          if (setBidirectionalLink(thisLinkable, thisPeer, otherLinkable, otherPeer))
            upstreamCount++;
        }
        else
        {
          // Check whether this should be established as an upstream (default)
          // or downstream link
          if (upstreamCount < paramCup) // upstream
          {
            assert !thisLinkable.contains(otherPeer): "How is it possible to already have this neighbor? Inspect for bug!";
            thisLinkable.addNeighbor(otherPeer);
            upstreamCount++;
          }
          else // downstream
          {
            if (otherLinkable.contains(thisPeer))
              continue;
            otherLinkable.addNeighbor(thisPeer);
            downstreamCount++;
          }
        }
      }
    }
  }



  /**
   * Visits all nodes of the network, and equips each node with the required
   * number of randomly picked neighbors.
   * 
   * Note that if a node A selects node B at random, but finds out it is
   * already connected to B (e.g., in terms of close neighbors, or because
   * we assume bidirectional links and B already selected A at random),
   * A skips B and continues picking neighbors at random.
   * 
   * @param bidirectional
   */
  private void setRandomLinks(boolean bidirectional)
  {
    if (paramR == 0)
      return;

    // Go through each and every node
    for (int i=0; i<Network.size(); i++)
    {
      // Get hold of this node's Node, Peer, and Linkable
      Node thisNode = Network.get(i);
      Peer thisPeer = thisNode.getProtocol(pid).myPeer();
      Linkable thisLinkable = (Linkable) thisNode.getProtocol(pid);


      // Set random peers to be my neighbors
      int upstreamCount = 0;    // #upstream neighbors set so far
      int downstreamCount = 0;  // #downstream neighbors set so far

      while (upstreamCount+downstreamCount < paramR)
      {
        int r = CommonState.r.nextInt(Network.size());

        // Get hold of other node's Node, Peer, and Linkable
        Node otherNode = Network.get(r);
        Peer otherPeer = otherNode.getProtocol(pid).myPeer();
        Linkable otherLinkable = (Linkable) otherNode.getProtocol(pid);

        if (otherPeer.equals(thisPeer))
          continue;

        if (bidirectional)
        {
          if (setBidirectionalLink(thisLinkable, thisPeer, otherLinkable, otherPeer))
            upstreamCount++;
        }
        else
        {
          // Check whether this should be established as an upstream (default)
          // or downstream link
          if (upstreamCount < paramRup) // upstream
          {
            if (thisLinkable.contains(otherPeer))
              continue;
            thisLinkable.addNeighbor(otherPeer);
            upstreamCount++;
          }
          else // downstream
          {
            if (otherLinkable.contains(thisPeer))
              continue;
            otherLinkable.addNeighbor(thisPeer);
            downstreamCount++;
          }
        }
      }
    }
  }



  /**
   * Sets a bidirectional link between {@code peerA} and {@code peerB}, if not already there.
   * 
   * It first checks whether the two peers refer to the same node.
   * Then it checks whether {@code peerA} already has {@code peerB} as its neighbor
   * (it is assumed that all links are bidirectional, therefore there is no need to also check
   * whether {@code peerA} is a neighbor of {@code peerB}).
   * If both checks fail, it establishes two links, one in each direction.
   * 
   * @param protA
   * @param peerA
   * @param protB
   * @param peerB
   * @return {@code True}, if a link was set; {@code False}, if no link was set.
   */
  private boolean setBidirectionalLink(Linkable protA, Peer peerA, Linkable protB, Peer peerB)
  {
    if (peerA.equals(peerB))
      return false;

    if (protA.contains(peerB))
      return false;

    protA.addNeighbor(peerB);
    protB.addNeighbor(peerA);
    return true;
  }



  /**
   * Auxiliary function used for debugging.
   * 
   * Dumps the first {@code topK} IDs of the {@code ids} array, and their
   * respective values from the {@distances} array.
   * 
   * @param distances
   * @param topK
   */
  private void dump(int[] distances, int topK)
  {
    for (int i=0; i<topK; i++)
    {
      int index = QuickSelect.ids[i];
      System.out.println(i+"  "+index+"  "+distances[index]);
    }
    System.out.println();
  }



  @Override
  public boolean execute()
  {
    setCloseLinks(bidirectional);
    setRandomLinks(bidirectional);

    return false;
  }
}
