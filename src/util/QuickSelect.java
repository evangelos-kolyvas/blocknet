/*
 * Created on Nov 15, 2020 by Spyros Voulgaris
 *
 */
package util;

import peernet.config.Configuration;
import peernet.core.CommonState;

public class QuickSelect
{
  public static int[] ids = null;
  private static int[] scores;
  
  static
  {
    int netsize = Configuration.getInt("NODES");

    ids = new int[netsize];
    for (int i=0; i<ids.length; i++)
      ids[i] = i;
  }



  public static void testQS(int size, int topK)
  {
    scores = new int[size];
    for (int i=0; i<scores.length; i++)
      scores[i] = i;

    for (int i=0; i<scores.length; i++)
    {
      int r = CommonState.r.nextInt(scores.length);
      int tmp = scores[r];
      scores[r] = scores[i];
      scores[i] = tmp;
    }

    quickSelect(scores, topK);
  }



  private static void swap(int i, int j)
  {
    int tmp = ids[i];
    ids[i] = ids[j];
    ids[j] = tmp;   
  }



  public static void shuffle()
  {
    for (int i=0; i<ids.length; i++)
    {
      int r = CommonState.r.nextInt(ids.length);
      int tmp = ids[r];
      ids[r] = ids[i];
      ids[i] = tmp;
    }
  }



  private static void dump(int count)
  {
    for (int i=0; i<count; i++)
      System.out.print(scores[ids[i]]+" "+(i==9 ? "- " : ""));
    System.out.println();
  }



  public static void quickSelect(int[] _scores, int topK)
  {
    scores = _scores;
    int j;
    int left = 0;
    int right = scores.length-1;

    //dump(20);

    topK--;
    do
    {
      j = quickSelectLoop(left, right);
      //dump(20);

      if (j > topK)
        right = j;
      else if (j < topK)
        left = j+1;

    } while (j != topK);
  }



  private static int quickSelectLoop(int left, int right)
  {
    int pivotScore = scores[ids[left]];
    int i = left+1; 
    int j = right;

    while (true)
    {
      while (i<=right && scores[ids[i]] < pivotScore)
        i++;
      while (j>left && scores[ids[j]] >= pivotScore)
        j--;
      if (i==j+1)
        break;
      swap(i,j);
      i++;
      j--;
    }
    assert i==j+1: "How did it break out from the loop?";

    swap(left, j);

    return j;
  }
}
