/*
 * Created on Nov 6, 2020 by Spyros Voulgaris
 *
 */
package cr;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import peernet.config.Configuration;
import peernet.core.Control;
import peernet.core.Network;

public class Stats implements Control
{
  static ArrayList<Long>[] deliveryTimes;
  static HashMap<Integer, Integer>[] deliveryHops;

  int disseminationPid;
  int blocks;
  String filebase;



  public Stats(String prefix)
  {
    disseminationPid = Configuration.getPid(prefix + ".protocol");
    blocks = Configuration.getInt(prefix + ".blocks");
    filebase = Configuration.getString("LOGFILE", null);
    if (filebase.isEmpty())
      filebase = null;

    deliveryTimes = new ArrayList[blocks];
    deliveryHops = new HashMap[blocks];

    for (int i=0; i<blocks; i++)
    {
      deliveryTimes[i] = new ArrayList<>();
      deliveryHops[i] = new HashMap<>();
    }
  }



  public static void reportDelivery(int blockId, long time, int hops)
  {
    deliveryTimes[blockId].add(time);
    int h = deliveryHops[blockId].getOrDefault(hops, Integer.valueOf(0));
    deliveryHops[blockId].put(hops, h+1);
  }



  private PrintStream getOutputStream(String extension) throws FileNotFoundException
  {
    if (filebase==null)
      return System.out;
    else
    {
      String filename = filebase + "." + extension;
      PrintStream out = new PrintStream(filename);
      return out;
    }
  }

  /**
   * Prints delivery times for all blocks
   * @throws IOException 
   */
  private void printAllTimes() throws FileNotFoundException
  {
    PrintStream out = getOutputStream("times");
    out.println("#time\tnodes");

    for (ArrayList<Long> times: deliveryTimes)
    {
      int uninformedNodes = Network.size();
      for (long time: times)
        out.println(time+"\t"+(--uninformedNodes));
      out.print("\n\n");
    }
    out.close();
  }



  /**
   * Averaging over all block delivery times
   * @throws FileNotFoundException 
   */
  private void printTimesAvgPerTime() throws FileNotFoundException
  {
    PrintStream out = getOutputStream("times.avg");
    out.println("#time\tnodes");

    int uninformedNodes = Network.size() * blocks;
    ArrayList<Long> all = new ArrayList<>(uninformedNodes);

    for (ArrayList<Long> times: deliveryTimes)
      all.addAll(times);

    Collections.sort(all);

    long time = 0;
    long prevTime = 0;
    for (long t: all)
    {
      time = t;
      if (time != prevTime)
      {
        out.println(prevTime+"\t"+uninformedNodes/(double)blocks);
        prevTime = time;
      }
      uninformedNodes--;
    }
    out.println(time+"\t"+0.0);
    out.print("\n\n");
    out.close();
  }



  private void printTimesAvgPerPercentile() throws FileNotFoundException
  {
    PrintStream out = getOutputStream("times.alt");
    out.println("#time\tnodes");

    ArrayList<Long> deliveryTimesSum = new ArrayList<>(Network.size());
    for (int i=0; i<Network.size(); i++)
      deliveryTimesSum.add(0L);

    for (ArrayList<Long> times: deliveryTimes)
      for (int i=0; i<Network.size(); i++)
        deliveryTimesSum.set(i, deliveryTimesSum.get(i)+times.get(i));

    for (int i=0; i<Network.size(); i++)
      out.println(deliveryTimesSum.get(i)/(double)blocks+"\t"+(Network.size()-i));

    out.print("\n\n");
    out.close();
  }



  private void printAllHops() throws FileNotFoundException
  {
    PrintStream out = getOutputStream("hops");
    out.println("#hops\tcount");

    HashMap<Integer,Integer> avg = new HashMap<>();

    for (HashMap<Integer,Integer> hopsForBlock: deliveryHops)
    {
      int hops = 0;
      int count;
      while ( (count=hopsForBlock.getOrDefault(hops, Integer.valueOf(-1))) >= 0)
      {
        // output the number of nodes that received this block in 'count' hops
        out.format("%d\t%d\n", hops, count);

        // Add this record to the aggregation structure
        int c = avg.getOrDefault(hops, Integer.valueOf(0));
        avg.put(hops, c+count);

        // Increment 'hops'
        hops++;
      }

      out.print("\n\n");
    }
    out.close();

    // Now output the average values
    out = getOutputStream("hops.avg");
    out.println("#hops\tcount%");
    int hops = 0;
    int count;
    while ( (count=avg.getOrDefault(hops, Integer.valueOf(-1))) >= 0)
    {
      // output the number of nodes that received this block in 'count' hops
//      out.format("%d\t%f\n", hops, count/(double)blocks);
      out.println(hops + "\t" + count/(double)blocks);

      // Increment 'hops'
      hops++;
    }
    out.print("\n\n");
    out.close();
  }

  /**
   * Averaging method followed by Vangelis
   */
//  private void altAvg()
//  {
//    int[] all = new int[2000];  // ugly
//
//    for (ArrayList<Long> times: deliveryTimes)
//    {
//      long t = 0;
//      int uninformedNodes = Network.size();
//
//      for (long time: times)
//      {
//        while (t<time)
//          all[(int) t++] += uninformedNodes;
//        uninformedNodes--;
//      }
//    }
//
//    int time = 0;
//    for (long uninformedNodes: all)
//    {
//      System.out.println(time+"\t"+uninformedNodes/(double)blocks);
//      time++;
//    }
//  }



  @Override
  public boolean execute()
  {
    //System.out.println("Starting to compute stats!");
    try
    {
      printTimesAvgPerTime();
      printTimesAvgPerPercentile();
      printAllTimes();
      printAllHops();
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }

    return false;
  }
}
