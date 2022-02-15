/*
 * Created on Jan 14, 2022 by Spyros Voulgaris
 *
 */
package prot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
public abstract class Perigee extends BaseDissemination
{
  private static final String PAR_OUTGOING = "outgoing";
  private static final String PAR_INCOMING = "incoming";
  private static final String PAR_REPLACE = "weakest_links";

  ArrayList<Peer> outgoingSelections;    // Peers selected by me
  ArrayList<Peer> incomingSelections;    // Peers that selected me

  static protected int numIncoming;
  static protected int numOutgoing;
  static protected int weakestLinks;

  protected HashMap<Address, Integer> peerMapping;



  public Perigee(String prefix)
  {
    super(prefix);

    // Instance fields
    outgoingSelections = new ArrayList<>();
    incomingSelections = new ArrayList<>();
    peerMapping = new HashMap<>();

    // Static fields
    numIncoming = Configuration.getInt(prefix + "." + PAR_INCOMING);
    numOutgoing = Configuration.getInt(prefix + "." + PAR_OUTGOING);
    weakestLinks = Configuration.getInt(prefix + "." + PAR_REPLACE);

    assert weakestLinks <= numOutgoing: PAR_REPLACE + " cannot be higher than "+PAR_OUTGOING;
  }



  public Object clone()
  {
    Perigee d = (Perigee) super.clone();
    d.outgoingSelections = (ArrayList<Peer>) outgoingSelections.clone();
    d.incomingSelections = (ArrayList<Peer>) incomingSelections.clone();
    d.peerMapping = (HashMap<Address, Integer>) peerMapping.clone();
    return d;
  }



  @Override
  public void nextCycle(int schedId)
  {
    // Models the calibration rounds
    calibrate();
  }



  protected abstract void calibrate();


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


  protected Peer id2peer(int id)
  {
    assert(Engine.getType() == Type.SIM);
    Node node = Network.get(id);
    Protocol prot = node.getProtocol(pid);
    Peer peer = prot.myPeer();
    return peer;
  }



  /**
   * Given an address, it returns the index of that peer in 'outgoingSelections',
   * if there, or null, if not there.
   * 
   * @param addr
   * @return
   */
  protected int addr2index(Address addr)
  {
    Integer index = peerMapping.get(addr);
    return index==null ? -1 : index;
  }



  protected Peer addr2peer(Address addr)
  {
    assert(Engine.getType() == Type.SIM);
    Node node = ((AddressSim) addr).node;
    Protocol prot = node.getProtocol(pid);
    Peer peer = prot.myPeer();
    return peer;
  }



  protected Protocol peer2prot(Peer peer)
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
