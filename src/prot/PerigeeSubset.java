/*
 * Created on Jan 14, 2022 by Spyros Voulgaris
 *
 */
package prot;

import java.util.ArrayList;
import java.util.BitSet;

import peernet.core.CommonState;
import peernet.core.Network;
import peernet.core.Peer;
import peernet.transport.Address;
import util.QuickSelect;

/**
 * Scores peers, and periodically calibrates neighbors.
 * Basically this is the class to run the Perigee model.
 * 
 * @author spyros
 *
 */
public class PerigeeSubset extends Perigee
{
  static ArrayList<BitSet> nodesInSubset;
  static ArrayList<ArrayList<Integer>> subsetsOfNode;
  static BitSet tmpSubset;

  static private final int LOWEST_SCORE = 10000; 
  static private final int SCORE_PERCENTILE = 90; 
  
  static int[] scores = new int[0];
  static int[] subsetScores;
  
  private ArrayList<int[]> allScores;
  private int[] currentScores;
  private int currentBlockId = -1;
  private long firstDelivery;
  private int numSubsets;
  private int completedSubsets;



  public PerigeeSubset(String prefix)
  {
    super(prefix);

    allScores = new ArrayList<int[]>();

    // Prepare subsets
    tmpSubset = new BitSet();  // temporary BitSet for walking through all combinations recursively
    nodesInSubset = new ArrayList<BitSet>();
    n_choose_k(numOutgoing, numOutgoing-weakestLinks);

    numSubsets = nodesInSubset.size();
    assert numSubsets == combinations(numOutgoing, weakestLinks);
    
    subsetScores = new int[numSubsets];

    subsetsOfNode = new ArrayList<ArrayList<Integer>>();
    for (int i=0; i<numOutgoing; i++)
      subsetsOfNode.add(new ArrayList<>());

    for (int i=0; i<numOutgoing; i++)
      for (int j=0; j<numSubsets; j++)
        if (nodesInSubset.get(j).get(i))
          subsetsOfNode.get(i).add(j);

//    for (int i=0; i<numSubsets; i++)
//      System.out.println("Subset "+i+"\t"+subsets.get(i));
//
//    for (int i=0; i<numOutgoing; i++)
//      System.out.println(neighborBelongsToSubsets.get(i));
  }



  @SuppressWarnings("unchecked")
  public Object clone()
  {
    PerigeeSubset d = (PerigeeSubset) super.clone();
    d.allScores = (ArrayList<int[]>) allScores.clone();
    return d;
  }



  private int getSubsetScore(int subset)
  {
    if (scores.length != allScores.size())
      scores = new int[allScores.size()];

    int percentile = SCORE_PERCENTILE * scores.length / 100;

    for (int i=0; i<scores.length; i++)
      scores[i] = allScores.get(i)[subset];

    QuickSelect.quickSelect(scores, percentile);

    int min=LOWEST_SCORE;
//    for (int j=0; j<scores.length; j++)
//      System.out.println(j+" "+scores[QuickSelect.ids[j]]);
    for (int j=percentile; j<scores.length; j++)
      min = Math.min(min, scores[QuickSelect.ids[j]]);

    return min;
  }



