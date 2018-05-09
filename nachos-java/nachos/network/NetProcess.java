package nachos.network;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>VMProcess</tt> that supports networking syscalls.
 */
public class NetProcess extends UserProcess {
    /**
     * Allocate a new process.
     */
    public NetProcess() {
        super();
    }


    private int handleConnect(int host, int port)
    {
        int srcLink = Machine.networkLink().getLinkAddress();
        int srcPort;
        int res;
        Connection connection = null;
        //check for an existing connection and get the index if exists
        if( (res = checkExistingConnection(host, srcLink, port, port)) == -1) {

            srcPort = NetKernel.postOffice.PortAvailable();

            connection = new Connection(host, port, srcLink, srcPort);
            int i;
            for (i = 2; i < fileDescriptors.length; i++) {
                if (fileDescriptors[i] == null) {
                    fileDescriptors[i] = connection;
                    break;
                }
            }
            res = i;
        }


        if(connection == null) connection = (Connection) fileDescriptors[res];
        srcPort = connection.sourcePort;

        try {
            UdpPacket packet = new UdpPacket(host, port, srcLink, srcPort, UdpPacket.SYN,0, new byte[0]);

            NetKernel.postOffice.send(packet);

            System.out.println("SYS SENT");

            UdpPacket SynAckPack = NetKernel.postOffice.receive(srcPort);

            if(SynAckPack.flag == UdpPacket.SYNACK && Machine.networkLink().getLinkAddress() == SynAckPack.packet.dstLink){
                System.out.println("SYNACK RECEIVED: ");
                System.out.print(SynAckPack);
                //Sent ack back.
                UdpPacket ackPack = new UdpPacket(host, port, srcLink, srcPort, UdpPacket.ACK, SynAckPack.seq+1, new byte[0]);
                NetKernel.postOffice.send(ackPack);
                //ack sent. start sending immediately!!!!
            }


        }catch (MalformedPacketException e){
            Lib.assertNotReached("This is a malformed acknowledgement packet");
            return -1;
        }




        return res;
    }

    private int handleAccept(int port) {

        Lib.assertTrue(port >= 0 && port < Packet.linkAddressLimit);

        //MailMessage mail = NetKernel.postOffice.receive(port);
        UdpPacket mail = NetKernel.postOffice.receive(port);
        if(mail == null) {
            return -1;
        }
        //Added source port for connections. It might just be the same port instead
        //need to return ack so things might look weird
        //For some reason the port used are not individual for each nachos instance.
        int srcPort = mail.destPort;//NetKernel.postOffice.PortAvailable();
        int sourceLink = mail.packet.dstLink;
        int destinationLink = mail.packet.srcLink;
        int destinationPort = mail.srcPort;
        int seq = mail.seq + 1;

        int res;

        if( (res = checkExistingConnection(destinationLink, sourceLink, srcPort, destinationPort)) == -1){

            Connection conn = new Connection(destinationLink, destinationPort, sourceLink, srcPort);
            int connectionIndex = -1;

            for(int i = 2; i < fileDescriptors.length; i++){

                if(fileDescriptors[i] == null) {
                    fileDescriptors[i] = conn;
                    connectionIndex = i;
                    break;
                }
            }
            res = connectionIndex;
        }



        try {

            UdpPacket ackPacket = null;
            if(mail.flag == UdpPacket.SYN && Machine.networkLink().getLinkAddress() == mail.packet.dstLink) {
                ackPacket = new UdpPacket(destinationLink, destinationPort, sourceLink, srcPort, UdpPacket.SYNACK, seq, new byte[0]);
            }

            if(ackPacket == null)//Nothing left to do here. The message should happen either
                Lib.assertNotReached();

            NetKernel.postOffice.send(ackPacket);
            //Synack Sent

            //finish handshake
            UdpPacket ackPack = NetKernel.postOffice.receive(port);

            if(ackPack.flag == UdpPacket.ACK && Machine.networkLink().getLinkAddress() == mail.packet.dstLink){ //means connection established
                System.out.print("::CONNECTIONS ESTABLISHED::");
            }

        } catch (MalformedPacketException e) {
            Lib.assertNotReached("This is a malformed acknowledgement packet");
            return -1;
        }

        return res;
    }

    public int checkExistingConnection(int dest, int src, int srcport, int desport){
        //Go through sockets and find connection.
        for(int i = 2; i < fileDescriptors.length; i++){
            //if the connections isnt null and is the same
            if(fileDescriptors[i] != null && (fileDescriptors[i] instanceof Connection)){
                Connection con = (Connection) fileDescriptors[i];
                //chekc to see if already have cconnection
                if(con.destinationLink == dest &&
                        con.sourceLink == src &&
                        con.sourcePort == srcport &&
                        con.destinationPort == desport
                        ) {
                    return i;
                }
            }
        }

        //This means the connection doesnt already exist.
        return -1;
    }


    private int handleInet(int fd){
        if(fileDescriptors[fd] instanceof Connection){
            Connection connection = (Connection) fileDescriptors[fd];
            if(connection != null){
                return (connection.sourceLink == Machine.networkLink().getLinkAddress()) ? connection.destinationLink : connection.sourceLink;
            }
        }
        //File specified not a connection
        return -1;
    }

    private int handleSend(int fd, int messagePtr, int messLen, int flag){//need to send ack packet using the connection. I know that's not convention
        Connection connection=null;
        if(fileDescriptors[fd] instanceof Connection){
            connection = (Connection) fileDescriptors[fd];
        }
        if(connection == null)return -1;

        String message = readVirtualMemoryString(messagePtr, messLen);

        System.out.println("\nSending: "+ message);

        while(messLen > 0){
            int written = connection.handleWrite(message.getBytes(), 0, message.length());

            if(written == -1)return -1;

            messLen -= written;
        }

        return messLen;
    }

    private int handleNetRead(int fd, int buffer, int len){
        Connection connection=null;
        if(fileDescriptors[fd] instanceof Connection){
            connection = (Connection) fileDescriptors[fd];
        }
        if(connection == null)return -1;

        byte[] tempBuffer = new byte[len];

        int readSize = 0;
        while(readSize < len){//need to find a way to stop because length is 64
            int res;
            res = connection.handleRead(tempBuffer, readSize, len);
            if(res == -1)return -1;//Something went wrong sending
            readSize += res;
        }

        return writeVirtualMemory(buffer, tempBuffer, 0, readSize);
    }


    private static final int
            syscallConnect = 11,
            syscallAccept = 12,
            syscallInetAddr = 13,
            syscallSend = 14,
            syscallNetRead = 15;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>11</td><td><tt>int  connect(int host, int port);</tt></td></tr>
     * <tr><td>12</td><td><tt>int  accept(int port);</tt></td></tr>
     * </table>
     *
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
            case syscallConnect:
                return handleConnect(a0,a1);
            case syscallAccept:
                return handleAccept(a0);
            case syscallInetAddr:
                return handleInet(a0);
            case syscallSend:
                return handleSend(a0, a1, a2, a3);
            case syscallNetRead:
                return handleNetRead(a0, a1, a2);
            default:
                return super.handleSyscall(syscall, a0, a1, a2, a3);
        }
    }

/*    public static class Socket extends OpenFile{
        protected Connection connection;

        Socket(){ }
        Socket(Connection connection){
            this.connection = connection;
        }

        public int read(){
            return 1;
        }

        public int write(){
            return 1;
        }
    }*/

}
