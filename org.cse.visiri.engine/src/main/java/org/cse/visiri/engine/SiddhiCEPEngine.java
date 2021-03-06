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

package org.cse.visiri.engine;

import org.cse.visiri.util.Event;
import org.cse.visiri.util.EventRateStore;
import org.cse.visiri.util.Query;
import org.cse.visiri.util.StreamDefinition;
import org.wso2.siddhi.core.SiddhiManager;
import org.wso2.siddhi.core.config.SiddhiConfiguration;
import org.wso2.siddhi.core.persistence.PersistenceStore;
import org.wso2.siddhi.core.stream.input.InputHandler;
import org.wso2.siddhi.core.stream.output.StreamCallback;
import org.wso2.siddhi.query.api.definition.Attribute;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;



public class SiddhiCEPEngine extends CEPEngine {

    private SiddhiManager siddhiManager;
    private Query query;
    private OutputEventReceiver outputEventReceiver;
    public  EventRateStore eventRateStore;


    public SiddhiCEPEngine(Query query,OutputEventReceiver outputEventReceiver){
        eventRateStore=new EventRateStore();
        this.query=query;
        this.outputEventReceiver=outputEventReceiver;
        this.start();
    }

    public double getAvgEventRate(){
        return eventRateStore.getAverageRate();
    }

    public double getInstantEventRate(){
        return eventRateStore.getInstantRate();
    }

    @Override
    public void start() {

        SiddhiConfiguration configuration = new SiddhiConfiguration();
        configuration.setQueryPlanIdentifier(query.getQueryId());
        configuration.setAsyncProcessing(false);
        this.siddhiManager=new SiddhiManager(configuration);
        PersistenceStore persistenceStore = new SiddhiDistributedPersistenceHandler();
        siddhiManager.setPersistStore(persistenceStore);


        List<StreamDefinition> inputStreamDefinitionList=query.getInputStreamDefinitionsList();
        String queryString=query.getQuery();
        String outputStreamId=query.getOutputStreamDefinition().getStreamId();

        for(int i=0;i<inputStreamDefinitionList.size();i++){
            org.wso2.siddhi.query.api.definition.StreamDefinition streamDefinition;
            streamDefinition = new org.wso2.siddhi.query.api.definition.StreamDefinition();
            streamDefinition = streamDefinition.name(inputStreamDefinitionList.get(i).getStreamId());

            List<StreamDefinition.Attribute> attributeList=inputStreamDefinitionList.get(i).getAttributeList();

            for(int j=0;j<attributeList.size();j++){
                StreamDefinition.Type type=attributeList.get(j).getType();
                Attribute.Type type1;
                if(type.equals(StreamDefinition.Type.STRING)){
                    type1= Attribute.Type.STRING;
                }else if (type.equals(StreamDefinition.Type.INTEGER)){
                    type1=Attribute.Type.INT;
                }else if(type.equals(StreamDefinition.Type.DOUBLE)){
                    type1=Attribute.Type.DOUBLE;
                }else if(type.equals(StreamDefinition.Type.FLOAT)){
                    type1=Attribute.Type.FLOAT;
                }else if (type.equals(StreamDefinition.Type.LONG)){
                    type1=Attribute.Type.LONG;
                }else if (type.equals(StreamDefinition.Type.BOOLEAN)){
                    type1=Attribute.Type.BOOL;
                }else{
                    type1=Attribute.Type.TYPE;
                }

                streamDefinition.attribute(attributeList.get(j).getName(),type1 );
            }
           siddhiManager.defineStream(streamDefinition);

        }

        siddhiManager.addQuery(queryString);
        siddhiManager.addCallback(outputStreamId,new StreamCallback() {
            @Override
            public void receive(org.wso2.siddhi.core.event.Event[] events) {
                //to do with output stream

                for(int i=0;i<events.length;i++){
                    Event event=new Event();
                    event.setStreamId(events[i].getStreamId());
                    event.setData(events[i].getData());
                    try {
                        try {

                            outputEventReceiver.sendEvents(event);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

    }

    @Override
    public void restoreEngine(){
        siddhiManager.restoreLastRevision();
    }

    @Override
    public void stop() {
        siddhiManager.shutdown();
    }

    @Override
    public Object saveState() {
         return siddhiManager.persist();
    }

    @Override
    public void sendEvent(Event event) {
        //eventRateStore.increment();
        InputHandler inputHandler=siddhiManager.getInputHandler(event.getStreamId());
        //System.out.println(event.getStreamId());
//        System.out.println("Engine is Query ID : "+query.getQueryId());
        try {

            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("VISIRI_check.txt", true)));
            out.println(query.getQueryId());
            out.close();

        } catch (IOException e) {
            e.printStackTrace();
        }


        try {
            inputHandler.send(event.getData());
        }catch (InterruptedException e){
            e.printStackTrace();
        }
    }

    public List<StreamDefinition> getInputStreamDefinitionList(){
        return query.getInputStreamDefinitionsList();
    }

}
