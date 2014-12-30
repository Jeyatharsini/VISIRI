package org.cse.visiri.algo;

import org.cse.visiri.communication.Environment;
import org.cse.visiri.util.DynamicQueryDistribution;
import org.cse.visiri.util.Query;
import org.cse.visiri.util.StreamDefinition;
import org.cse.visiri.util.Utilization;
import org.cse.visiri.util.costmodelcalc.CostModelCalculator;

import java.util.*;

/**
 * Created by Geeth on 2014-12-01.
 */
public class DynamicQueryDistributionAlgoImpliment extends DynamicQueryDistributionAlgo{

    public final double costThreshold = 10;
    public final double utilizationThreshold = 10;


    @Override
    public Map<Query, String> getQueryDistribution(List<Query> queries) {
        Map<Query,String> dist = new HashMap<Query, String>();
        for(Query q: queries)
        {
            String node = getQueryDistribution(q);
            dist.put(q,node);
        }

        return dist;
    }

    @Override
    public String getQueryDistribution(Query query) {
        DynamicQueryDistribution dist = new DynamicQueryDistribution();
        Environment env = Environment.getInstance();

        Random randomizer = new Random();

        CostModelCalculator costCal = new CostModelCalculator();

        Map<String,List<Query>> nodeQueryTable = new HashMap<String, List<Query>>(env.getNodeQueryMap());
        List<String> nodeList = new ArrayList<String>(env.getNodeIdList(Environment.NODE_TYPE_PROCESSINGNODE));
        // Map<String,Utilization> utilizations = new HashMap<String, Utilization>(env.getNodeUtilizations());
        Map<String,Set<String>> nodeEventTypes = new HashMap<String, Set<String>>();
        Map<String,Double> costs = new HashMap<String, Double>();
        List<String> dispatcherList = new ArrayList<String>(env.getNodeIdList(Environment.NODE_TYPE_DISPATCHER));


        for(String str: nodeList)
        {
            //calculate costs of each node
            double cost = 0.0;

            if(!nodeQueryTable.containsKey(str))
            {
                nodeQueryTable.put(str,new ArrayList<Query>());
            }

            for(Query q: nodeQueryTable.get(str))
            {
                cost += costCal.calculateCost(q);
            }
            costs.put(str,cost);

            // calculate event type counts
            Set<String> eventTypes = new HashSet<String>();
            List<Query> existingQueries = nodeQueryTable.get(str);

            for(Query q: existingQueries)
            {
                for(StreamDefinition def : q.getInputStreamDefinitionsList())
                {
                    eventTypes.add(def.getStreamId());
                }
            }
            nodeEventTypes.put(str,eventTypes);

        }
        //store types of events in dispatcher list
        for(String str: dispatcherList)
        {
            if(!nodeQueryTable.containsKey(str))
            {
                nodeQueryTable.put(str,new ArrayList<Query>());
            }

            Set<String> eventTypes = new HashSet<String>();
            List<Query> existingQueries = nodeQueryTable.get(str);
            for(Query q: existingQueries)
            {
                for(StreamDefinition def : q.getInputStreamDefinitionsList())
                {
                    eventTypes.add(def.getStreamId());
                }
            }
            nodeEventTypes.put(str,eventTypes);
        }

        Set<String> candidateNodes = new HashSet<String>(nodeList);


        //minimum utilization
        Map<String, Utilization> utilizations = env.getNodeUtilizations();
        double maxUtil = Collections.max(utilizations.values(), new
                Comparator<Utilization>() {
                    @Override
                    public int compare(Utilization o1, Utilization o2) {
                        return (int) ( o1.getFreeMemoryPercentage() - o2.getFreeMemoryPercentage());
                    }
                }).getFreeMemoryPercentage();

        //filter ones above threshold
        for(Iterator<String> iter = candidateNodes.iterator() ; iter.hasNext() ;)
        {
            String nodeId = iter.next();
            if(utilizations.get(nodeId).getFreeMemoryPercentage()
                        > maxUtil - utilizationThreshold)
            {
                iter.remove();
            }
        }


        //minimum total cost
        double minCost = Collections.min(costs.values());

        //filter ones above threshold
        for(Iterator<String> iter = candidateNodes.iterator() ; iter.hasNext() ;)
        {
            String nodeId = iter.next();
            if(costs.get(nodeId) > minCost + costThreshold)
            {
                iter.remove();
            }
        }

        Set<String> usedEventTypes = new HashSet<String>();
        for(StreamDefinition def : query.getInputStreamDefinitionsList())
        {
            usedEventTypes.add(def.getStreamId());
        }

        //number of common event streams
        int commonMax = 0;
        List<String> maximumCommonEventNodes = new ArrayList<String>();
        //find nodes with maximum common event
        for(String node: candidateNodes)
        {
            //find common count
            Set<String> curTypes = new HashSet<String>(usedEventTypes);
            curTypes.retainAll(nodeEventTypes.get(node));
            int count = curTypes.size();

            if(count == commonMax)
            {
                maximumCommonEventNodes.add(node);
            }
            else if(count > commonMax)
            {
                commonMax = count;
                maximumCommonEventNodes.clear();
                maximumCommonEventNodes.add(node);
            }
        }

        candidateNodes.retainAll(maximumCommonEventNodes);

        // TODO : EVENT RATES

        // TODO : EVENT TYPES

        int randIndex = randomizer.nextInt(candidateNodes.size());
        String targetNode = candidateNodes.toArray(new String[candidateNodes.size()])[randIndex];

        return targetNode;
    }
}