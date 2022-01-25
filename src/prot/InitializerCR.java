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
  private static final String PAR_CDOWNSTREAM = "c_down";
  private static final String PAR_RDOWNSTREAM = "r_down";

  int paramC;  // close peers
  int paramR;  // random peers
  int paramCup;  // how many of the close peers should be upstream
  int paramRup;  // how many of the random peers should be upstream
  int pid;

  public InitializerCR(String prefix)
  {
    paramC = Configuration.getInt(prefix+"."+PAR_C);
    paramR = Configuration.getInt(prefix+"."+PAR_R);
    int paramCdown = Configuration.getInt(prefix+"."+PAR_CDOWNSTREAM, 0);  // defaults to 0
    int paramRdown = Configuration.getInt(prefix+"."+PAR_RDOWNSTREAM, 0);  // defaults to 0
    paramCup = paramC - paramCdown;
    paramRup = paramR - paramRdown;
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
      int upstreamCount = 0;
      for (int j=0; j<paramC+1; j++)
      {
        int closeNeighborID = QuickSelect.ids[j];
        if (closeNeighborID == thisNode.getID())
          continue;

        // Check whether this should be established as an upstream (default) or downstream link
        if (upstreamCount < paramCup)  // upstream
        {
          Peer otherPeer = Network.get(closeNeighborID).getProtocol(pid).myPeer();
          thisLinkable.addNeighbor(otherPeer);
          upstreamCount++;
        }
        else // downstream
        {
          Linkable otherProt = (Linkable) Network.get(closeNeighborID).getProtocol(pid);
          otherProt.addNeighbor(thisPeer);
        }
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
      int upstreamCount = 0;
      do
      {
        int r = CommonState.r.nextInt(Network.size());
        Linkable otherProt = (Linkable) Network.get(r).getProtocol(pid);
        Peer peer = Network.get(r).getProtocol(pid).myPeer();
        if (peer.equals(thisPeer))
          continue;

        // Check whether this should be established as an upstream (default) or downstream link
        if (upstreamCount < paramRup)  // upstream
        {
          if (thisLinkable.contains(peer))
            continue;
          thisLinkable.addNeighbor(peer);
          upstreamCount++;
        }
        else // downstream
        {
          if (otherProt.contains(thisPeer))
            continue;
          otherProt.addNeighbor(thisPeer);
        }

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
