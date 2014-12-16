package org.cse.visiri.communication;

import com.hazelcast.config.Config;
import com.hazelcast.core.*;

import com.hazelcast.transaction.TransactionContext;
import com.hazelcast.transaction.TransactionOptions;
import com.hazelcast.transaction.impl.Transaction;
import org.cse.visiri.util.Query;
import org.cse.visiri.util.QueryDistribution;
import org.cse.visiri.util.StreamDefinition;
import org.cse.visiri.util.Utilization;

import java.util.*;
import java.util.Queue;

/**
 * Created by Geeth on 2014-10-31.
 */
public class Environment implements MessageListener {

    public static final int NODE_TYPE_PROCESSINGNODE = 1;
    public static final int NODE_TYPE_DISPATCHER = 2;

    public static final int EVENT_TYPE_QUERIES_CHANGED = 1;
    public static final int EVENT_TYPE_NODES_CHANGED = 2;
    public static final int EVENT_TYPE_BUFFERINGSTATE_CHANGED = 3;
    public static final int EVENT_TYPE_EVENTSUBSCIBER_CHANGED = 4;
    public static final int EVENT_TYPE_NODE_START = 5;
    public static final int EVENT_TYPE_NODE_STOP = 6;
    public static final int EVENT_TYPE_ENGINE_PASS = 7;

    private final String UTILIZATION_MAP = "UTILIZATION_MAP";
    private final String NODE_QUERY_MAP = "NODE_QUERY_MAP";
    private final String ORIGINAL_TO_DEPLOYED_MAP = "ORIGINAL_TO_DEPLOYED_MAP";
    private final String SUBSCRIBER_MAP = "SUBSCRIBER_MAP";
    private final String NODE_LIST = "NODE_LIST";
    private final String PERSISTENCE_MAP = "persistenceMap";
    private final String REVISION_MAP = "revisionMap";


    private List<String> bufferingEventList = null;
    private EnvironmentChangedCallback changedCallback = null;
    private static HazelcastInstance hzInstance = null;
    private static Environment instance = null;

    private static TransactionOptions options=null;
    private static TransactionContext transaction =null;



    private ITopic<Object> topic;

    private Environment() {
        Config cfg = new Config();
        hzInstance = Hazelcast.newHazelcastInstance(cfg);



        bufferingEventList = new ArrayList<String>();

        topic = hzInstance.getTopic ("VISIRI");
        topic.addMessageListener(this);

    }
    public void setNodeType(int nodeType){
        hzInstance.getMap(NODE_LIST).put(getNodeId(),nodeType);
    }

    public int getNodeType(){
        return (Integer.parseInt(hzInstance.getMap(NODE_LIST).get(getNodeId()).toString()));
    }


    public void setChangedCallback(EnvironmentChangedCallback callback) {
        this.changedCallback = callback;
    }

    public EnvironmentChangedCallback getChangedCallback() {
        return changedCallback;
    }

    public void setNodeUtilization(String nodeIp, Double value) {
        hzInstance.getMap(UTILIZATION_MAP).put(nodeIp, value);
    }

