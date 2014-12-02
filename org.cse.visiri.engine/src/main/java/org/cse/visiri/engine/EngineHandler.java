package org.cse.visiri.engine;

import com.hazelcast.map.client.MapGetEntryViewRequest;
import org.cse.visiri.algo.AlgoFactory;
import org.cse.visiri.algo.QueryDistributionAlgo;
import org.cse.visiri.communication.Environment;
import org.cse.visiri.communication.eventserver.client.EventClient;
import org.cse.visiri.communication.eventserver.server.EventServer;
import org.cse.visiri.communication.eventserver.server.EventServerConfig;
import org.cse.visiri.communication.eventserver.server.StreamCallback;
import org.cse.visiri.util.*;


import java.util.*;

/**
 * Created by Malinda Kumarasinghe on 10/31/2014.
 */
public class EngineHandler {
    private Map<String,CEPEngine> queryEngineMap;
    private Map<String,List<CEPEngine>> eventEngineMap;
    private OutputEventReceiver outputEventReceiver;
    private Map<String,StreamDefinition> streamDefinitionMap;
    private EventServerConfig eventServerConfig;
    private List<Query> myQueryList;
    private EventServer eventServer;
    private TransferbleQuery transferbleQuery;


    public EngineHandler(){
        this.queryEngineMap=new HashMap<String, CEPEngine>();
        this.eventEngineMap=new HashMap<String, List<CEPEngine>>();
        this.outputEventReceiver=new OutputEventReceiver();
        this.streamDefinitionMap=new HashMap<String, StreamDefinition>();
        this.eventServerConfig=new EventServerConfig(7211);
        this.outputEventReceiver=new OutputEventReceiver();
        this.myQueryList=new ArrayList<Query>();
        this.transferbleQuery=new TransferbleQuery();
    }

