/*
 * Created on Jan 14, 2022 by Spyros Voulgaris
 *
 */
package prot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import base.BaseDissemination;
import base.Stats;
import peernet.config.Configuration;
import peernet.core.CommonState;
import peernet.core.Engine;
import peernet.core.Engine.Type;
import peernet.core.Network;
import peernet.core.Node;
import peernet.core.Peer;
import peernet.core.Protocol;
import peernet.transport.Address;
import peernet.transport.AddressSim;

/**
 * Scores peers, and periodically calibrates neighbors.
 * Basically this is the class to run the Perigee model.
 * 
 * @author spyros
 *
 */
public class Perigee extends BaseDissemination
{
  private static final String PAR_OUTGOING = "outgoing";
  private static final String PAR_INCOMING = "incoming";

  ArrayList<Peer> outgoingSelections;    // Peers selected by me
  ArrayList<Peer> incomingSelections;    // Peers that selected me

  ArrayList<Integer> deliveryTimes;

  HashMap<Integer, Integer> scoring = new HashMap<Integer, Integer>();

  static private int numIncoming;
  static private int numOutgoing;
  int prevId = -1;
  int currentBlockId = -1;

  public Perigee(String prefix)
  {
    super(prefix);
    outgoingSelections = new ArrayList<>();
    incomingSelections = new ArrayList<>();
    numIncoming = Configuration.getInt(prefix + "." + PAR_INCOMING);
    numOutgoing = Configuration.getInt(prefix + "." + PAR_OUTGOING);
  }



  public Object clone()
  {
    Perigee d = (Perigee) super.clone();
    d.outgoingSelections = (ArrayList<Peer>) outgoingSelections.clone();
    d.incomingSelections = (ArrayList<Peer>) incomingSelections.clone();
    d.scoring = (HashMap<Integer, Integer>) scoring.clone();
    return d;
  }



  public void calibrate()
  {
    // Remove weakest link, if scores are in place
    if (scoring.size() > 0)
    {
      int minScore = Integer.MAX_VALUE;
      int minId = -1;
      
      for (Map.Entry<Integer,Integer> entry: scoring.entrySet())
      {
        int id = entry.getKey();
        int score = entry.getValue();
        if (score<minScore)
        {
          minScore=score;
          minId=id;
        }
      }
      Peer minPeer = id2peer(minId);
      
      // remove this peer from me
      outgoingSelections.remove(minPeer);
      removeDownstreamPeer(minPeer);

      // remove me from other peer
      Perigee minProt = (Perigee) peer2prot(minPeer);
      minProt.incomingSelections.remove(myPeer());
      minProt.removeDownstreamPeer(myPeer());

      // Reset scoring to assess the quality of the current outgoing peers
      scoring.clear();
    }

    // Fill in all missing links
    while (outgoingSelections.size() < numOutgoing)
    {
      // pick random neighbor
      int i = CommonState.r.nextInt(Network.size());
      Peer newPeer = id2peer(i);
   
      // check if I randomly picked myself, and skip me! :-)
      if (newPeer.equals(myPeer()))
        continue;

      // check if I already have this peer (either as outgoing or incoming)
      if (contains(newPeer))
        continue;

      // else, check if the other peer has available incoming slots
      Perigee newProt = (Perigee) peer2prot(newPeer);
      if (newProt.incomingSelections.size() >= numIncoming)
        continue;

      // if all is ok, add that node to me
      outgoingSelections.add(newPeer);
      addDownstreamPeer(newPeer);

      // and add myself to that node!
      newProt.incomingSelections.add(myPeer());
      newProt.addDownstreamPeer(myPeer());
    }
  }



  @Override
  public void nextCycle(int schedId)
  {
    // Models the calibration rounds
    calibrate();
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



  @Override
  protected void hookReceivedBody(int blockId, long relativeTime, int hops)
  {
    Stats.reportDelivery(blockId, relativeTime, hops);
    //System.out.println(relativeTime+"\t"+msg.replyTo+" -> "+myNode().getID());
  }



  @Override
  public int degree()
  {
    return incomingSelections.size() + outgoingSelections.size();
  }



  @Override
  public Peer getNeighbor(int i)
  {
    return incomingSelections.get(i);
  }


  private Peer id2peer(int id)
  {
    assert(Engine.getType() == Type.SIM);
    Node node = Network.get(id);
    Protocol prot = node.getProtocol(pid);
    Peer peer = prot.myPeer();
    return peer;
  }
  
  private Peer addr2peer(Address addr)
  {
    assert(Engine.getType() == Type.SIM);
    Node node = ((AddressSim) addr).node;
    Protocol prot = node.getProtocol(pid);
    Peer peer = prot.myPeer();
    return peer;
  }

  private Protocol peer2prot(Peer peer)
  {
    assert(Engine.getType() == Type.SIM);
    AddressSim addr = (AddressSim) peer.address;
    Node node = addr.node;
    Protocol prot = node.getProtocol(pid);
    return prot;
  }
  

  @Override
  public boolean addNeighbor(Peer neighbor)
  {
    // Check if we are already connected
    if (contains(neighbor))
      return false;

    // Check if the other peer has available slots
    Perigee other = (Perigee) peer2prot(neighbor);
    if (other.incomingSelections.size() >= numIncoming)
      return false;

    // Record my outgoing and their incoming links
    outgoingSelections.add(neighbor);
    other.incomingSelections.add(myPeer());

    // Create bidirectional link in the lower layer
    addDownstreamPeer(neighbor);
    other.addDownstreamPeer(myPeer());

    return true;
  }



  @Override
  public boolean contains(Peer neighbor)
  {
    return outgoingSelections.contains(neighbor) || incomingSelections.contains(neighbor);
  }



  @Override
  public void onKill()
  {
    // TODO Auto-generated method stub
  }



  public String toString()
  {
    return "Dissemination "+myNode().getID()+" ("+outgoingSelections.size()+" out, "+incomingSelections.size()+" in)";
  }

}
