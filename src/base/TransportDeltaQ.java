/*
 * Created on Jun 9, 2021 by Spyros Voulgaris
 *
 */
package base;

import peernet.core.Engine;
import peernet.core.Engine.AddressType;
import peernet.core.Node;
import peernet.transport.Address;
import peernet.transport.AddressSim;
import peernet.transport.RouterNetwork;
import peernet.transport.Transport;

public class TransportDeltaQ extends Transport
{
  /**
   * The delay that corresponds to the time spent on the source (and
   * destination) nodes. In other words, full latency is calculated by fetching
   * the latency that belongs to communicating between two routers, incremented
   * by twice this delay. Defaults to 0.
   * 
   * @config
   */
  static double scale;
  static boolean body;

  static int[] size = {5, 10, 20, 50, 100, 200, 500, 1000, 2000};
  static double[] transferForLatency150ms = {0.150, 0.450, 0.750, 1.050, 1.351, 1.651, 2.251, 3.152, 5.253};

  static int extra_tcp_trips;


  public TransportDeltaQ(String prefix)
  {
    assert Engine.getAddressType()==AddressType.SIM;
  }

  public static void setBody(boolean _body)
  {
    body = _body;
  }

  public static void setBodySize(int kilobytes)
  {
    // find the right scale
    int i=0;
    while (i<transferForLatency150ms.length)
    {
      if (kilobytes <= size[i])
        break;
      i++;
    }

    //XXX: Won't work for > 2000KB
    scale = transferForLatency150ms[i];
  }

  public static void setBodyExtraRoundTrips(int trips)
  {
    extra_tcp_trips = trips;
  }

  @Override
  public void send(Node src, Address dest, int pid, Object payload)
  {
    int senderRouter = (int) src.getID()%RouterNetwork.getSize();
    int receiverRouter = dest.hashCode()%RouterNetwork.getSize();
    if (senderRouter==676 && receiverRouter==914)
      System.out.println("Here");
    Address senderAddress = new AddressSim(src);

//    double l = RouterNetwork.getLatency(senderRouter, receiverRouter);
//    int latency = (int)(body ? l * scale / 0.150 : l);

    int latency = RouterNetwork.getLatency(senderRouter, receiverRouter);
//    int latency2 = RouterNetwork.getLatency(receiverRouter, senderRouter);
//    if (latency != latency2)
//    {
//      //System.out.println("DISCR "+latency+" "+latency2+" "+(latency-latency2));
//      latency = Math.min(latency,latency2);
//    }
    if (body)
      latency = latency * (1 + 2*extra_tcp_trips);

    if (latency>=0) // if latency < 0, it's a broken link
      addEventIn(latency, senderAddress, ((AddressSim) dest).node, pid, payload);
  }



  @Override
  public Object clone()
  {
    return this; // In SIM or EMU modes, all nodes use a single transport instance
  }
}
