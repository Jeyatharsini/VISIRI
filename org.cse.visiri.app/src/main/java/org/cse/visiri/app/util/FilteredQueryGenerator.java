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

package org.cse.visiri.app.util;

import org.apache.commons.lang3.StringUtils;
import org.cse.visiri.util.Configuration;
import org.cse.visiri.util.Query;
import org.cse.visiri.util.StreamDefinition;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class FilteredQueryGenerator extends RandomQueryGenerator{

    private double complexity = 2;
    private final int maximumLengthWindow = 2000;
    private int fixedQueryType = -1;

    private final boolean timeMode = true;

    public  FilteredQueryGenerator(int seed)
    {
        super(seed);
    }
    public  FilteredQueryGenerator(int seed,double complexity)
    {
        super(seed);
        this.complexity = complexity;
    }
    public FilteredQueryGenerator(int seed,double complexity, int queryType)
    {
        super(seed);
        this.complexity = complexity;
        fixedQueryType = queryType;
    }

    protected int nextQueryType()
    {
        if(fixedQueryType < 0) {
            int numTypes = 5;
            return randomizer.nextInt(numTypes);
        }
        else {
            return  fixedQueryType;
        }
    }

    ///--------------- time vs length --------------

    private String windowUnit = timeMode? " milliseconds":"";

    protected String nextWindowType()
    {
        String normal,batch;
        if(timeMode)
        {
            normal = "time" ;
            batch = "timeBatch";
        }
        else {
            normal = "length";
            batch = "lengthBatch";
        }
        return (randomizer.nextFloat() > 0.88) ? normal:batch;
    }

    @Override
    protected String newCondition(List<StreamDefinition.Attribute> attrs)
    {
        int attrIdx =  randomizer.nextInt(attrs.size());
        String attrName = attrs.get(attrIdx).getName();
        String op,valStr;
        double value = randomizer.nextFloat() * 100;///(complexity/2.0);
        if(randomizer.nextBoolean()) {
            op = "<";
            valStr = new DecimalFormat("#0.0#").format(value);
        }
        else
        {
            op = ">" ;
         //   value = 100-value;
            valStr = new DecimalFormat("#0.0#").format(value);
        }
        String cond = String.format("%s %s %s",attrName,op,valStr);

        return cond;
    }

    @Override
    protected Query generateQuery(List<StreamDefinition> inputs, List<StreamDefinition> outputs)
    {
        int inputIndex= randomizer.nextInt(inputs.size());
        int outputIndex = randomizer.nextInt(outputs.size());

        StreamDefinition inputDef= inputs.get(inputIndex);
        StreamDefinition outputDef =outputs.get(outputIndex);

        //int numTypes = 5;
        //Query types
        // 0: filter 1
        // 1: filter 2
        // 2: window
        // 3 4: window + filter 1

        int queryType =nextQueryType();


        String template = "from %s%s%s " +
                " select %s " +
                " insert into %s ";

        String varInput, varCondition,varWindow, varInAttr, varOutput;
        varCondition=varWindow= varInAttr= "";

        varInput = inputDef.getStreamId();
        varOutput = outputDef.getStreamId();

        List<StreamDefinition.Attribute> inAttrs = inputDef.getAttributeList();
        List<StreamDefinition.Attribute> outAttrs = outputDef.getAttributeList();


        switch (queryType)
        {
            case 0: //filter 1
            {
                String conds = generateMultipleConditions(inAttrs,(int)(2*complexity));
                varCondition = "[" + conds + "]";

                List<String> inps = new ArrayList<String>();
                for(int i=0; i < outAttrs.size(); i++)
                {
                    int attrPos =i;
                    String attr = inAttrs.get(attrPos).getName();
                    inps.add(attr);
                }
                varInAttr= StringUtils.join(inps, ",");
                break;
            }
            case 1: //filter 2
            {
                String cond = generateMultipleConditions(inAttrs,(int)(3*complexity));

                varCondition = "[" + cond + "]";

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
                String type = nextWindowType();
                int window = Math.min((int)(300*complexity),maximumLengthWindow);
                int batchCount = 5 + randomizer.nextInt(window);
                varWindow="#window."+type+"("+batchCount+windowUnit+") ";

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
                String cond = generateMultipleConditions(inAttrs,(int)(2*complexity));
                varCondition = "[" + cond + "]";
                String type = nextWindowType();
                int window = Math.min((int)(300*complexity),maximumLengthWindow);
                int batchCount = 5 + randomizer.nextInt(window);
                varWindow="#window."+type+"("+batchCount+windowUnit+") ";

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


        Query query = new Query( queryString,inputDefList,outputDef,newQueryID(), Configuration.ENGINE_TYPE_SIDDHI,1.0);


        return query;
    }
}