    public void addQueryDistribution(QueryDistribution queryDistribution) {
        options = new TransactionOptions().setTransactionType( TransactionOptions.TransactionType.LOCAL );
        transaction= hzInstance.newTransactionContext(options);
        transaction.beginTransaction();
        //Adding to originalQueryToDeployedQueryM

        Map<Query, List<Query>> generatedQueries = queryDistribution.getGeneratedQueries();
        int xx = 0;
        System.out.print("adding query map....");
        for (Query query : generatedQueries.keySet()) {
            transaction.getMap(ORIGINAL_TO_DEPLOYED_MAP).put(query, generatedQueries.get(query));
            if(++xx % 10 == 0)
            {
                System.out.print(xx+ " ");
            }
        }
        System.out.println(" done.");
        //Adding to nodeT7oQueriesMap
        Map<Query, String> queryAllocation = queryDistribution.getQueryAllocation();
        System.out.print("put query allocation map....");
        xx =0;

        Map<String,List<Query>> nodeQueryMap = new HashMap<String, List<Query>>( transaction.getMap(NODE_QUERY_MAP));

        for (Query query : queryAllocation.keySet()) {
            String ip = queryAllocation.get(query);
            List<Query> queryList = (List<Query>) nodeQueryMap.get(ip));//.get(ip);

            if (queryList == null) {
                queryList = new ArrayList<Query>();
            }

            queryList.add(query);
            transaction.getMap(NODE_QUERY_MAP).put(ip, queryList);
            if(++xx % 10 == 0)
            {
                System.out.print(xx+ " ");
            }
        }
        System.out.println(" done.");
        transaction.commitTransaction();
        System.out.println("-- commited---");
        transaction = null;
    }

    /**
     * Singleton Accessor *
     */
    public static Environment getInstance() {
        if (instance == null) {
            instance = new Environment();
        }
        return instance;
    }

    public List<String> getNodeIdList(int nodeType) {
        Set<Member> t = hzInstance.getCluster().getMembers();
        List<String> nodeIdList = new ArrayList<String>();

        for (Member member : t) {
            String ip=member.getInetSocketAddress().getHostString();

            if(nodeType == hzInstance.getMap(NODE_LIST).get(ip))
                nodeIdList.add(ip);
        }
        return nodeIdList;
    }

    public Map<Query, List<Query>> getOriginalToDeployedQueriesMapping() {
        return hzInstance.getMap(ORIGINAL_TO_DEPLOYED_MAP);
    }

    public Map<String, Map<String, byte[]>> getPersistenceMapping() {
        return hzInstance.getMap(PERSISTENCE_MAP);
    }

    public Map<String, List<String>> getRevisionMapping() {
        return hzInstance.getMap(REVISION_MAP);
    }

    public void stop(){
        hzInstance.shutdown();
        instance=null;
    }

    public String getNodeId() {
        return hzInstance.getCluster().getLocalMember().getInetSocketAddress().getHostString();
    }

    public Map<String, List<Query>> getNodeQueryMap() {
        return hzInstance.getMap(NODE_QUERY_MAP);
    }


    public Map<String, Utilization> getNodeUtilizations() {
        return hzInstance.getMap(UTILIZATION_MAP);
    }

    public void setNodeUtilizations(Utilization utilization) {
        hzInstance.getMap(UTILIZATION_MAP).put(getNodeId(),utilization);
    }

    public List<String> getBufferingEventList() {
        return bufferingEventList;
    }

    public Map<String, List<String>> getSubscriberMapping() {
        return hzInstance.getMap(SUBSCRIBER_MAP);
    }

    public Map<String, List<String>> getEventNodeMapping() {

        transaction.beginTransaction();

        TransactionalMap<String,List<Query>> nodeQueryMap=transaction.getMap(NODE_QUERY_MAP);
        Map<String,Set<String>> eventNodeMap=new HashMap<String, Set<String>>();
        Map<String,List<String>> eventNodeMap2=new HashMap<String, List<String>>();

        //For all IPs in the node query map
        for(Object ob: getNodeIdList(NODE_TYPE_PROCESSINGNODE)){
            String ip=(String)ob;

            //For all queries of a specific ip
            for(Query query : nodeQueryMap.get(ip) ){

                //For all StreamDefinitions of a query
                for(StreamDefinition streamDefinition : query.getInputStreamDefinitionsList()) {

                    Set<String> ipList=eventNodeMap.get(streamDefinition.getStreamId());
                    if(ipList==null){
                        ipList=new HashSet<String>();
                    }
                    ipList.add(ip);
                    eventNodeMap.put(streamDefinition.getStreamId(), ipList);
                }
            }
        }

        //Converting Set to List
        for(String stream : eventNodeMap.keySet()){
            Set<String> ipSet=eventNodeMap.get(stream);
            List<String> iplist = new ArrayList<String>(ipSet);
            eventNodeMap2.put(stream,iplist);
        }

        transaction.commitTransaction();
        return eventNodeMap2;
    }

    @Override
    public void onMessage(Message event) {
        System.out.println("Message Recieved "+event.getMessageObject());

        MessageObject messageObject=(MessageObject)event.getMessageObject();
        int eventType=messageObject.getEventType();

        switch (eventType){
            case Environment.EVENT_TYPE_BUFFERINGSTATE_CHANGED:
                changedCallback.bufferingStateChanged();
                break;
            case Environment.EVENT_TYPE_QUERIES_CHANGED:
                changedCallback.queriesChanged();
                break;
            case Environment.EVENT_TYPE_NODES_CHANGED:
                changedCallback.nodesChanged();
                break;
            case Environment.EVENT_TYPE_EVENTSUBSCIBER_CHANGED:
                changedCallback.eventSubscriberChanged();
                break;
            case Environment.EVENT_TYPE_NODE_START:
                changedCallback.startNode();
                break;
            case Environment.EVENT_TYPE_NODE_STOP:
                changedCallback.stopNode();
                break;
            case Environment.EVENT_TYPE_ENGINE_PASS:
                if(messageObject.getDestination().equals(getNodeId()))
                    changedCallback.newEngineRecieved(messageObject.getPersistedEngine());
                break;
        }

    }

    public void sendEvent(int eventType){
       topic.publish(new MessageObject(eventType));
    }

    public void sendEngine(Query query,String destination){
        topic.publish(new MessageObject(Environment.EVENT_TYPE_ENGINE_PASS,query,destination));
    }




//    public static void main(String args[]) {
//
//    }
}
