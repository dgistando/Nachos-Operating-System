package nachos.network;


import nachos.machine.MalformedPacketException;
import nachos.machine.Packet;

public class UdpPacket {

    public Packet packet;

    public int destPort;
    public int srcPort;
    public int flags;
    public int seq;

    public byte[] payload;

    public int HEADER_LENGTH = 4;
    public int MAX_PAYLOAD_LENGTH = Packet.maxContentsLength - HEADER_LENGTH;

    public UdpPacket(){}

    public UdpPacket(int dstLink, int destPort, int srcLink, int srcPort, int flags, int seq, byte[] payload)throws MalformedPacketException{
        //Make sure we have a valid port range
        if (destPort < 0 || destPort >= maxPortLimit ||
                srcPort < 0 || srcPort >= maxPortLimit ||
                payload.length > MAX_PAYLOAD_LENGTH)
            throw new MalformedPacketException();

        this.destPort = (byte)destPort;
        this.srcPort = (byte)srcPort;
        this.flags = flags;
        this.seq = seq;
        this.payload = payload;

        byte[] contents =  new byte[HEADER_LENGTH + payload.length];

        contents[0] = (byte)destPort;
        contents[1] = (byte)srcPort;

        System.arraycopy(payload, 0, contents, HEADER_LENGTH, payload.length);

        packet = new Packet(dstLink, srcLink, contents);
    }

    public UdpPacket(Packet packet) throws MalformedPacketException{
        this.packet = packet;
        //check port range again and form packet
        if(packet.contents.length < HEADER_LENGTH ||
                packet.contents[0] < 0 || packet.contents[0] >= maxPortLimit ||
                packet.contents[1] < 0 || packet.contents[1] >= maxPortLimit)
            throw new MalformedPacketException();

        destPort = packet.contents[0];
        srcPort  = packet.contents[1];

        payload = new byte[packet.contents.length - HEADER_LENGTH];
        System.arraycopy(packet.contents, HEADER_LENGTH, payload, 0, payload.length);
    }

    //These are all the possible flags
    static final int DATA = 0;
    static final int SYN = 1;
    static final int ACK = 2;
    static final int STP = 4;
    static final int FIN = 8;

    //public static final int newHeaderLength = 2;
    //public static final int maxUdpLength = Packet.maxContentsLength - newHeaderLength;
    public static final int maxPortLimit = 128;
}