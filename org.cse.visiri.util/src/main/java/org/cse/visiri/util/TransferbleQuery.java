package org.cse.visiri.util;

import org.cse.visiri.util.costmodelcalc.CostModelCalculator;

import org.cse.visiri.util.Query;

import java.util.*;

/**
 * Created by visiri on 11/23/14.
 */
//TODO have to optimize the query selection
public class TransferbleQuery {

    private Query[] queryArray;
    private CostModelCalculator costModelCalculator;



    public TransferbleQuery(List<Query> queryList){
        this.costModelCalculator=new CostModelCalculator();
//        String myNode= Environment.getInstance().getNodeId();
//        Map<String,List<Query>> nodeQueryMap=Environment.getInstance().getNodeQueryMap();
//        List<Query> queryList=nodeQueryMap.get(myNode);

        queryArray=new Query[queryList.size()];
        queryArray= (Query[]) queryList.toArray();

    }

    public TransferbleQuery(Query[] queriesArray){
        this.costModelCalculator=new CostModelCalculator();
        this.queryArray=queriesArray;
    }

    public TransferbleQuery(){
        this.costModelCalculator=new CostModelCalculator();
    }

    private void updateQueryArray(List<Query> qList){
//        String myNode= Environment.getInstance().getNodeId();
//        Map<String,List<Query>> nodeQueryMap=Environment.getInstance().getNodeQueryMap();
//        List<Query> queryList=nodeQueryMap.get(myNode);
          List<Query> queryList=qList;
        queryArray=new Query[queryList.size()];
        queryArray= (Query[]) queryList.toArray();
    }

    public List<Query> detectTransferbleQuery(double[] eventRates,List<Query> qList){
        updateQueryArray(qList);
        List<Query> queryList=new ArrayList<Query>();
        if(eventRates.length!=queryArray.length){
            System.out.println("*****Error****");       //has to handle exception
            throw new UnknownError();
        }

        int size=queryArray.length;

        double[] costArray=new double[size];
        double[] costRateValueArray=new double[size];
        Map<Double,Query> costRateQueryMap=new HashMap<Double, Query>();

        int i=0;
        for(Query query:queryArray){
            costArray[i]=costModelCalculator.calculateCost(query);
            costRateValueArray[i]=eventRates[i]*costArray[i];  // cost * eventRate
            costRateQueryMap.put(costRateValueArray[i],query);
            i++;
        }

        Arrays.sort(costRateValueArray);

        int middleIndex=costRateValueArray.length/2;        //get the query w.r.t the moddle values in the costRate array
        queryList.add(costRateQueryMap.get(costRateValueArray[middleIndex]));
        return queryList;
    }

}