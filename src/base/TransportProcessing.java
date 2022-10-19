package base;

import peernet.config.Configuration;
import peernet.core.CommonState;
import peernet.core.Engine;
import peernet.core.Network;
import peernet.core.Node;
import peernet.transport.Address;
import peernet.transport.AddressSim;
import peernet.transport.RouterNetwork;
import peernet.transport.Transport;

public class TransportProcessing extends Transport
{
  static boolean body;

  static int extra_tcp_trips;
  private static final String PAR_MIN = "processingMin";
  private static final String PAR_MAX = "processingMax";

  private final int[] processingTimes;

  public TransportProcessing(String prefix) {
    assert Engine.getAddressType()== Engine.AddressType.SIM;
    int min = Configuration.getInt(prefix + "." + PAR_MIN);
    int max = Configuration.getInt(prefix + "." + PAR_MAX);
    processingTimes = new int[Network.size()];
    for (int i = 0; i < processingTimes.length; i++) {
      processingTimes[i] = CommonState.r.nextInt(max-min) + min;
    }
  }

  public static void setBody(boolean _body)
  {
    body = _body;
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
      latency = latency * (1 + 2*extra_tcp_trips) + processingTimes[senderRouter];

    if (latency>=0) // if latency < 0, it's a broken link
      addEventIn(latency, senderAddress, ((AddressSim) dest).node, pid, payload);
  }



  @Override
  public Object clone()
  {
    return this;
  }
}
