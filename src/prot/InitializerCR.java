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
import peernet.util.RandPermutation;
import util.QuickSelect;



/**
 * Uses latencies in RouterNetwork to determine distances between nodes.
 * Picks for each node <code>c</code> close neighbors and <code>r</code>
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

  int paramC;  // close peers
  int paramR;  // random peers
  int pid;

  public InitializerCR(String prefix)
  {
    paramC = Configuration.getInt(prefix+"."+PAR_C);
    paramR = Configuration.getInt(prefix+"."+PAR_R);
    pid = Configuration.getPid(prefix+"."+PAR_PROTOCOL);
  }



  private int latency(int i, int j)
  {
    return RouterNetwork.getLatency(j % RouterNetwork.getSize(), i % RouterNetwork.getSize());
  }



  private void setCloseLinks()
  {
    if (paramC == 0)
      return;

    int[] distances = new int[Network.size()];

    for (int i=0; i<Network.size(); i++)
    {
      Node thisNode = Network.get(i);
      Linkable thisLinkable = (Linkable) thisNode.getProtocol(pid);
      Peer thisPeer = thisNode.getProtocol(pid).myPeer();

      for (int j=0; j<Network.size(); j++)
        distances[j] = latency(j,i) + latency(i,j);  // round-trip time

      // Find the paramC+1 nodes with lowest latency (+1 because self is one of them)
      QuickSelect.shuffle();
      QuickSelect.quickSelect(distances, paramC+1);

      // Set the close peers
      for (int j=0; j<paramC+1; j++)
      {
        int closeNeighborID = QuickSelect.ids[j];
        // System.out.println(RouterNetwork.getLatency(closeNeighborID % RouterNetwork.getSize(), i % RouterNetwork.getSize()));
        if (closeNeighborID == thisNode.getID())
          continue;
        Peer peer = Network.get(closeNeighborID).getProtocol(pid).myPeer();
        thisLinkable.addNeighbor(peer);
      }
    }
  }



  private void setRandomLinks()
  {
    if (paramR == 0)
      return;

    // Set the random peers
    for (int i=0; i<Network.size(); i++)
    {
      Node thisNode = Network.get(i);
      Linkable thisLinkable = (Linkable) thisNode.getProtocol(pid);
      Peer thisPeer = thisNode.getProtocol(pid).myPeer();

      int j = 0;
      do
      {
        int r = CommonState.r.nextInt(Network.size());
        Peer peer = Network.get(r).getProtocol(pid).myPeer();
        if (peer.equals(thisPeer))
          continue;
        if (thisLinkable.contains(peer))
          continue;
        thisLinkable.addNeighbor(peer);
        j++;
      } while (j<paramR);
    }
  }



  private void setPerfectMatchingRandomLinks()
  {
    if (paramR == 0)
      return;

    RandPermutation rp = new RandPermutation(CommonState.r);

    for (int r=0; r<paramR; r++)
    {
      rp.reset(Network.size());

      // Set the random peers
      for (int i=0; i<Network.size(); i++)
      {
        Node thisNode = Network.get(i);
        Linkable thisLinkable = (Linkable) thisNode.getProtocol(pid);
        Peer thisPeer = thisNode.getProtocol(pid).myPeer();

        int randomId = rp.next();
        Peer peer = Network.get(randomId).getProtocol(pid).myPeer();

        while (peer.equals(thisPeer) || thisLinkable.contains(peer))
        {
          randomId = CommonState.r.nextInt(Network.size());
          peer = Network.get(randomId).getProtocol(pid).myPeer();
        }

        thisLinkable.addNeighbor(peer);
      }
    }
  }



  @Override
  public boolean execute()
  {
    setCloseLinks();
    setRandomLinks();
    //setPerfectMatchingRandomLinks();

    return false;
  }
  
}
