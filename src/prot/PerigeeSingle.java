/*
 * Created on Jan 14, 2022 by Spyros Voulgaris
 *
 */
package prot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import peernet.core.CommonState;
import peernet.core.Network;
import peernet.core.Peer;

/**
 * Scores peers, and periodically calibrates neighbors.
 * Basically this is the class to run the Perigee model.
 * 
 * @author spyros
 *
 */
public abstract class PerigeeSingle extends Perigee
{
  /*
   * Structure storing the scoring of individual nodes.
   * Maps each node ID to its score (an integer).
   */
  HashMap<Integer, Integer> scoring = new HashMap<Integer, Integer>();



  public PerigeeSingle(String prefix)
  {
    super(prefix);
  }



  public Object clone()
  {
    PerigeeSingle d = (PerigeeSingle) super.clone();
    d.scoring = (HashMap<Integer, Integer>) scoring.clone();
    return d;
  }



  public void calibrate()
  {
    // Remove weakest link, if scores are in place
    if (scoring.size() > 0 & weakestLinks > 0)
    {
      // First, store the scores into an ArrayList, sort it, and find the k-th weakest one
      List<Integer> scores = new ArrayList<Integer>(scoring.values());
      Collections.sort(scores);
      int minScore = scores.get(weakestLinks-1);

      // Then, go through all <ID,score> tuples and store the IDs of the weakest links into 'minScoreIds'
      ArrayList<Integer> minScoreIds = new ArrayList<>();
      for (Map.Entry<Integer,Integer> entry: scoring.entrySet())
      {
        int score = entry.getValue();
        if (score<=minScore)
        {
          int id = entry.getKey();
          minScoreIds.add(id);
          if (minScoreIds.size() >= weakestLinks)
            break;
        }
      }

      // Finally, properly remove the (bidirectional) links between me and each of the weakest peers
      for (int id: minScoreIds)
      {
        // remove this peer from me
        Peer weakPeer = id2peer(id);
        outgoingSelections.remove(weakPeer);
        removeDownstreamPeer(weakPeer);

        // remove me from other peer
        PerigeeSingle weakProt = (PerigeeSingle) peer2prot(weakPeer);
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
      PerigeeSingle newProt = (PerigeeSingle) peer2prot(newPeer);
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
    scoring.clear();
    for (Peer p: outgoingSelections)
      scoring.put((int) p.getID(), 0);
  }

}
