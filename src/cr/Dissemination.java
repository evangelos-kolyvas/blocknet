/*
 * Created on May 13, 2021 by Spyros Voulgaris
 *
 */
package cr;

import java.util.ArrayList;
import java.util.HashSet;

import peernet.config.Configuration;
import peernet.core.CommonState;
import peernet.core.Linkable;
import peernet.core.Network;
import peernet.core.Peer;
import peernet.core.Protocol;
import peernet.transport.Address;



public class Dissemination extends Protocol implements Linkable
{
  private static final String PAR_HEADER_PROCESSING = "headerProcessing";
  private static final String PAR_BODY_PROCESSING = "bodyProcessing";
  private static final String PAR_EXTRAROUNDTRIPS = "extra_tcp_trips";

  static int cycle;
  static int pid;

  static int headerProcessing;
  static int bodyProcessing;

  HashSet<Integer> receivedHeaders;
  HashSet<Integer> receivedBodies;

  ArrayList<Peer> upstreamPeers;
  ArrayList<Peer> downstreamPeers;
//  ArrayList<Integer> deliveryTimes;

  enum MSGType
  {
    MINE_BLOCK,
    SEND_HEADER,
    REQ_BODY,
    SEND_BODY,
    PROCESSING_HEADER,
    PROCESSING_BODY
  }

  class Message
  {
    int blockId;
    Address replyTo;
    MSGType type;
    int hops;

    public String toString()
    {
//      return "Block "+blockId+" time "+time+" miner "+miner;
      return "<"+type+","+blockId+","+replyTo+","+hops+">";
    }
  }


  

  public Dissemination(String prefix)
  {
    super(prefix);

    cycle = Configuration.getInt("CYCLE");
    headerProcessing = Configuration.getInt(prefix+"."+PAR_HEADER_PROCESSING);
    bodyProcessing = Configuration.getInt(prefix+"."+PAR_BODY_PROCESSING);

    int extra_round_trips = Configuration.getInt(prefix+"."+PAR_EXTRAROUNDTRIPS);
    TransportDeltaQ.setBodyExtraRoundTrips(extra_round_trips);

    pid = myPid();
  }



  public Object clone()
  {
    Dissemination d = (Dissemination) super.clone();

    d.upstreamPeers = new ArrayList<>(10);
    d.downstreamPeers = new ArrayList<>(10);

    d.receivedHeaders = new HashSet<>();
    d.receivedBodies = new HashSet<>();

    return d;
  }



  @Override
  public void nextCycle(int schedId)
  {
    // TODO Auto-generated method stub
  }



  @Override
  public void processEvent(Address src, Object event)
  {
    Message msg = (Message)event;

    //System.err.println(CommonState.getTime()+": "+src+"->"+myNode().getID()+", "+msg);

    switch (msg.type)
    {
      case SEND_HEADER:
        if (!receivedHeaders.contains(msg.blockId))
        {
          receivedHeaders.add(msg.blockId);  // Mark that I received this header
          Message m = new Message();
          m.type = MSGType.PROCESSING_HEADER;
          m.blockId = msg.blockId;
          m.replyTo = src;
          m.hops = msg.hops;  // internal event, no new hop
          schedule(headerProcessing, m);
        }
        break;

      case PROCESSING_HEADER:
      {
        Message m = new Message();
        m.type = MSGType.REQ_BODY;
        m.blockId = msg.blockId;
        m.replyTo = null;
        m.hops = msg.hops;  // sending back to sender, no new hop

        TransportDeltaQ.setBody(false);  // Going to send request for body (==> SMALL)
        send(msg.replyTo, myPid(), m);
        break;
      }

      case REQ_BODY:
      {
        assert receivedBodies.contains(msg.blockId): "I should have received this block body if someone is requesting it from me!";
        Message m = new Message();
        m.type = MSGType.SEND_BODY;
        m.blockId = msg.blockId;
        m.replyTo = null;
        m.hops = msg.hops;  // responding to downstream peer, no new hop

        TransportDeltaQ.setBody(true);  // Going to send body (==> LARGE)
        send(src, myPid(), m);
        break;
      }

      case SEND_BODY:
      {
        assert !receivedBodies.contains(msg.blockId): "I shouldn't have received this block body for a second time!";
        receivedBodies.add(msg.blockId);  // Mark that I have received this body

        Message m = new Message();
        m.type = MSGType.PROCESSING_BODY;
        m.blockId = msg.blockId;
        m.replyTo = src;
        m.hops = msg.hops;  // internal event, no new hop
        schedule(bodyProcessing, m);
        break;
      }

      case PROCESSING_BODY:
      {
        long timeSinceBlockGeneration = CommonState.getTime() % cycle; // Quick & dirty way to estimate relative time
        Stats.reportDelivery(msg.blockId, timeSinceBlockGeneration, msg.hops);
        System.out.println(timeSinceBlockGeneration+"\t"+msg.replyTo+" -> "+myNode().getID());

        Message m = new Message();
        m.type = MSGType.SEND_HEADER;
        m.blockId = msg.blockId;
        m.replyTo = null;
        m.hops = msg.hops+1;  // forwarding downstream to the next hop!

        TransportDeltaQ.setBody(false);  // Going to send header (==> SMALL)
        for (Peer peer: downstreamPeers)
          send(peer.address, myPid(), m);

        break;
      }

      case MINE_BLOCK:
      {
        // Pretend I just "received" header and body
        receivedHeaders.add(msg.blockId);  // Mark that I have received this header
        receivedBodies.add(msg.blockId);  // Mark that I have received this body
        long timeSinceBlockGeneration = CommonState.getTime() % cycle; // Quick & dirty way to estimate relative time
        Stats.reportDelivery(msg.blockId, timeSinceBlockGeneration, msg.hops);

        // Then forward header to my downstream peers
        Message m = new Message();
        m.type = MSGType.SEND_HEADER;
        m.blockId = msg.blockId;
        m.replyTo = null;
        m.hops = msg.hops+1;  // forwarding downstream to the first hop!

        TransportDeltaQ.setBody(false);  // Going to send header (==> SMALL)
        for (Peer peer: downstreamPeers)
          send(peer.address, myPid(), m);

        break;
      }

      default:
        assert false: "How did we get here?!";
    }
  }



  public void generateBlock(int blockId)
  {
    Message msg = new Message();
    msg.type = MSGType.MINE_BLOCK;
    msg.blockId = blockId;
    msg.replyTo = null;
    msg.hops = 0;

    processEvent(null,  msg);
  }



  public void addSubscriber(Peer neighbor)
  {
    downstreamPeers.add(neighbor);
  }



  public void removeSubscriber(Peer neighbor)
  {
    downstreamPeers.remove(neighbor);
  }



  @Override
  public void onKill()
  {
    // TODO Auto-generated method stub
    
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
    getDiss((int)neighbor.getID()).addSubscriber(myPeer());

    return true;
  }



  @Override
  public boolean contains(Peer neighbor)
  {
    return upstreamPeers.contains(neighbor);
  }



  static private Dissemination getDiss(int i)
  {
    return ((Dissemination)Network.get(i).getProtocol(pid));
  }



  public String toString()
  {
    return "Diss "+myNode().getID()+" ("+upstreamPeers.size()+" up, "+downstreamPeers.size()+" down)";
  }
}
