/*
 * Created on Nov 15, 2020 by Spyros Voulgaris
 *
 */
package util;

import java.util.AbstractList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import peernet.core.CommonState;


/**
 * Implementation of the QuickSelect algorithm, which works like QuickSort,
 * but select the topK (or minK) elements of a list without resorting to
 * an exhaustive (and unnecessary) sorting.
 * 
 * @author spyros
 *
 */
public class QuickSelect
{
  public static int[] ids = new int[0];
  private static int[] scores;



  /**
   * Method to test the class. Creates an array of integers, shuffles them,
   * and runs quickSelect() to select the topK of them.
   * 
   * @param size
   * @param topK
   */
  private static void testQS(int size, int topK)
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



  static void reset(int size)
  {
    //System.err.println("QS resetting to "+size);
    ids = new int[size];
    for (int i=0; i<size; i++)
      ids[i] = i;
  }



  private static void swap(int i, int j)
  {
    int tmp = ids[i];
    ids[i] = ids[j];
    ids[j] = tmp;   
  }



  private static void shuffle()
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



  /**
   * Runs the QuickSelect algorithm on the int array {@code _scores}.
   * 
   * It does not alter in any way the {@code _scores} array.
   * Instead, it operates on a second array, {@code ids}, which stores
   * integers from {@code 0} to {@code scores.size()-1}, indexing the items
   * in {@code scores}. The algorithm rearranges only the integers in {@code ids},
   * such that the first {@code topK} of them contain the indexes of the
   * {@code scores} elements with the minimum {@code topK} items.
   * 
   * @param _scores
   * @param topK
   */
  public static void quickSelect(final int[] _scores, int topK)
  {
    if (_scores.length != ids.length)
      reset(_scores.length);
    shuffle();

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



  
  private static class SortByScore implements Comparator<Integer>
  {
    @Override
    public int compare(Integer idA, Integer idB)
    {
      return scores[idA] - scores[idB];
    }
  }  
  
  public static void sortFirstItems(int topK)
  {
    assert scores.length >= topK: "topK="+topK+" is higher than scores.length="+scores.length;

    List<Integer> wrapper = new AbstractList<Integer>()
    {
      @Override
      public Integer get(int index)
      {
        return ids[index];
      }

      @Override
      public int size()
      {
        return topK;
      }

      @Override
      public Integer set(int index, Integer element)
      {
        int v = ids[index];
        ids[index] = element;
        return v;
      }
    };

    Collections.sort(wrapper, new SortByScore());
  }
}
