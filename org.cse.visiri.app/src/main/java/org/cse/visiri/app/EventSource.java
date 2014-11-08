package org.cse.visiri.app;

import org.cse.visiri.communication.eventserver.client.EventClient;
import org.cse.visiri.util.Event;
import org.cse.visiri.util.StreamDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by Geeth on 2014-11-08.
 */
public class EventSource {

    EventClient cl;

    private List<StreamDefinition> getDefinitions()
    {
        List<StreamDefinition> defs = new ArrayList<StreamDefinition>();

        StreamDefinition sd =new StreamDefinition("fire",null);
        sd.addAttribute("location", StreamDefinition.Type.STRING);
        sd.addAttribute("temperature", StreamDefinition.Type.DOUBLE);
        sd.addAttribute("casualties", StreamDefinition.Type.BOOLEAN);
        defs.add(sd);

        sd =new StreamDefinition("fight",null);
        sd.addAttribute("location", StreamDefinition.Type.STRING);
        sd.addAttribute("fighters", StreamDefinition.Type.INTEGER);
        sd.addAttribute("deaths", StreamDefinition.Type.INTEGER);
        sd.addAttribute("duration", StreamDefinition.Type.DOUBLE);
        defs.add(sd);

        return defs;
    }

    public void start() throws  Exception
    {
        cl = new EventClient("localhost:6666",getDefinitions());
    }

    public void sendEvents() throws Exception
    {
        List<StreamDefinition> defs = getDefinitions();
        StreamDefinition def = defs.get(1);

        for(int i=0; i < 10; i++) {
            Event ev = new Event();
            ev.setStreamId(def.getStreamId());
            Object [] dat = new Object[def.getAttributeList().size()];
            ev.setData(dat);
            int index = 0;
            Random r = new Random();
            for(StreamDefinition.Attribute att : def.getAttributeList())
            {
                Object o = "<NULL>";
                switch (att.getType()) {
                    case DOUBLE:
                        o = r.nextDouble() * 100;
                        break;
                    case INTEGER:
                        o = r.nextInt(1000);
                        break;
                    case BOOLEAN:
                        o = r.nextBoolean();
                        break;
                    case STRING:
                        o = "str_" + r.nextInt(1000) ;
                        break;
                    case FLOAT:
                        o = r.nextFloat() * 100;
                        break;
                }
                dat[index++] = o;
            }
            cl.sendEvent(ev);
        }
    }

    public static void main(String[] arg) throws Exception {
        EventSource source = new EventSource();
        source.start();
        source.sendEvents();
    }
}
