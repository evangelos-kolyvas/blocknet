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



public abstract class BaseDissemination extends Protocol implements Linkable
{
  private static final String PAR_HEADER_PROCESSING = "header_validation_time";
  private static final String PAR_BODY_PROCESSING = "body_validation_time";
  private static final String PAR_EXTRA_ROUND_TRIPS = "extra_tcp_trips";

  //static int cycle;
  static int pid;

  static int header_validation_time;
  static int body_validation_time;

  HashSet<Integer> receivedHeaders;
  HashSet<Integer> receivedBodies;

//  ArrayList<Peer> upstreamPeers;
  ArrayList<Peer> downstreamPeers;

  /**
   * These are the message types for dissemination. The message name indicates
   * which node, upstream (UP) or downstream (DN), should take some action,
   * and the action per se.
   * 
   * E.g., UP__GENERATE_NEW_BLOCK is sent to the node that is selected (by a
   * control) as slot leader and is asked to generate a new block.
   * 
   * DN__RECEIVE_AND_PROCESS_HEADER will be sent to a downstream peer to deliver
   * a header to it. The peer will check whether this corresponds to a new
   * header, and if so it will schedule a DN__SEND_BODY_REQUEST locally, to
   * account for the time needed to locally validate the header. When that
   * latter event is fired, the peer will send an UP__SEND_BODY message to the
   * corresponding upstream peer. And so on so forth.
   * 
   * @author spyros
   */
  enum MSGType
  {
    UP__GENERATE_NEW_BLOCK,
    DN__RECEIVE_AND_PROCESS_HEADER,
    DN__SEND_BODY_REQUEST,
    UP__SEND_BODY,
    DN__RECEIVE_AND_PROCESS_BODY,
    DN__FORWARD_NEXT_HOP
  }

  class Message
  {
    int blockId;
    Address replyTo;
    MSGType type;
    int hops;        // number of hops so far (for miner, hops=0)  
    long time;       // this block's generation time

    public String toString()
    {
      return "<"+type+","+blockId+","+time+","+replyTo+","+hops+">";
    }
  }


  

  public BaseDissemination(String prefix)
  {
    super(prefix);

//    cycle = Configuration.getInt("CYCLE");
    header_validation_time = Configuration.getInt(prefix+"."+PAR_HEADER_PROCESSING);
    body_validation_time = Configuration.getInt(prefix+"."+PAR_BODY_PROCESSING);

    int extra_round_trips = Configuration.getInt(prefix+"."+PAR_EXTRA_ROUND_TRIPS);
    TransportDeltaQ.setBodyExtraRoundTrips(extra_round_trips);

    pid = myPid();
  }



  public Object clone()
  {
    BaseDissemination d = (BaseDissemination) super.clone();

//    d.upstreamPeers = new ArrayList<>();
    d.downstreamPeers = new ArrayList<>();

    d.receivedHeaders = new HashSet<>();
    d.receivedBodies = new HashSet<>();

    return d;
  }



//  @Override
//  public void nextCycle(int schedId)
//  {
//    // TODO Auto-generated method stub
//  }



