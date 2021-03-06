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


package org.cse.visiri.core;

import org.cse.visiri.algo.*;
import org.cse.visiri.algo.util.UtilizationUpdater;
import org.cse.visiri.communication.Environment;
import org.cse.visiri.communication.EnvironmentChangedCallback;
import org.cse.visiri.engine.EngineHandler;
import org.cse.visiri.util.Configuration;
import org.cse.visiri.util.Query;
import org.cse.visiri.util.QueryDistribution;
import org.cse.visiri.util.costmodelcalc.CostModelCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;



//TODO how to receive Siddhi persist and restore
public class Node implements EnvironmentChangedCallback{

    private List<Query> queries;
    private EngineHandler engineHandler;
    private UtilizationUpdater utilizationUpdater;
    private boolean started ;
    private Agent agent;

    private GUICallback guiCallback;

    public  void initialize()
    {
        queries = new ArrayList<Query>();
        started = false;
        Environment.getInstance().setChangedCallback(this);
        Configuration.setNodeType(Environment.NODE_TYPE_PROCESSINGNODE);
        Environment.getInstance().setNodeType(Environment.NODE_TYPE_PROCESSINGNODE);

        utilizationUpdater = new UtilizationUpdater();
        utilizationUpdater.start();
        engineHandler = new EngineHandler("Node");
        agent=new Agent(engineHandler,utilizationUpdater);

    }

    public void start() throws Exception{
        while(!Environment.getInstance().checkReadyAllNodes())
        {
            Thread.sleep(2000);
        }
        Environment.getInstance().sendEvent(Environment.EVENT_TYPE_NODE_START);

    }

    public void stop() {
        engineHandler.stop();
        utilizationUpdater.stop();
        agent.stop();
        System.out.println("***Overall system average memory - "+agent.getOverallAverageMemory());
    }

    public void addQueries(List<Query> queries)
    {
        int algoId=QueryDistributionAlgo.SCTXPF_PLUS_ALGO;
        CostModelCalculator costCal = new CostModelCalculator();
        if(algoId==QueryDistributionAlgo.SCTXPF_PLUS_ALGO) {
            System.out.print("Calculating costs...");
            for (Query q : queries) {
                double cost = costCal.calculateCost(q);
                q.setCost(cost);
            }
            System.out.println("... Done!");
        }
        QueryDistributionAlgo algo = AlgoFactory.createAlgorithm(algoId);
        QueryDistributionParam params = QueryDistributionParam.fromEnvironment();
        params.setQueries(queries);
        QueryDistribution dist = algo.getQueryDistribution(params);
        Environment.getInstance().addQueryDistribution(dist);

        System.out.println("------Queries are distributed to Environment----");
        Environment.getInstance().sendEvent(Environment.EVENT_TYPE_QUERIES_CHANGED);
    }

    public void subscribeToStream(String eventID, String ip_port)
    {
        Map<String,List<String>> subMap = Environment.getInstance().getSubscriberMapping();
        List<String> subs= new ArrayList<String>();
        if( subMap.containsKey(eventID) )
        {
            subs = subMap.get(eventID);
        }
        subs.add(ip_port);
        subMap.put(eventID, subs);

    }
    public void unsubscribeFromStream(String eventID, String ip_port)
    {
        Map<String,List<String>> subMap = Environment.getInstance().getSubscriberMapping();
        subMap.get(eventID).remove(ip_port);
    }

    @Override
    public void queriesChanged() {

        String nodeID = Environment.getInstance().getNodeId();
        List<Query> newQuerySet = Environment.getInstance().getNodeQueryMap().get(nodeID);
        if(newQuerySet == null)
        {
            newQuerySet = new ArrayList<Query>();
        }
        List<Query> addedQueries = new ArrayList<Query>(newQuerySet);
        addedQueries.removeAll(queries);

        int count =0;
        for(Query q : addedQueries)
        {
            engineHandler.addQuery(q);
            if(++count % 20 == 0)
            {
                System.out.print(count+" ");
            }
        }
        queries.addAll(addedQueries);
        System.out.println("\nQueries changed. added " + addedQueries.size() + " queries" );
//        send ready message
        Environment.getInstance().setReady();
        if(guiCallback!=null) {
            guiCallback.queriesChanged();
        }
    }

    @Override
    public void nodesChanged() {

    }

    @Override
    public void bufferingStart() {

    }

//    @Override
//    public void bufferingStop() {
//
//    }


    @Override
    public void eventSubscriberChanged() {

    }


    @Override
    public void startNode() {
        agent.start();
       // System.out.println("Node trying to start. . . . .");
        if(!started)
        {
            try {
              //  System.out.println("EH");
                engineHandler.start();
              //  System.out.println("EH2");
                started = true;
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
       // System.out.println("Before agent started");

       // System.out.println("Node started");
    }

    @Override
    public void stopNode()
    {
        engineHandler.stop();
        utilizationUpdater.stop();
    }

    @Override
    public void newEnginesRecieved(String from) {
        //System.out.println("\n-------111  newEnginesReceived method : "+queries);
        List<Query> queries=Environment.getInstance().getAdditionalQueries();
        //System.out.println("\n-------newEnginesReceived method : "+queries);
        if(queries!=null){
            System.out.println("\n"+queries.size()+" engines received");
            engineHandler.addNewQueries(queries);
            this.queries.addAll(queries);
            guiCallback.newEnginesRecieved(from,queries.size());
        }
        guiCallback.newEnginesRecieved(from,0);
    }

    @Override
    public void dynamicCompleted() {

    }

    public List<Query> getQueries() {
        return engineHandler.getMyQueryList();
    }

    public UtilizationUpdater getUtilizationUpdater() {
        return utilizationUpdater;
    }

    public EngineHandler getEngineHandler() {
        return engineHandler;
    }

    public void setGuiCallback(GUICallback guiCallback) {
        this.guiCallback = guiCallback;
    }

    public void setAgentEnabled(boolean agentEnabled){
        agent.setAgentEnabled(agentEnabled);
    }

}
