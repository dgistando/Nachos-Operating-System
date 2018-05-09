package nachos.network;

import nachos.network.*;
import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.util.Arrays;
import java.lang.Math;

public class Connection extends OpenFile{
    public int sourceLink;
    public int destinationLink;

    public int sourcePort;
    public int destinationPort;

    public int currentSeqNum;
    public int SeqNum;

    public Connection(int destinationLink, int destinationPort, int sourceLink, int sourcePort) {
        super(null, "Connection");
        this.sourceLink = sourceLink;
        this.sourcePort = sourcePort;
        this.destinationLink = destinationLink;
        this.destinationPort = destinationPort;

        this.currentSeqNum = 0;
        this.SeqNum = 0;
    }

    public int handleRead(byte[] buffer, int offset, int size)
    {
        //create a new packet and receive it on the source port
        UdpPacket packet = NetKernel.postOffice.receive(sourcePort);

        //if the packet is not valid
        if(packet == null)
        {
            return -1;
        }

        //increment the current sequence number
        currentSeqNum++;

        //the amount of bytes read is the minimum of the 2
        int bytesRead = Math.min(size, packet.payload.length);

        //copy the array to the destination
        System.arraycopy(packet.payload, 0, buffer, offset, bytesRead);

        return bytesRead;
    }

    public int handleWrite(byte[] buffer, int offset, int size)
    {
        int amt = Math.min(offset+size, buffer.length);


        byte[] elements = Arrays.copyOfRange(buffer, offset, amt);

        try {
            //write the new packet
            UdpPacket packet = new UdpPacket(destinationLink, destinationPort, sourceLink, sourcePort, UdpPacket.DATA ,SeqNum+1, elements);

            //send the packet
            NetKernel.postOffice.send(packet);

            //increment the sequence number
            SeqNum++;

            return amt;
        }

        //invalid packet
        catch(MalformedPacketException e)
        {
            return -1;
        }//catch (Exception e){ Might have to catch any exception and return -1 if it doesn't work
       //     return -1;
       // }


    }



}
