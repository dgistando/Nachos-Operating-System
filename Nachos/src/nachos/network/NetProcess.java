package nachos.network; ///NEW/////

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

    /**
     *  This is the connect function. Its usually called from the Cient.
     *  It takes in the host ip address and the port that it wants to connect on.
     *  This function sends a SYN packet and waits for the server to reply with a SYNACK.
     *  Once it get th SYNACK it responds with an ACK making the three way handshake.
     *
     *
     * @param host The opposite computer in the interations. If im client then host is Server
     * @param port The port on which the application funs.
     * @return
     */
    private int handleConnect(int host, int port)
    {
        int srcLink = Machine.networkLink().getLinkAddress();
        int srcPort;
        int res;
        Connection connection = null;
        //check for an existing connection and get the index if already exists
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

        //If the connection already existed then find it in the file descriptors
        if(connection == null) connection = (Connection) fileDescriptors[res];
        srcPort = connection.sourcePort;

        try {
            //create the SYN packets
            UdpPacket packet = new UdpPacket(host, port, srcLink, srcPort, UdpPacket.SYN,0, new byte[0]);
            //Use post office to send the packet
            NetKernel.postOffice.send(packet);

            System.out.println("SYS SENT");

            //This functions operates on a semaphore so if it hasn't
            //received anything it will just wait until. Something
            //arrives.
            UdpPacket SynAckPack = NetKernel.postOffice.receive(srcPort);

            //Received SYNACK, just need to send an ACK back.
            if(SynAckPack.flag == UdpPacket.SYNACK && Machine.networkLink().getLinkAddress() == SynAckPack.packet.dstLink){
                System.out.println("SYNACK RECEIVED: ");
                System.out.print(SynAckPack);
                //Sent ack back.
                UdpPacket ackPack = new UdpPacket(host, port, srcLink, srcPort, UdpPacket.ACK, SynAckPack.seq+1, new byte[0]);
                NetKernel.postOffice.send(ackPack);
                //ack sent. At this point its okay to send data.
            }


        }catch (MalformedPacketException e){
            Lib.assertNotReached("This is a malformed acknowledgement packet");
            return -1;
        }




        return res;
    }

    /**
     * This is accept(int). Its usually called by a server to accept
     * connection coming from a client. It just waits after the Kernel.postOffice.receive(port)
     * is called.
     * When it does finally get something on the port
     *
     * @param port
     * @return
     */
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
        //make sure the connection doesn't already exist.
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
            //check that its a syn packet
            UdpPacket ackPacket = null;                             //Also make sure it was sent to the right person
            if(mail.flag == UdpPacket.SYN && Machine.networkLink().getLinkAddress() == mail.packet.dstLink) {
                ackPacket = new UdpPacket(destinationLink, destinationPort, sourceLink, srcPort, UdpPacket.SYNACK, seq, new byte[0]);
            }

            if(ackPacket == null)//Nothing left to do here. The message should happen either
                Lib.assertNotReached();

            NetKernel.postOffice.send(ackPacket);
            //Synack Sent

            //finish handshake
            UdpPacket ackPack = NetKernel.postOffice.receive(port);
            //Once you receive an ack from the the client you offically have a connection
            if(ackPack.flag == UdpPacket.ACK && Machine.networkLink().getLinkAddress() == mail.packet.dstLink){ //means connection established
                System.out.print("::CONNECTIONS ESTABLISHED::");
            }

        } catch (MalformedPacketException e) {
            Lib.assertNotReached("This is a malformed acknowledgement packet");
            return -1;
        }

        return res;
    }

    /**
     *  This function is to check if you already have a connection.
     *
     *
     * @param dest
     * @param src
     * @param srcport
     * @param desport
     * @return
     */
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

    /**
     * This functions is to get the ip address of the opposite person in
     * connection.
     *
     * @param fd The file descriptor to find the "socket" associated with the connection.
     * @return
     */
    private int handleInet(int fd){
        if(fileDescriptors[fd] instanceof Connection){
            Connection connection = (Connection) fileDescriptors[fd];
            if(connection != null){//This is an if check to see if the ip address of the source address is the same as yours. If it is then send the other.
                return (connection.sourceLink == Machine.networkLink().getLinkAddress()) ? connection.destinationLink : connection.sourceLink;
            }
        }
        //File specified not a connection
        return -1;
    }

    /**
     * This is the send syscall. It handles the interation with the processor to get the data
     * It takes in the fd and finds the Connection.
     *
     * Then it reads virtualMemory for the message itself.
     *
     * Then it calls write on the connection being careful about the number
     * of bytessent each time.
     *
     * @param fd
     * @param messagePtr
     * @param messLen
     * @param flag
     * @return returns the number of bytes sent
     */
    private int handleSend(int fd, int messagePtr, int messLen, int flag){//need to send ack packet using the connection. I know that's not convention
        Connection connection=null;
        if(fileDescriptors[fd] instanceof Connection){
            connection = (Connection) fileDescriptors[fd];
        }
        if(connection == null)return -1;
        //convert the message from bytes in mem to a string
        String message = readVirtualMemoryString(messagePtr, messLen);

        System.out.println("\nSending: "+ message);

        while(messLen > 0){
            int written = connection.handleWrite(message.getBytes(), 0, message.length());
            //The case that there was an error while writing to the other person
            if(written == -1)return -1;
            //subtract from the amount you have left to write
            messLen -= written;
        }

        return messLen;
    }

    private int handleNetRead(int fd, int buffer, int len){
        //set the initial connection to null
        Connection connection=null;

        //check to make sure that the file descriptor is an
        //an instance of Connection
        if(fileDescriptors[fd] instanceof Connection){
            //if it is, then set the connection to the file descriptor
            connection = (Connection) fileDescriptors[fd];
        }

        //if the connection is not valid, then return -1 because of error
        if(connection == null)return -1;

        //create a new temporary, that is the size of the length of the message
        byte[] tempBuffer = new byte[len];

        //initial the readSize to 0
        int readSize = 0;

        //while loop is commented out, because the receiver continues to wait for data
        //to fill the buffer, but the sender has sent all data
        // while(readSize < len){
        int res;
        //store the amount of bytes that handleRead returns
        res = connection.handleRead(tempBuffer, readSize, len);
        //if res is -1, then there was error, so return -1
        if(res == -1)return -1;
        //increase the readsize by the amount of bytes returned by handleRead
        readSize += res;

        //}

        //transfer the data from the temporary buffer to the virtual memory
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
