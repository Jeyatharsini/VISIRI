package org.cse.visiri.util;

import java.util.HashMap;

/**
 * Created by Malinda Kumarasinghe on 11/12/2014.
 */
public class EventRateStore {
    //TODO check for accuracy of this results.
    private Long[] instantMap,avgMap;
    private int instantPos=-1;
    private int avgPos=-1;
    private int instantMapSize,avgMapSize;
    private static EventRateStore instance;

    public EventRateStore(){

        instantMapSize=Configuration.INSTANT_EVENT_COUNT;
        avgMapSize=Configuration.AVERAGE_EVENT_COUNT;

        instantMap=new Long[instantMapSize];
        avgMap=new Long[avgMapSize];

        long startTime=System.currentTimeMillis();

        for(int i=0;i<instantMapSize;i++){
            instantMap[i]=startTime;
        }

        for(int i=0;i<avgMapSize;i++){
            avgMap[i]=startTime;
        }
        increment("");
    }

    public void increment(String message){
        Long mil=System.currentTimeMillis();

        instantPos = (++instantPos >= instantMapSize) ? 0 : instantPos;
        instantMap[instantPos]=mil;

        avgPos = (++avgPos >= avgMapSize) ? 0 : avgPos;
        avgMap[avgPos]=mil;

        if(avgPos%Configuration.EVENT_RATE_FREQ==0){
            System.out.print(".");
           // System.out.println(message+" ## Ins : "+getInstantRate()+"\tAvg : "+getAverageRate());
        }
    }



    public double getInstantRate(){

        int idxLast=instantPos;
        int idxFirst=(instantPos + 1)%(instantMapSize);

        long last=instantMap[idxLast];
        long first=instantMap[idxFirst];

        double avg=((double)instantMapSize)/(double)(last-first+1);
        return avg*1000;

    }
    public double getAverageRate(){
        int idxLast=avgPos;
        int idxFirst=(avgPos + 1)%(avgMapSize);

        long last=avgMap[idxLast];
        long first=avgMap[idxFirst];

        double avg=((double)avgMapSize)/(double)(last-first+1);
        return avg*1000;
    }

//    private void printMap(){
//        for(int x : historyMap.keySet()){
//            System.out.print(" " + historyMap.get(x));
//        }
//        System.out.println("\n");
//    }




    public static void main(String[] args){

        EventRateStore eventRateStore=new EventRateStore();

        eventRateStore.getAverageRate();
/*
        for(int i=0;i<5000000;i++){
            eventRateStore.increment(i+" dd");
            try {
                if(i>5 && i<10)    {
                   // Thread.sleep(50);
                }
                else {
                   // Thread.sleep(500);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

           // System.out.print(i+"\t"+eventRateStore.getAverageRate()*1000);
            //System.out.println("\t\t"+i+"\t"+eventRateStore.getInstantRate()*1000);

        }
*/
        for(int i=0;i<25;i++){
            eventRateStore.getAverageRate();
        }

    }

}
