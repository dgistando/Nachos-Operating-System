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
        int srcPort = NetKernel.postOffice.PortAvailable();
        int srcLink = Machine.networkLink().getLinkAddress();

        //Connection connection = new Connection(host, port, srcLink, srcPort);
        int i;
        for(i = 2; i < 16; i++)
        {
            if(fileTable[i] == null)
            {
                //fileTable[i] = connection;
                //break;
            }
        }
        //______ message = new _____(host, port, srcLink, srcPort, 1, 0, new byte[0]);
        //NetKernel.postoffice.send(message);

        //if(message == null)
        //return -1;

        //______ received = NetKkernel.postOffice.receive(srcPort);

        return i;
    }


    
    private int handleAccept(int port) {

        Lib.assertTrue(port >= 0 && port < Packet.linkAddressLimit);

        MailMessage mail = NetKernel.postOffice.receive(port);
        if(mail == null) {
            return -1;
        }

        int sourceLink = mail.packet.srcLink;
        int destinationLink = mail.packet.dstLink;
        int destinationPort = mail.srcPort;

        Connection conn = new Connection(destinationLink, destinationPort, sourceLink);

        int connectionIndex;
        for(int i = 2; i < fileTable.length; i++){

            if(fileTable[i] == null) {
                fileTable[i] = conn;
                connectionIndex = i;
                break;
            }
        }

        UdpPacket ackPacket = new UdpPacket( destinationPort, sourceLink, port, 1, 0, new byte [0]);

        try {
            NetKernel.postOffice.send(ackPacket);
        } catch (MalformedPacketException e) {
            Lib.assertNotReached("This is a malformed acknowledgement packet");
            return -1
        }

        return connectionIndex
    }

    private static final int
            syscallConnect = 11,
            syscallAccept = 12;

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
            default:
                return super.handleSyscall(syscall, a0, a1, a2, a3);
        }
    }
}
