import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.util.ArrayList;
import java.util.TreeMap;

class MidiNote
{
    public int channel;
    public int pitch;
    public int velocity;

    public int tick;
    public int duration;

    public void parse(Element elem)
    {
        channel = Integer.parseInt(elem.getAttribute("Channel"));
        pitch = Integer.parseInt(elem.getAttribute("Note"));
        velocity = Integer.parseInt(elem.getAttribute("Velocity"));
    }

    public void setEndTick(int endTick)
    {
        duration = endTick - tick;
        if (duration <= 0)
        {
            Main.printf("Invalid end tick %d (start tick is %d)", endTick, tick);
            throw new RuntimeException("Invalid note duration");
        }
    }

    public Integer key()
    {
        return Integer.valueOf(pitch);
    }
}

public class Main
{
    private int m_pitchMin = 9999;
    private int m_pitchMax = 0;
    private MidiNote[] m_onNotes = new MidiNote[128];
    private TreeMap<Integer,ArrayList<MidiNote>> m_events = new TreeMap<Integer,ArrayList<MidiNote>>();

    public static void printf(String format, Object ...args)
    {
        System.out.println(args.length == 0 ? format : String.format(format, args));
    }

    private void processEvent(Element event) throws Exception
    {
        NodeList noteOn = event.getElementsByTagName("NoteOn");
        if (noteOn.getLength() == 0)
            return;

        MidiNote note = new MidiNote();
        note.parse((Element)noteOn.item(0));

        String tickStr = event.getElementsByTagName("Absolute").item(0).getTextContent();
        note.tick = Integer.parseInt(tickStr);
        Integer keyTick = Integer.valueOf(note.tick);


        if (note.velocity == 0)
        {
            // note off
            if (m_onNotes[note.pitch] == null)
            {
                printf("NoteOff %d @tick%d is null", note.pitch, note.tick);
                throw new RuntimeException("Cannot find the start of Note");
            }

            m_onNotes[note.pitch].setEndTick(note.tick);
            m_onNotes[note.pitch] = null;

            return;
        }
        else
        {
            // note on
            if (m_onNotes[note.pitch] != null)
            {
                throw new RuntimeException("Duplicated note");
            }

            if (note.pitch < m_pitchMin)
                m_pitchMin = note.pitch;
            if (m_pitchMax < note.pitch)
                m_pitchMax = note.pitch;

            m_onNotes[note.pitch] = note;
        }


        ArrayList<MidiNote> notes;
        if (m_events.containsKey(keyTick))
        {
            notes = m_events.get(keyTick);
        }
        else
        {
            notes = new ArrayList<MidiNote>();
            m_events.put(keyTick, notes);
        }

        notes.add(note);
        //printf("Note added: tick=%d, pitch=%d, channel=%d", note.tick, note.pitch, note.channel);
    }

    private void processTrack(Element track)  throws Exception
    {
        NodeList events = track.getElementsByTagName("Event");
        for (int i=0; i<events.getLength(); i++)
        {
            processEvent((Element)events.item(i));
        }
    }

    private void readXml(Element root) throws Exception
    {
        NodeList tracks = root.getElementsByTagName("Track");
        processTrack((Element) tracks.item(1));
        processTrack((Element) tracks.item(2));

        dumpEvents();
    }

    private void dumpEvents()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("{\"pitchMin\":%d, \"pitchMax\":%d, \"notes\":[\n", m_pitchMin, m_pitchMax));

        for (Integer keyTick : m_events.keySet())
        {
            ArrayList<MidiNote> notes = m_events.get(keyTick);
            if (notes.size() == 0)
                throw new RuntimeException("Notes empty at tick " + keyTick);

            int quantizedTick = (keyTick.intValue() / 10) * 10;
            sb.append(String.format("[%d,[", quantizedTick));

            int iNotes = 0;
            for (MidiNote note : notes)
            {
                if (note.tick != keyTick.intValue())
                    throw new RuntimeException("Tick key mismatch");

                if (++iNotes > 1)
                    sb.append(",");

                sb.append(String.format("[%d,%d]", note.pitch, note.duration));
            }

            sb.append("]],\n");
        }

        sb.setLength(sb.length() - 2);
        sb.append("\n]}");

        System.out.println(sb.toString());
    }

    // http://flashmusicgames.com/midi/mid2xml.php

    public static void main(String[] args) throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        File file = new File("bach_846.xml");
        Document doc = builder.parse(file);
        doc.getDocumentElement().normalize();

        Element root = doc.getDocumentElement();
        if (!"MIDIFile".equals(root.getNodeName()))
        {
            System.out.println("Not a MIDI file: " + root.getNodeName());
            return;
        }

        new Main().readXml(root);
    }
}
