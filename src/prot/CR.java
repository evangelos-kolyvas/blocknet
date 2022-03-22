/*
 * Created on Jan 14, 2022 by Spyros Voulgaris
 *
 */
package prot;

import java.util.ArrayList;

import base.BaseDissemination;
import base.Stats;
import peernet.core.Peer;
import peernet.transport.Address;

/**
 * Executes dissemination based on a static overlay.
 * No scoring, no calibration rounds.
 * Basically this is the class to run the basic CR model.
 * 
 * @author spyros
 *
 */
public class CR extends BaseDissemination
{
  ArrayList<Peer> upstreamPeers;
  //ArrayList<Peer> downstreamPeers;


  //ArrayList<Integer> deliveryTimes;


  public CR(String prefix)
  {
    super(prefix);
    upstreamPeers = new ArrayList<>();
    //downstreamPeers = new ArrayList<>();
  }



  public Object clone()
  {
    CR d = (CR) super.clone();
    d.upstreamPeers = (ArrayList<Peer>) upstreamPeers.clone();
    //d.downstreamPeers = (ArrayList<Peer>) downstreamPeers.clone();
    return d;
  }



  @Override
  public void nextCycle(int schedId)
  {
  }



  @Override
  protected void hookReceivedHeader(int blockId, long relativeTime, int hops, Address from)
  {
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
    return downstreamPeers.size();
  }



  @Override
  public Peer getNeighbor(int i)
  {
    return downstreamPeers.get(i);
  }



  @Override
  public boolean addNeighbor(Peer neighbor)
  {
    if (upstreamPeers.contains(neighbor))
      return false;

    // Add neighbor to my hotPeers
    upstreamPeers.add(neighbor);

    // Add myself to neighbor's subscribers
    getDissProt((int)neighbor.getID()).addDownstreamPeer(myPeer());

    return true;
  }



  @Override
  public boolean contains(Peer neighbor)
  {
    return upstreamPeers.contains(neighbor);
  }



  @Override
  public void onKill()
  {
    // TODO Auto-generated method stub
  }



  public String toString()
  {
    return "Dissemination "+myNode().getID()+" ("+upstreamPeers.size()+" up, "+downstreamPeers.size()+" down)";
  }
}
