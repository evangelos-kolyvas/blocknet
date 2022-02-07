/*
 * Created on Nov 6, 2020 by Spyros Voulgaris
 *
 */
package base;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import peernet.config.Configuration;
import peernet.core.CommonState;
import peernet.core.Control;
import peernet.core.Network;

public class Stats implements Control
{
  //static ArrayList<Long>[] deliveryTimes;

  // One ArrayList<Long> per block
  static ArrayList<ArrayList<Long>> deliveryTimesArray;
  static ArrayList<Long> currentDeliveryTimes;

  // One HashMap per block
  static ArrayList<HashMap<Integer, Integer>> deliveryHopsArray;
  static HashMap<Integer, Integer> currentDeliveryHops;

  int disseminationPid;
  String filebase;

  static int currentBlock = -1;
  boolean firstTime = true;

  // Storing the maximum reported time and hops
  static long maxTime = -1;
  static long maxHops = -1;



  public Stats(String prefix)
  {
    disseminationPid = Configuration.getPid(prefix + ".protocol");
    filebase = Configuration.getString("LOGFILE", null);
    if (filebase.isEmpty())
      filebase = null;

    deliveryTimesArray = new ArrayList<ArrayList<Long>>();
    deliveryHopsArray = new ArrayList<HashMap<Integer,Integer>>();
  }



  public static void reportDelivery(int blockId, long time, int hops)
  {
    if (blockId != currentBlock)
    {
      currentBlock = blockId;

      currentDeliveryTimes = new ArrayList<>();
      deliveryTimesArray.add(currentDeliveryTimes);

      currentDeliveryHops = new HashMap<Integer,Integer>();
      deliveryHopsArray.add(currentDeliveryHops);
    }

    currentDeliveryTimes.add(time);
    int h = currentDeliveryHops.getOrDefault(hops, Integer.valueOf(0));
    currentDeliveryHops.put(hops, h+1);

    maxTime = Math.max(time, maxTime);
    maxHops = Math.max(hops, maxHops);
  }



  private PrintStream getOutputStream(String extension) throws FileNotFoundException
  {
    if (filebase==null)
      return System.out;
    else
    {
      String filename;
      filename = filebase + "." + extension;

      PrintStream out;
      if (firstTime)
        out = new PrintStream(filename);  // create
      else
        out = new PrintStream(new FileOutputStream(filename, true));  // append

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

    for (ArrayList<Long> times: deliveryTimesArray)
    {
      int uninformedNodes = Network.size();
      for (long time: times)
        out.println(time+"\t"+(--uninformedNodes));
      out.print("\n\n");
    }
    out.close();
  }



  /**
   * Vertical averaging over all block delivery times.
   * That is, for each point in time, the average number of
   * uninformed nodes (across all block disseminations) is
   * reported.
   * 
   * @throws FileNotFoundException 
   */
  private void printVerticalAvg() throws FileNotFoundException
  {
    PrintStream out = getOutputStream("times.avg");
    out.println("#time\tnodes");

    int numBlocks = deliveryTimesArray.size();
    int uninformedNodes = Network.size() * numBlocks;
    ArrayList<Long> all = new ArrayList<>(uninformedNodes);

    for (ArrayList<Long> times: deliveryTimesArray)
      all.addAll(times);

    Collections.sort(all);

    long time = 0;
    long prevTime = 0;
    for (long t: all)
    {
      time = t;
      if (time != prevTime)
      {
        out.println(prevTime+"\t"+uninformedNodes/(double)numBlocks);
        prevTime = time;
      }
      uninformedNodes--;
    }
    out.println(time+"\t"+0.0);
    out.print("\n\n");
    out.close();
  }



  /**
   * Vertical averaging, reporting the average for every single millisecond,
   * irrespectively of whether it has changed since the previous millisecond.
   * 
   * @throws FileNotFoundException 
   */
  private void printVerticalAvgExhaustive() throws FileNotFoundException
  {
    PrintStream out = getOutputStream("times.xavg");
    out.println("#time\tnodes");
  
    int[] all = new int[(int)maxTime];
  
    int numBlocks = deliveryTimesArray.size();
  
    for (ArrayList<Long> times: deliveryTimesArray)
    {
      long t = 0;
      int uninformedNodes = Network.size();
  
      for (long time: times)
      {
        while (t<time)
          all[(int) t++] += uninformedNodes;
        uninformedNodes--;
      }
    }
  
    int time = 0;
    for (long uninformedNodes: all)
    {
      out.println(time + "\t" + uninformedNodes/(double)numBlocks);
      time++;
    }
  
    out.print("\n\n");
    out.close();
  }



  /**
   * Horizontal averaging over all block delivery times.
   * That is, for each number of uninformed nodes, the average
   * time when this was reached (across all block disseminations)
   * is reported.
   * 
   * @throws FileNotFoundException 
   */
  private void printHorizontalAvg() throws FileNotFoundException
  {
    PrintStream out = getOutputStream("times.alt");
    out.println("#time\tnodes");

    ArrayList<Long> deliveryTimesSum = new ArrayList<>(Network.size());
    for (int i=0; i<Network.size(); i++)
      deliveryTimesSum.add(0L);

    for (ArrayList<Long> times: deliveryTimesArray)
      for (int i=0; i<Network.size(); i++)
        deliveryTimesSum.set(i, deliveryTimesSum.get(i)+times.get(i));

    int numBlocks = deliveryTimesArray.size();
    for (int i=0; i<Network.size(); i++)
    {
      double time = deliveryTimesSum.get(i) / (double)numBlocks;
      double blocks = Network.size() - i - 1;
      out.println(time + "\t" + blocks);
    }

    out.print("\n\n");
    out.close();
  }



  private void printAllHops() throws FileNotFoundException
  {
    PrintStream out = getOutputStream("hops");
    out.println("#hops\tcount");

    HashMap<Integer,Integer> avg = new HashMap<>();

    for (HashMap<Integer,Integer> hopsForBlock: deliveryHopsArray)
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
    int numBlocks = deliveryHopsArray.size();
    while ( (count=avg.getOrDefault(hops, Integer.valueOf(-1))) >= 0)
    {
      // output the number of nodes that received this block in 'count' hops
//      out.format("%d\t%f\n", hops, count/(double)blocks);
      out.println(hops + "\t" + count/(double)numBlocks);

      // Increment 'hops'
      hops++;
    }
    out.print("\n\n");
    out.close();
  }



  @Override
  public boolean execute()
  {
    if (CommonState.getTime() == 0)
      return false;

    //System.out.println("Starting to compute stats!");
    try
    {
      printVerticalAvg();
      printVerticalAvgExhaustive();
      printHorizontalAvg();

      printAllTimes();
      //printAllHops();

      // Finally, reset all data, to prepare for next measurements
      deliveryTimesArray.clear();
      deliveryHopsArray.clear();
      maxTime = -1;
      maxHops = -1;
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }

    firstTime = false;

    return false;
  }
}
