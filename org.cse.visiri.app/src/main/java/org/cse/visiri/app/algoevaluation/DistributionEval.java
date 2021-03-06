/*
 * Copyright 2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cse.visiri.app.algoevaluation;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.cse.visiri.algo.*;
import org.cse.visiri.app.util.FilteredQueryGenerator;
import org.cse.visiri.util.Query;
import org.cse.visiri.util.QueryDistribution;
import org.cse.visiri.util.StreamDefinition;
import org.cse.visiri.util.costmodelcalc.CostModelCalculator;
import org.cse.visiri.util.costmodelcalc.FastSiddhiCostModelCalculator;
import org.cse.visiri.util.costmodelcalc.SiddhiCostModelCalculator;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;


public class DistributionEval {

    final String DISP_PREFIX = "20.1.";
    final String NODE_PREFIX = "10.1.";

    public void EvaluateDistribution(QueryDistribution dist,String algoname)
    {
        Map<String,Integer> allInfo = new TreeMap<String, Integer>();
        Map<String,Integer> nodeInfo = new TreeMap<String, Integer>();
        Map<String,Double> nodeCosts = new TreeMap<String, Double>();
        for(Query q: dist.getQueryAllocation().keySet())
        {
            String node = dist.getQueryAllocation().get(q);
            if(!allInfo.containsKey(node))
            {
                allInfo.put(node, 0);
            }
            int val= allInfo.get(node);
            allInfo.put(node, val + 1);
            if(node.startsWith(NODE_PREFIX))
            {
                if(!nodeInfo.containsKey(node))
                {
                    nodeInfo.put(node, 0);
                    nodeCosts.put(node,0.0);
                }
                val= nodeInfo.get(node);
                nodeInfo.put(node, val + 1);
                nodeCosts.put(node, nodeCosts.get(node) + q.getCost());
            }
        }

        DescriptiveStatistics stat = new DescriptiveStatistics();
        DescriptiveStatistics costStat = new DescriptiveStatistics();
        System.out.println("Query counts : ");
        for(String node: nodeInfo.keySet())
        {
            System.out.println(node + " : " + allInfo.get(node));
            stat.addValue(nodeInfo.get(node));
            costStat.addValue(nodeCosts.get(node));
        }


        System.out.println();
        double mean= stat.getMean();
        double stdDev = Math.sqrt(stat.getPopulationVariance());
        double varCoef = stdDev/mean;
        System.out.println("mean : " + mean);
        System.out.println("stdDev : " + stdDev);
        System.out.println("Coefficient of var : " + varCoef);


        System.out.println("\nCosts :");
        mean= costStat.getMean();
        stdDev = Math.sqrt(costStat.getPopulationVariance());
        varCoef = stdDev/mean;
        System.out.println("mean : " + mean);
        System.out.println("stdDev : " + stdDev);
        System.out.println("Coefficient of var : " + varCoef);

        //calculate event duplication
        Map<String,Set<String>> eventMap = new TreeMap<String, Set<String>>();
        for(Query q: dist.getQueryAllocation().keySet() )
        {
            String targetNode = dist.getQueryAllocation().get(q);

            for(StreamDefinition def: q.getInputStreamDefinitionsList())
            {
                if(!eventMap.containsKey(def.getStreamId()))
                {
                    eventMap.put(def.getStreamId(),new HashSet<String>());
                }
                eventMap.get(def.getStreamId()).add(targetNode);
            }
        }

        stat = new DescriptiveStatistics();
        for(Set<String> nodes : eventMap.values())
        {
            stat.addValue(nodes.size());
        }

        double avg = stat.getMean();

        System.out.println();
        System.out.println("Avg. event duplication " + avg);


        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("VISIRI_algoeval.txt", true)));
            out.println(stdDev);
            out.close();
        }catch (IOException e){
            e.printStackTrace();
        }

        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("VISIRI_eventDup.txt", true)));
            out.println(avg);
            out.close();
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    public List<String> generateNodeList(int count,boolean dispatcher)
    {
        String prefix = dispatcher? DISP_PREFIX : NODE_PREFIX;
        List<String> nodes = new ArrayList<String>();

        for(int i=1; i <= count; i++)
        {
            String newNode = prefix + i/10+ "." + i% 10;
            nodes.add(newNode);
        }

        return nodes;
    }

    public void start()
    {
        int seed = 999;
        double complexity= 4.1;

        int inputDefCount = 1000, outputDefCount = 500;
        FilteredQueryGenerator qg = new FilteredQueryGenerator(seed,complexity);

        List<Integer> queryCounts = Arrays.asList(1000,5000,10000);
        Map<String,QueryDistributionAlgo> algos = new HashMap<String, QueryDistributionAlgo>();
        algos.put("Random", new RandomDistributionAlgo());
        algos.put("SCTXPF",new SCTXPFDistributionAlgo());
        algos.put("SCTXPF+", new SCTXPFPlusDistributionAlgo());

        List<Integer> nodeCounts = Arrays.asList(4,8,12,16);


        List<StreamDefinition> inDef,outDef;
        inDef= qg.generateDefinitions(inputDefCount,3,5);
        outDef = qg.generateDefinitions(outputDefCount,1,3);
        List<Query> queries;

        for(int queryCount : queryCounts) {
            queries = qg.generateQueries(queryCount,inDef,outDef);

            CostModelCalculator costCal = new CostModelCalculator();
            FastSiddhiCostModelCalculator fastCal = new FastSiddhiCostModelCalculator();
            System.out.print("Calculating costs ");
            int cnt =0;
            for(Query q: queries)
            {
                cnt++;
                double cost =fastCal.calculateCost(q);

                q.setCost(cost);
                if(cnt%100 == 0)
                {
                    System.out.print(".");
                }
            }
            System.out.println(" - done!");

            System.out.println( "************ " + queryCount +" queries ***********");
            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("VISIRI_algoeval.txt", true)));
                out.println("\n*************\nQuery count : " +queryCount);
                out.close();
            }catch (IOException e){
                e.printStackTrace();
            }
            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("VISIRI_eventDup.txt", true)));
                out.println("\n*************\nQuery count : " +queryCount);
                out.close();
            }catch (IOException e){
                e.printStackTrace();
            }
            for (int nodeCount : nodeCounts) {
                System.out.println("-----------" + nodeCount + " nodes --------------");
                try {
                    PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("VISIRI_algoeval.txt", true)));
                    out.println("\nNode count : " +nodeCount);
                    out.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
                try {
                    PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("VISIRI_eventDup.txt", true)));
                    out.println("\nNode count : " +nodeCount);
                    out.close();
                }catch (IOException e){
                    e.printStackTrace();
                }

                for (String algoName : algos.keySet()) {
                    QueryDistributionAlgo algo = algos.get(algoName);

                    QueryDistributionParam param = new QueryDistributionParam();

                    param.setNodeList(generateNodeList(nodeCount, false));
                    param.setDispatcherList(generateNodeList(1, true));
                    param.setQueries(queries);
                    param.setNodeQueryTable(new HashMap<String, List<Query>>());

                    QueryDistribution dist = algo.getQueryDistribution(param);

                    System.out.println("-- " + algoName + " --");

                    EvaluateDistribution(dist,algoName);
                    System.out.println();
                }
            }
        }

    }

    public static  void main(String[] arg)
    {
        DistributionEval ev = new DistributionEval();
        ev.start();
    }
}
