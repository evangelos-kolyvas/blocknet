package base;

import peernet.config.Configuration;
import peernet.core.CommonState;
import peernet.core.Engine;
import peernet.core.Engine.AddressType;
import peernet.core.Node;
import peernet.transport.Address;
import peernet.transport.AddressSim;
import peernet.transport.RouterNetwork;
import peernet.transport.Transport;

public class TransportFailures extends Transport
{
  static boolean body;

  static int extra_tcp_trips;

  private static final String PAR_Failures = "failures";

  private final int failures;


  public TransportFailures(String prefix)
  {
    assert Engine.getAddressType()==AddressType.SIM;
    failures = Configuration.getInt(prefix + "." + PAR_Failures);
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
    if (CommonState.r.nextInt(100) < failures) {
      return;
    }

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
      latency = latency * (1 + 2*extra_tcp_trips);

    if (latency>=0) // if latency < 0, it's a broken link
      addEventIn(latency, senderAddress, ((AddressSim) dest).node, pid, payload);
  }



  @Override
  public Object clone()
  {
    return this;
  }
}
