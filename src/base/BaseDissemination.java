/*
 * Created on May 13, 2021 by Spyros Voulgaris
 *
 */
package base;

import java.util.ArrayList;
import java.util.HashMap;
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
  static private final String PAR_HEADER_PROCESSING = "header_validation_time";
  static private final String PAR_BODY_PROCESSING = "body_validation_time";
  static private final String PAR_EXTRA_ROUND_TRIPS = "extra_tcp_trips";
  static private final String PAR_HEADER_ONLY = "header_only";
  static private final String PAR_BODY_REQUESTS = "body_requests";

  static protected int pid;

  static private int header_validation_time;
  static private int body_validation_time;
  static private boolean headerOnly;
  static private int bodyRequests = 0; // From how many upstream peers to pull a body.

  HashSet<Integer> receivedHeaders;
  HashSet<Integer> receivedBodies;
  HashSet<Integer> validatedBodies;
  HashMap<Integer,Integer> bodiesRequested; // The number of upstream peers from which I have requested this body

  protected ArrayList<Peer> downstreamPeers;

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

  class Message implements Cloneable
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

    public Object clone()
    {
      try
      {
        return super.clone();
      }
      catch (CloneNotSupportedException e)
      {
        e.printStackTrace();
        return null;
      }
    }
  }


  

  public BaseDissemination(String prefix)
  {
    super(prefix);

    header_validation_time = Configuration.getInt(prefix+"."+PAR_HEADER_PROCESSING);
    body_validation_time = Configuration.getInt(prefix+"."+PAR_BODY_PROCESSING);
    headerOnly = Configuration.getBoolean(prefix+"."+PAR_HEADER_ONLY, false);
    bodyRequests = Configuration.getInt(prefix+"."+PAR_BODY_REQUESTS);

    int extra_round_trips = Configuration.getInt(prefix+"."+PAR_EXTRA_ROUND_TRIPS);
    TransportDeltaQ.setBodyExtraRoundTrips(extra_round_trips);

    pid = myPid();
  }



  public Object clone()
  {
    BaseDissemination d = (BaseDissemination) super.clone();

    d.downstreamPeers = new ArrayList<>();

    d.receivedHeaders = new HashSet<>();
    d.receivedBodies = new HashSet<>();
    d.validatedBodies = new HashSet<>();
    d.bodiesRequested = new HashMap<>();

    return d;
  }



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
        validatedBodies.add(msg.blockId);  // Mark that I have validated this body

        // Stats
        hookReceivedBody(msg.blockId, CommonState.getTime()-msg.time, msg.hops);

        // Then forward header to my downstream peers
        Message m = (Message) msg.clone();
        m.type = MSGType.DN__RECEIVE_AND_PROCESS_HEADER;
        m.hops++;  // forwarding downstream to the first hop!

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
        hookReceivedHeader(msg.blockId, CommonState.getTime()-msg.time, msg.hops, src);

        if (shouldRequestBody(msg.blockId))
        //if (!receivedHeaders.contains(msg.blockId))
        {
          receivedHeaders.add(msg.blockId);  // Mark that I received this header

          Message m = (Message) msg.clone();
          if (headerOnly)
          {
            // Assume that the body came along with the header, so proceed to forwarding it further on
            m.type = MSGType.DN__FORWARD_NEXT_HOP;
            m.replyTo = src;
            schedule(header_validation_time + body_validation_time, m);
          }
          else
          {
            // Respond to my upstream peer requesting the body
            m.type = MSGType.DN__SEND_BODY_REQUEST;
            m.replyTo = src;
            schedule(header_validation_time, m);
          }
        }
        break;

      /*
       *  Called when a node has completed the processing (validation) of a
       *  new header it recently received, and it is ready to request the body
       *  from the respective upstream peer.
       */
      case DN__SEND_BODY_REQUEST:
      {
        Message m = (Message) msg.clone();
        m.type = MSGType.UP__SEND_BODY;

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

        Message m = (Message) msg.clone();
        m.type = MSGType.DN__RECEIVE_AND_PROCESS_BODY;

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
        //assert !receivedBodies.contains(msg.blockId): "I shouldn't have received this block body for a second time!";

        receivedBodies.add(msg.blockId);  // Mark that I have received this body

        Message m = (Message) msg.clone();
        m.type = MSGType.DN__FORWARD_NEXT_HOP;
        m.replyTo = src;

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


        // Stats
        if (!validatedBodies.contains(msg.blockId))
        {
          hookReceivedBody(msg.blockId, CommonState.getTime()-msg.time, msg.hops);
          validatedBodies.add(msg.blockId);  // Mark that I have validated this body

          Message m = (Message) msg.clone();
          m.type = MSGType.DN__RECEIVE_AND_PROCESS_HEADER;
          m.hops++;  // forwarding downstream to the next hop!

          TransportDeltaQ.setBody(false);  // Going to send header (==> SMALL)
          for (Peer peer: downstreamPeers)
          {
            if (peer.address.equals(msg.replyTo))  // do not send block back to the node that gave it to me!
              continue;
            send(peer.address, myPid(), m);
          }
        }

        break;
      }

      default:
        assert false: "How did we end up here?!";
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



  protected boolean shouldRequestBody(int blockId)
  {
    // First check whether I have already received this block's body.
    if (validatedBodies.contains(blockId))  // If yes, do not request again.
      return false;

    // Then, check how many times (if any) I have requested the body.
    int numRequested = bodiesRequested.getOrDefault(blockId, 0);
    if (numRequested < bodyRequests)
    {
      bodiesRequested.put(blockId, numRequested+1);
      return true;
    }
    else
      return false;
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



  protected abstract void hookReceivedHeader(int blockId, long elapsedTime, int hops, Address from);

  protected abstract void hookReceivedBody(int blockId, long elapsedTime, int hops);
}