  public void calibrate()
  {
    // Remove weakest link, if scores are in place
    if (allScores.size() > 0 & weakestLinks > 0)
    {
      for (int i=0; i<numSubsets; i++)
        subsetScores[i] = getSubsetScore(i);

      // Find the subset with the best score (min number)
      int bestScore = LOWEST_SCORE;
      int strongestSubset=-1;
      for (int i=0; i<numSubsets; i++)
      {
        if (subsetScores[i] < bestScore)
        {
          strongestSubset = i;
          bestScore = subsetScores[i];
        }
      }

      // Finally, properly remove the (bidirectional) links between me and each of
      // the weakest peers (i.e., the peers NOT belonging to the strongest subset)
      for (int index=numOutgoing-1; index>=0; index--)
      {
        if (nodesInSubset.get(strongestSubset).get(index))
          continue;

        // remove my i-th outgoing neighbor
        Peer weakPeer = outgoingSelections.get(index);
        outgoingSelections.remove(weakPeer);
        removeDownstreamPeer(weakPeer);

        // remove me from other peer
        PerigeeSubset weakProt = (PerigeeSubset) peer2prot(weakPeer);
        weakProt.incomingSelections.remove(myPeer());
        weakProt.removeDownstreamPeer(myPeer());
      }
    }

    // Fill in all missing links
    while (outgoingSelections.size() < numOutgoing)
    {
      // pick random neighbor
      int i = CommonState.r.nextInt(Network.size());
      Peer newPeer = id2peer(i);
   
      // check if I randomly picked myself, and skip me! :-)
      if (newPeer.equals(myPeer()))
        continue;

      // check if I already have this peer (either as outgoing or incoming)
      if (contains(newPeer))
        continue;

      // else, check if the other peer has available incoming slots
      PerigeeSubset newProt = (PerigeeSubset) peer2prot(newPeer);
      if (newProt.incomingSelections.size() >= numIncoming)
        continue;

      // if all is ok, add that node to me
      outgoingSelections.add(newPeer);
      addDownstreamPeer(newPeer);

      // and add myself to that node!
      newProt.incomingSelections.add(myPeer());
      newProt.addDownstreamPeer(myPeer());
    }

    // Reset scoring to allow the future assessment of the updated set of outgoing peers
    // It is important to explicitly set the initial score of all selected peers to 0,
    // as some never receive any points, and they would not be replaced if not present
    // in the scoring hashmap.
    allScores.clear();

    peerMapping.clear();
    int index=0;
    for (Peer peer: outgoingSelections)
      peerMapping.put(peer.address, index++);
  }



  @Override
  protected void hookReceivedHeader(int blockId, long relativeTime, int hops, Address from)
  {
    // Check if the sender is one of my selected (aka, outgoing) peers.
    int upstreamPeerIndex = addr2index(from);
    if (upstreamPeerIndex==-1)
      return;

    // Check if we have a new block ID.
    // If so, save the current timestamp on 'firstDelivery'
    // and append a new array of scores in 'allScores'.
    if (blockId != currentBlockId)
    {
      currentBlockId = blockId;
      firstDelivery = CommonState.getTime();
      completedSubsets = 0;

      // create new 'currentScores'
      currentScores = new int[numSubsets];
      for (int i=0; i<numSubsets; i++)
        currentScores[i] = LOWEST_SCORE;

      // and add it to 'allScores'
      allScores.add(currentScores);
    }

    // Check if all subsets have received a score for this block.
    if (completedSubsets == numSubsets)
      return;
    
    // Compute the score to be assigned to subsets seeing this block for the first time.
    int score = (int)(CommonState.getTime()-firstDelivery);

    // Go through all subsets of the upstream peer.
    // and assign this score to those that have not received a score yet.
    for (int subset: subsetsOfNode.get(upstreamPeerIndex))
    {
      if (currentScores[subset] == LOWEST_SCORE) // subset not previously scored
      {
        currentScores[subset] = score;
        completedSubsets++;
      }
    }
  }



  /**
   * Recursively walks through all "n choose k" combinations, and identifies
   * them in the 'subset' BitSet, populating the 'subsets' ArrayList for each
   * new subset.
   * 
   * @param n Total number of elements
   * @param k Number of elements to choose
   */
  private void n_choose_k(int n, int k)
  {
    if (k==0)
    {
      nodesInSubset.add((BitSet) tmpSubset.clone());
      return;
    }

    while (--n >= k-1)
    {
      tmpSubset.set(n);
      n_choose_k(n, k-1);
      tmpSubset.clear(n);
    }
  }



  /**
   * Compute the number of combinations of n choose k.
   * 
   * @param n
   * @param k
   * @return n choose k
   */
  private static int combinations(int n, int k)
  {
    int nominator = 1;
    int denominator = 1;

    do
    {
      nominator *= n--;
      denominator *= k;
    }
    while (--k >= 1);
    return nominator / denominator;
  }
}
