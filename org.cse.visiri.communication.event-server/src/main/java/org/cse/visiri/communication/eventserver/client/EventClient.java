package org.cse.visiri.communication.eventserver.client;


import org.cse.visiri.communication.eventserver.server.EventServerUtils;
import org.cse.visiri.communication.eventserver.server.StreamRuntimeInfo;
import org.cse.visiri.util.Event;
import org.cse.visiri.util.StreamDefinition;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by visiri on 10/28/14.
 */


public class EventClient {

    private final String hostUrl;
    private final List<StreamDefinition> streamDefinitionsList;
    //private final StreamRuntimeInfo streamRuntimeInfo;
    private final HashMap<String,StreamRuntimeInfo> streamRuntimeInfoHashMap;
    private OutputStream outputStream;
    private Socket clientSocket;
    private BlockingQueue blockingQueue;


    public EventClient(String hostUrl, List<StreamDefinition> streamDefinitions) throws Exception {
        this.hostUrl = hostUrl;
        this.streamDefinitionsList = streamDefinitions;
        //this.streamRuntimeInfo = EventServerUtils.createStreamRuntimeInfo(streamDefinition);

        streamRuntimeInfoHashMap=new HashMap<String, StreamRuntimeInfo>();
        for(int i=0;i<streamDefinitions.size();i++){
            // this.streamRuntimeInfos[i]=EventServerUtils.createStreamRuntimeInfo(streamDefinitions[i]);
            streamRuntimeInfoHashMap.put(streamDefinitions.get(i).getStreamId(),EventServerUtils.createStreamRuntimeInfo(streamDefinitions.get(i)));
        }

        System.out.println("Sending to " + hostUrl);
        String[] hp = hostUrl.split(":");
        String host = hp[0];
        int port = Integer.parseInt(hp[1]);
        clientSocket = new Socket(host, port);
        //clientSocket = new Socket("10.219.122.189", 5180);
        outputStream = new BufferedOutputStream(clientSocket.getOutputStream());

        System.out.print("Event Client started :" + host + " :" + port + " ;");
        for(StreamDefinition sd: streamDefinitionsList)
        {
            System.out.print(sd.getStreamId() + ", ");
        }
        System.out.println();
    }

    public void start(){
        Runnable runnable=new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        Event eventStream = (Event) blockingQueue.take();

                        StreamRuntimeInfo streamRuntimeInfo = streamRuntimeInfoHashMap.get(eventStream.getStreamId());
                        Object[] event = eventStream.getData();

                        outputStream.write((byte) streamRuntimeInfo.getStreamId().length());
                        outputStream.write((streamRuntimeInfo.getStreamId()).getBytes("UTF-8"));

                        ByteBuffer buf = ByteBuffer.allocate(streamRuntimeInfo.getFixedMessageSize());
                        int[] stringDataIndex = new int[streamRuntimeInfo.getNoOfStringAttributes()];
                        int stringIndex = 0;
                        StreamDefinition.Type[] types = streamRuntimeInfo.getAttributeTypes();
                        for (int i = 0, typesLength = types.length; i < typesLength; i++) {
                            StreamDefinition.Type type = types[i];
                            switch (type) {
                                case INTEGER:
                                    buf.putInt((Integer) event[i]);
                                    continue;
                                case LONG:
                                    buf.putLong((Long) event[i]);
                                    continue;
                                case BOOLEAN:
                                    buf.put((byte) (((Boolean) event[i]) ? 1 : 0));
                                    continue;
                                case FLOAT:
                                    buf.putFloat((Float) event[i]);
                                    continue;
                                case DOUBLE:
                                    buf.putDouble((Double) event[i]);
                                    continue;
                                case STRING:
                                    buf.putShort((short) ((String) event[i]).length());
                                    stringDataIndex[stringIndex] = i;
                                    stringIndex++;
                            }
                        }

                        outputStream.write(buf.array());
                        for (int aStringIndex : stringDataIndex) {
                            outputStream.write(((String) event[aStringIndex]).getBytes("UTF-8"));
                        }
                        outputStream.flush();
                      //      System.out.println("sent event-- : " + eventStream.getStreamId()+".");
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        };

        Thread thread=new Thread(runnable);
        thread.start();


    }

    public void close() {
        try {
            outputStream.flush();
            clientSocket.close();
        } catch (Throwable e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    public void sendEvent(Event eventStream) throws IOException, InterruptedException {
        try {
            blockingQueue.put(eventStream);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        outputStream.write(buf.array());
        for (int aStringIndex : stringDataIndex) {
            outputStream.write(((String) event[aStringIndex]).getBytes("UTF-8"));
        }
        outputStream.flush();
    //    System.out.println("sent event : " + eventStream.getStreamId()+".");

    }

    public List<StreamDefinition> getStreamDefinitionsList(){
        return streamDefinitionsList;
    }

    public void addStreamDefinition(StreamDefinition streamDefinition){
        this.streamDefinitionsList.add(streamDefinition);
        streamRuntimeInfoHashMap.put(streamDefinition.getStreamId(),EventServerUtils.createStreamRuntimeInfo(streamDefinition));

    }


    public static void main(String[] args) throws Exception {

        //TsendEventhread.sleep(1000);
//        System.out.println("Start testing");
//        Random random = new Random();
//
//        StreamDefinition streamDefinition1=new StreamDefinition();
//        streamDefinition1.setStreamId("ABC");
//        streamDefinition1.addAttribute("Att1", StreamDefinition.Type.INTEGER);
//        streamDefinition1.addAttribute("Att2", StreamDefinition.Type.FLOAT);
//
//        StreamDefinition def2=new StreamDefinition();
//        def2.setStreamId("student");
//        def2.addAttribute("age", StreamDefinition.Type.INTEGER);
//        def2.addAttribute("weight", StreamDefinition.Type.FLOAT);

        //SimpleEventHandler client = new SimpleEventHandler("localhost:7612");
//        EventClient client1=new EventClient("localhost:5180",streamDefinition1);
//        EventClient client2=new EventClient("localhost:5180",def2);
//
//        for (int i = 0; i < 10000; i++) {
//            //client.sendEvent(new Object[]{random.nextInt(), random.nextFloat(), "Abcdefghijklmnop" + random.nextLong(), random.nextInt()}, streamRuntimeInfo;
//            client1.sendEvent(new Object[]{random.nextInt(), random.nextFloat()});
//            client2.sendEvent(new Object[]{random.nextInt(),random.nextFloat()});
//        }
    }
}