  @Override
  public void processEvent(Address src, Object event)
  {
    Message msg = (Message)event;

    //System.err.println(CommonState.getTime()+": "+src+"->"+myNode().getID()+", "+msg);

    switch (msg.type)
    {
      /*
       *  Called when a node is chosen (slot leader) to generate a new block
       */
      case UP__GENERATE_NEW_BLOCK:
      {
        // Pretend I just "received" header and body
        receivedHeaders.add(msg.blockId);  // Mark that I have received this header
        receivedBodies.add(msg.blockId);  // Mark that I have received this body
//        long timeSinceBlockGeneration = CommonState.getTime() % cycle; // Quick & dirty way to estimate relative time
//        Stats.reportDelivery(msg.blockId, timeSinceBlockGeneration, msg.hops);
        hookReceivedBody(msg.blockId, CommonState.getTime()-msg.time, msg.hops);

        // Then forward header to my downstream peers
        Message m = new Message();
        m.type = MSGType.DN__RECEIVE_AND_PROCESS_HEADER;
        m.blockId = msg.blockId;
        m.replyTo = null;
        m.hops = msg.hops+1;  // forwarding downstream to the first hop!

        TransportDeltaQ.setBody(false);  // Going to send header (==> SMALL)
        for (Peer peer: downstreamPeers)
          send(peer.address, myPid(), m);

        break;
      }

      /*
       *  Called when a node receives a header sent by an upstream peer.
       *  Takes the time needed for processing, and schedules an internal
       *  event DO_SEND_BODY_REQUEST to be 
       */
      case DN__RECEIVE_AND_PROCESS_HEADER:
        if (!receivedHeaders.contains(msg.blockId))
        {
          receivedHeaders.add(msg.blockId);  // Mark that I received this header

          // Respond to my upstream peer requesting the body
          Message m = new Message();
          m.type = MSGType.DN__SEND_BODY_REQUEST;
          m.blockId = msg.blockId;
          m.replyTo = src;
          m.hops = msg.hops;  // internal event, no new hop

          schedule(header_validation_time, m);
        }
        break;

      /*
       *  Called when a node has completed the processing (validation) of a
       *  new header it recently received, and it is ready to request the body
       *  from the respective upstream peer.
       */
      case DN__SEND_BODY_REQUEST:
      {
        Message m = new Message();
        m.type = MSGType.UP__SEND_BODY;
        m.blockId = msg.blockId;
        m.replyTo = null;
        m.hops = msg.hops;  // sending back to sender, no new hop

        TransportDeltaQ.setBody(false);  // Going to send request for body (==> SMALL)
        send(msg.replyTo, myPid(), m);
        break;
      }

      /*
       *  Called when the upstream peer receives a request to send the body.
       */
      case UP__SEND_BODY:
      {
        assert receivedBodies.contains(msg.blockId): "Someone is requesting from me a body I have not received!";

        Message m = new Message();
        m.type = MSGType.DN__RECEIVE_AND_PROCESS_BODY;
        m.blockId = msg.blockId;
        m.replyTo = null;
        m.hops = msg.hops;  // responding to downstream peer, no new hop

        TransportDeltaQ.setBody(true);  // Going to send body (==> LARGE)
        send(src, myPid(), m);
        break;
      }

      /*
       *  Called when a node receives the block body.
       *  It takes the time needed for processing (body validation), and
       *  schedules an internal event to proceed with forwarding the
       *  block further.
       */
      case DN__RECEIVE_AND_PROCESS_BODY:
      {
        assert !receivedBodies.contains(msg.blockId): "I shouldn't have received this block body for a second time!";

        receivedBodies.add(msg.blockId);  // Mark that I have received this body

        Message m = new Message();
        m.type = MSGType.DN__FORWARD_NEXT_HOP;
        m.blockId = msg.blockId;
        m.replyTo = src;
        m.hops = msg.hops;  // internal event, no new hop

        schedule(body_validation_time, m);
        break;
      }

      /*
       *  Called when a node has completed the validation of the received block,
       *  and it proceeds with forwarding its header to all its downstream peers.
       */
      case DN__FORWARD_NEXT_HOP:
      {
//        long timeSinceBlockGeneration = CommonState.getTime() % cycle; // Quick & dirty way to estimate relative time
//        Stats.reportDelivery(msg.blockId, timeSinceBlockGeneration, msg.hops);
//        System.out.println(timeSinceBlockGeneration+"\t"+msg.replyTo+" -> "+myNode().getID());

        Message m = new Message();
        m.type = MSGType.DN__RECEIVE_AND_PROCESS_HEADER;
        m.blockId = msg.blockId;
        m.replyTo = null;
        m.hops = msg.hops+1;  // forwarding downstream to the next hop!

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
    msg.type = MSGType.UP__GENERATE_NEW_BLOCK;
    msg.blockId = blockId;
    msg.replyTo = null;
    msg.hops = 0;
    msg.time = CommonState.getTime();

    processEvent(null,  msg);
  }



  public void addDownstreamPeer(Peer neighbor)
  {
    downstreamPeers.add(neighbor);
  }



  public void removeDownstreamPeer(Peer neighbor)
  {
    downstreamPeers.remove(neighbor);
  }



  static protected BaseDissemination getDissProt(int i)
  {
    return ((BaseDissemination) Network.get(i).getProtocol(pid));
  }



  public String toString()
  {
    return "BaseDissemination "+myNode().getID()+" ("+downstreamPeers.size()+" down)";
  }



  protected abstract void hookReceivedHeader();

  protected abstract void hookReceivedBody(int blockId, long l, int hops);
}