    public void start() throws Exception {

        try {
            this.configureOutputEventReceiver();
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<StreamDefinition> streamDefinitionList=new ArrayList<StreamDefinition>();
        Set<String> streamIdSet=streamDefinitionMap.keySet();

        for(String streamId: streamIdSet){
            streamDefinitionList.add(streamDefinitionMap.get(streamId));
        }

        eventServer=new EventServer(eventServerConfig,streamDefinitionList,new StreamCallback() {
            @Override
            public void receive(Event event) {
                List<CEPEngine> cepEngineList=eventEngineMap.get(event.getStreamId());
                for(int i=0;i<cepEngineList.size();i++){
                    cepEngineList.get(i).sendEvent(event);
                }
            }
        },"EngineHandler");
        eventServer.start();

    }

    public Map<String,String> getTransferableEngines(){
        //1. get transferable queries from TransferableQueries class
        //2. run Dynamic distribution algorithm to get query distribution algorithm
        //3. call "removeEngine" method for all transferable engines
        //4. return new dynamic query distribution

        String myNode= Environment.getInstance().getNodeId();
        Map<String,List<Query>> nodeQueryMap=Environment.getInstance().getNodeQueryMap();
        List<Query> queryList=nodeQueryMap.get(myNode);
        double[] eventRates=new double[queryList.size()];
        int i=0;
        for(Query query:queryList){
            SiddhiCEPEngine cepEngine=(SiddhiCEPEngine)queryEngineMap.get(query.getQueryId());
            eventRates[i]=cepEngine.getAvgEventRate();
            i++;
        }

        List<Query> transferbleQueryList=transferbleQuery.detectTransferbleQuery(eventRates,queryList);
        QueryDistributionAlgo queryDistributionAlgo= AlgoFactory.createAlgorithm(QueryDistributionAlgo.SCTXPF_ALGO);
        QueryDistribution queryDistribution=queryDistributionAlgo.getQueryDistribution(transferbleQueryList);

        Map<Query,String> queryAllocation=queryDistribution.getQueryAllocation();
        Map<String,String> transferbleEnginesMap=new HashMap<String, String>();

        for(Query query:transferbleQueryList){
            SiddhiCEPEngine siddhiCEPEngine=(SiddhiCEPEngine)queryEngineMap.get(query.getQueryId());


            //Add siddhiEngine to buffering list and remove engine from engineHandler
            addToBufferingList(siddhiCEPEngine);

            String engine=(String)siddhiCEPEngine.saveState();
            transferbleEnginesMap.put(queryAllocation.get(query),engine);

            siddhiCEPEngine.stop();

        }
        //Sending buffering message to dispatcher
        Environment.getInstance().sendEvent(Environment.EVENT_TYPE_BUFFERINGSTATE_CHANGED);

        return transferbleEnginesMap;

    }

    private void addToBufferingList(SiddhiCEPEngine siddhiCEPEngine){
        List<String> bufferingList=Environment.getInstance().getBufferingEventList();
        List<StreamDefinition> strmDefs=siddhiCEPEngine.getInputStreamDefinitionList();

        for(StreamDefinition strdrf : strmDefs){
            bufferingList.add(strdrf.getStreamId());
           // Environment.getInstance().getBufferingEventList().add(strdrf.getStreamId());
        }
    }



    public void stop(){

    }

    public void sendEvents(){

    }

    public void addQuery(Query query){

       CEPEngine cepEngine=CEPFactory.createEngine(query.getEngineId(), query,outputEventReceiver);
       queryEngineMap.put(query.getQueryId(),cepEngine);

        System.out.print("Adding new query :");

       List<StreamDefinition> inputStreamDefinitionList=query.getInputStreamDefinitionsList();
       for(int i=0;i<inputStreamDefinitionList.size();i++){
           StreamDefinition streamDefinition=inputStreamDefinitionList.get(i);
           setEnginesToEvents(streamDefinition.getStreamId(),cepEngine);
           streamDefinitionMap.put(streamDefinition.getStreamId(), streamDefinition);
           System.out.print(" In:"+inputStreamDefinitionList.get(i).getStreamId());
       }
        System.out.println(" Out:"+query.getOutputStreamDefinition().getStreamId());
        this.myQueryList.add(query);
    }

    public void eventServerBufferChanged(List<String> bufferingEventList){
        eventServer.bufferStateChanged(bufferingEventList);
    }

    public void addQueryList(List<Query> queryList){
        for(Query query: queryList){
            addQuery(query);

//            CEPEngine cepEngine=CEPFactory.createEngine(query.getEngineId(), query,outputEventReceiver);
//            queryEngineMap.put(query.getQueryId(),cepEngine);
//
//            List<StreamDefinition> inputStreamDefinitionList=query.getInputStreamDefinitionsList();
//            for(int i=0;i<inputStreamDefinitionList.size();i++){
//                StreamDefinition streamDefinition=inputStreamDefinitionList.get(i);
//                setEnginesToEvents(streamDefinition.getStreamId(),cepEngine);
//                streamDefinitionMap.put(streamDefinition.getStreamId(), streamDefinition);
//            }
//
//            this.myQueryList.add(query);
        }
    }

    public void dynamicAddQuery(String persistedEngine) throws Exception {
        //todo Should be changed
        //public void dynamicAddQuery(Query query) throws Exception {
        Query query=null;
        CEPEngine cepEngine=CEPFactory.createEngine(query.getEngineId(), query,outputEventReceiver);
        queryEngineMap.put(query.getQueryId(),cepEngine);


        List<StreamDefinition> inputStreamDefinitionList=query.getInputStreamDefinitionsList();
        for(int i=0;i<inputStreamDefinitionList.size();i++){
            StreamDefinition streamDefinition=inputStreamDefinitionList.get(i);
            setEnginesToEvents(streamDefinition.getStreamId(),cepEngine);
            streamDefinitionMap.put(streamDefinition.getStreamId(), streamDefinition);
            eventServer.addStreamDefinition(streamDefinition);
        }
        this.myQueryList.add(query);

        Map<String,List<String>> destinationNodeMap;

        //Have to check about whether dispatcher or processing node
        if(Environment.getInstance().getNodeType()==Environment.NODE_TYPE_DISPATCHER){
            destinationNodeMap=Environment.getInstance().getEventNodeMapping();
        }else {
            destinationNodeMap=Environment.getInstance().getSubscriberMapping();
        }


        StreamDefinition outputStreamDefiniton=query.getOutputStreamDefinition();
        String streamId=outputStreamDefiniton.getStreamId();
        List<String> nodeIpList=destinationNodeMap.get(streamId);

        Map<String,List<EventClient>> eventToClientsMap=outputEventReceiver.getEventToClientsMap();

        if(!eventToClientsMap.containsKey(streamId)){
            for(String nodeIp: nodeIpList){
                List<StreamDefinition> streamDefinitionList=new ArrayList<StreamDefinition>();
                streamDefinitionList.add(outputStreamDefiniton);
                if(!nodeIp.contains(":")){
                    nodeIp=nodeIp+":"+EventServer.DEFAULT_PORT;
                }
                EventClient eventClient=new EventClient(nodeIp,streamDefinitionList);
                outputEventReceiver.addDestinationIp(nodeIp,eventClient);
            }
        }

    }

    public void dynamicRemoveQuery(Query query){

        CEPEngine siddhiCepEngine=(SiddhiCEPEngine)this.queryEngineMap.get(query.getQueryId());
        this.queryEngineMap.remove(query.getQueryId());

        //TODO have to implement persistent siddhi instance
    }

    public Map<String, CEPEngine> getQueryEngineMap() {
        return queryEngineMap;
    }


    public Map<String, List<CEPEngine>> getEventEngineMap() {
        return eventEngineMap;
    }


    public OutputEventReceiver getOutputEventReceiver() {
        return outputEventReceiver;
    }

    private void setEnginesToEvents(String streamId,CEPEngine cepEngine){

        if(eventEngineMap.containsKey(streamId)){
            eventEngineMap.get(streamId).add(cepEngine);
        }else{
            List<CEPEngine> cepEngineList=new ArrayList<CEPEngine>();
            cepEngineList.add(cepEngine);
            eventEngineMap.put(streamId,cepEngineList);
        }
    }

    private void configureOutputEventReceiver() throws Exception {
        //String myIp= Environment.getInstance().getNodeId();
        //List<Query> myQueryList=Environment.getInstance().getNodeQueryMap().get(myIp);
        List<StreamDefinition> ouputStreamDefinitionList=new ArrayList<StreamDefinition>();
        Map<String,List<StreamDefinition>> nodeStreamDefinitionListMap=new HashMap<String, List<StreamDefinition>>();

        for(Query q : myQueryList){
            ouputStreamDefinitionList.add(q.getOutputStreamDefinition());
        }

        Map<String,List<String>> destinationNodeMap;

        //Have to check about whether dispatcher or processing node
        if(Environment.getInstance().getNodeType()==Environment.NODE_TYPE_DISPATCHER){
            destinationNodeMap=Environment.getInstance().getEventNodeMapping();
        }else {
            destinationNodeMap=Environment.getInstance().getSubscriberMapping();
        }


        for(StreamDefinition streamDefinition : ouputStreamDefinitionList){
            String streamId = streamDefinition.getStreamId();
            List<String> nodeIpList=destinationNodeMap.get(streamId);
            if(nodeIpList != null) {

                for (String nodeIp : nodeIpList) {
                    if (nodeStreamDefinitionListMap.containsKey(nodeIp)) {
                        List<StreamDefinition> streamDefinitionList = nodeStreamDefinitionListMap.get(nodeIp);
                        streamDefinitionList.add(streamDefinition);
                    } else {
                        List<StreamDefinition> streamDefinitionList = new ArrayList<StreamDefinition>();
                        streamDefinitionList.add(streamDefinition);
                        nodeStreamDefinitionListMap.put(nodeIp, streamDefinitionList);
                    }
                }
            }
        }

        Set<String> nodeIpSet=nodeStreamDefinitionListMap.keySet();
        for (String nodeIp :  nodeIpSet){
            String fullIp = nodeIp;
            if(!nodeIp.contains(":")){
                fullIp=nodeIp+":"+EventServer.DEFAULT_PORT;
            }
            EventClient eventClient=new EventClient(fullIp,nodeStreamDefinitionListMap.get(nodeIp));
            outputEventReceiver.addDestinationIp(nodeIp,eventClient);
        }
    }
}
