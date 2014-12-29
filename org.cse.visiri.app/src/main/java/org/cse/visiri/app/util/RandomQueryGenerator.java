package org.cse.visiri.app.util;

import org.apache.commons.lang3.StringUtils;
import org.cse.visiri.util.Configuration;
import org.cse.visiri.util.Query;
import org.cse.visiri.util.StreamDefinition;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Created by Geeth on 2014-12-11.
 */
public class RandomQueryGenerator {

    private int _lastAttrID = 1;
    private int _lastStreamID = 1;
    private int _lastQueryID = 1;

    private Random randomizer;

    private String newStreamName()
    {
        return "stream"+ (_lastStreamID++);
    }
    private String newQueryID()
    {
        return "query"+ (_lastQueryID++);
    }
    private String newAttributeName()
    {
        return "attr" + (_lastAttrID++);
    }

    private String newCondition(List<StreamDefinition.Attribute> attrs)
    {
        int attrIdx =  randomizer.nextInt(attrs.size());
        String attrName = attrs.get(attrIdx).getName();
        String op = randomizer.nextBoolean() ? ">" : "<";
        float value = randomizer.nextFloat() * 100;
        String valStr = new DecimalFormat("#0.0#").format(value);
        String cond = String.format("%s %s %s",attrName,op,valStr);

        return cond;
    }

    public RandomQueryGenerator(int seed)
    {
        randomizer = new Random(seed);
    }

    public List<StreamDefinition> generateDefinitions(int defCount,int queryAttrMin, int queryAttrMax)
    {
        List<StreamDefinition> defs = new ArrayList<StreamDefinition>();

        for(int defIdx =0 ; defIdx < defCount ; defIdx++ )
        {
            StreamDefinition def = new StreamDefinition();
            def.setStreamId(newStreamName());
            int attrCount = queryAttrMin+randomizer.nextInt(queryAttrMax-queryAttrMin + 1);
            for(int attrId= 1 ; attrId<= attrCount ; attrId++)
            {
                def.addAttribute("attr" + attrId, StreamDefinition.Type.FLOAT);
            }

            defs.add(def);
        }

        return defs;
    }

    public List<Query> generateQueries(int queryCount,List<StreamDefinition> inputs, List<StreamDefinition> outputs)
    {
        List<Query> allQueries= new ArrayList<Query>(queryCount);

        int numTypes = 5;
        int[] typeFreq = new int[numTypes];

        for(int qIndex = 0; qIndex < queryCount ; qIndex++)
        {
            int inputIndex= randomizer.nextInt(inputs.size());
            int outputIndex = randomizer.nextInt(outputs.size());

            StreamDefinition inputDef= inputs.get(inputIndex);
            StreamDefinition outputDef =outputs.get(outputIndex);

            //Query types
            // 0: filter 1
            // 1: filter 2
            // 2,3: window
            // 4: window + filter 1

            int queryType = randomizer.nextInt(numTypes);
            typeFreq[queryType]++;

            String template = "from %s%s%s " +
                    " select %s " +
                    " insert into %s ";

            String varInput, varCondition,varWindow, varInAttr, varOutput;
            varInput=varCondition=varWindow= varInAttr= varOutput="";

            varInput = inputDef.getStreamId();
            varOutput = outputDef.getStreamId();

            List<StreamDefinition.Attribute> inAttrs = inputDef.getAttributeList();
            List<StreamDefinition.Attribute> outAttrs = outputDef.getAttributeList();

            switch (queryType)
            {
                case 0: //filter 1
                {
                    String cond1 = newCondition(inAttrs);
                    String cond2 = newCondition(inAttrs);
                    varCondition = "[" + cond1 + " and " + cond2 + "]";



                    List<String> inps = new ArrayList<String>();

                    for(int i=0; i < outAttrs.size(); i++)
                    {
                        int attrPos =i;
                        String attr = inAttrs.get(attrPos).getName();
                        inps.add(attr);
                    }
                    varInAttr= StringUtils.join(inps,",");
                    break;
                }
                case 1: //filter 2
                {
                    String cond1 = newCondition(inAttrs);
                    String cond2 = newCondition(inAttrs);
                    String cond3 = newCondition(inAttrs);
                    String cond4 = newCondition(inAttrs);
                    varCondition = "[" + cond1 + " and " + cond2 +
                                        " and " + cond3 + " and " +cond4 + "]";

                    List<String> inps = new ArrayList<String>();
                    for (int i = 0; i < outAttrs.size(); i++) {
                        int attrPos = i;
                        String attr = inAttrs.get(attrPos).getName();
                        inps.add(attr);
                    }
                    varInAttr = StringUtils.join(inps, ",");
                    break;
                }
                case 2: //window

                {
                    String type = randomizer.nextFloat() < 0.75 ? "lengthBatch" : "length";
                    int batchCount = 5 + randomizer.nextInt(10*1000);
                    varWindow="#window."+type+"("+batchCount+") ";

                    List<String> inps = new ArrayList<String>();
                    for (int i = 0; i < outAttrs.size(); i++) {
                        int attrPos = i;
                        String attr = " max(" + inAttrs.get(attrPos).getName() +
                                        ") as " +inAttrs.get(attrPos).getName();
                        inps.add(attr);
                    }
                    varInAttr = StringUtils.join(inps, ",");
                    break;
                }
                case 3:
                case 4: //window +  filter
                {
                    String cond1 = newCondition(inAttrs);
                    String cond2 = newCondition(inAttrs);
                    varCondition = "[" + cond1 + " and "+ cond2 + "]";
                    String type = randomizer.nextBoolean() ? "lengthBatch" : "length";
                    int batchCount = 5 + randomizer.nextInt(5000);
                    varWindow="#window."+type+"("+batchCount+") ";

                    List<String> inps = new ArrayList<String>();

                    for (int i = 0; i < outAttrs.size(); i++) {
                        int attrPos = i;
                        String attr = " max(" + inAttrs.get(attrPos).getName() +
                                ") as " +inAttrs.get(attrPos).getName();
                        inps.add(attr);
                    }
                    varInAttr= StringUtils.join(inps,",");
                    break;
                }

            }

            String queryString = String.format(template,varInput, varCondition,varWindow, varInAttr, varOutput);
            List<StreamDefinition> inputDefList = Arrays.asList(inputDef);

            Query query = new Query( queryString,inputDefList,outputDef,newQueryID(), Configuration.ENGINE_TYPE_SIDDHI);
            allQueries.add(query);
        }

        System.out.print("Types :");
        for(int i=0; i < numTypes; i++)
        {
            System.out.print(typeFreq[i]+" ");
        }
        System.out.println();

        return allQueries;
    }

}
