package nachos.network;

import nachos.threads.*;
import nachos.machine.*;

/**
 * The required connection object, will provide the sliding wind and buffer abstractions
 * as well as being the object that sockets "connect" to by referencing
 *
 * Where the connection to other instances of java occurs.
 *
 * Where the state of MailMessages are decoded and dealt with on an individual bases.
 *
 * Where packets are dealt with
 *
 */
public class Connection {

    protected Lock lockState = new Lock();
    protected State conState;

    int DSTPORT , SRCPORT, DSTADDRESS;

    Connection(int DSTPORT, int SRCPORT, int DSTADDRESS){
        this.DSTPORT = DSTPORT;
        this.SRCPORT = SRCPORT;
        this.DSTADDRESS = DSTADDRESS;
    }

    //Connects to another chat ready nachos instance
    protected int connect(){
        conState.CONNECT(this);

        return 1;
    }

    //accepts connection from another nachos instance
    protected int accept(){
        conState.ACCEPT(this);

        return 1;
    }

    protected int read(){
        return 1;
    }

    protected int write(){
        return 1;
    }

    protected int close(){
        return 1;
    }

    protected void send(int msg){
        //

    }

    //To be used by post master general
    public void msgPacket(UdpPacket hej){
        switch(hej.flags){
            case UdpPacket.FIN:
                conState.FIN(this);

            case UdpPacket.FIN | UdpPacket.ACK:
                conState.FINACK(this);

            case UdpPacket.STP:
                conState.STP(this);

            case UdpPacket.ACK:
                conState.ACK(this);

            case UdpPacket.SYN:
                conState.SYN(this);

            case UdpPacket.SYN | UdpPacket.ACK:
                conState.SYNACK(this);

            case UdpPacket.DATA:
                conState.DATA(this);

            default:
                break;
        }
    }


    public void packet(UdpPacket hej){
        switch(hej.flags){
            case UdpPacket.FIN:
                break;
            case UdpPacket.STP:
                break;
            case UdpPacket.ACK:
                break;
            case UdpPacket.SYN:
                break;

            default:
                break;
        }
    }


    //Holds the state for this connection and determines how to proceed when it receives a new signal from a MailMan
    protected enum State{
        DEADLOCK{

        },

        CLOSED{
            @Override
            void CONNECT(Connection conection){
                //Send SYN, goto SYN_SENT, block
                conection.send(syn);
                conection.conState = SYN_SENT;
                //block?

            }

            @Override
            void RECV(Connection recieve){
                //dequeue data, or fail syscall if none available
            }

            @Override
            int SEND(Connection conAir){
                //fail syscall
                return -1;
            }

            @Override
            void SYN(Connection conAir){
                //goto SYN_RCVD
                conAir.conState = SYN_RCVD;
            }

            @Override
            void STP(Connection conAir){
                //protocol error along with DATA and ACK, so do nothing?
            }

            @Override
            void FIN(Connection conAir){
                //Send FINACK
                conAir.send(fin);

                conAir.send(ack);
            }
        },

        SYN_SENT{
            @Override
            void TIMER(Connection con){
                //send SYN
                con.send(syn);
            }

            @Override
            void SYN(Connection con){
                //protocol deadlock!
                con.conState = DEADLOCK;
            }

            @Override
            void SYNACK(Connection con){
                //goto ESTABLISHED, wake thread waiting in connect()
                con.conState = ESTABLISHED;
                //Wake?
            }

            @Override
            void STP(Connection con){
                //send SYN
                con.send(syn);
            }

            @Override
            void FIN(Connection con){
                //send SYN
                con.send(syn);

            }
            @Override
            void ACK(Connection con){
                //protocol error
            }

        },

        SYN_RCVD{
            @Override
            void ACCEPT(Connection conAir){
                //send SYN_ACK, goto established
                conAir.send(syn);
                conAir.send(ack);

                conAir.conState = ESTABLISHED;


            }

            @Override
            void STP(Connection conAir){
                //protocol error along with DATA and ACK and FIN and FINACK
            }

        },

        ESTABLISHED{
            @Override
            void RECV(Connection con){
                //dequeue data
            }

            @Override
            int SEND(Connection con){
                //queue data, shift send window
                return 1;
            }

            @Override
            void CLOSE(Connection con){
                //if send queue is empty, send FIN, goto CLOSING. else send STOP, goto STP_SENT
                int k = 1;

                if(k == 1){
                    con.send(fin);
                    con.conState = CLOSING;
                }

                else{
                    //stop?
                    con.conState = STP_SENT;
                }
            }

            @Override
            void TIMER(Connection con){
                //resend unacknowledged packets
            }

            @Override
            void SYN(Connection con){
                //send SYN/ACK
                con.send(syn);
                con.send(ack);
            }

            @Override
            void DATA(Connection con){
                //queue data, send ACK
                //QUEUE

                con.send(ack);
            }

            @Override
            void ACK(Connection con){
                //shift send window, send data
            }

            @Override
            void STP(Connection con){
                //clear send window, goto STP_RCVD

                con.conState = STP_RCVD;
            }

            @Override
            void FIN(Connection con){
                //clear send window, send FIN/ACK, goto CLOSED

                con.send(fin);
                con.send(ack);

                con.conState = CLOSED;
            }

            @Override
            void FINACK(Connection con){
                //protocol error
            }

        },

        STP_RCVD{
            @Override
            void RECV(Connection con){
                //dequeue data
            }

            @Override
            int SEND(Connection con){
                //fail syscall
                return -1;
            }

            @Override
            void CLOSE(Connection con){
                //send FIN, goto CLOSING
                con.send(fin);

                con.conState = CLOSING;
            }

            @Override
            void DATA(Connection con){
                //queue data, send ACKACK, FINACK	protocol error

                con.send(ack);
                con.send(fin);
            }

            @Override
            void FIN(Connection con){
                //send FIN/ACK, goto CLOSED

                con.send(fin);
                con.send(ack);

                con.conState = CLOSED;
            }
        },

        STP_SENT{
            @Override
            void TIMER(Connection con){
                //retransmit unacknowledged packet
            }

            @Override
            void DATA(Connection con){
                //send STP ACK	shift send window, send data. if send queue is empty, send FIN and goto CLOSING
                con.send(stp);

                //shift send window

                //if sendqueue is empty
                if(true){
                    con.send(fin);
                    con.conState = CLOSING;
                }

            }

            @Override
            void STP(Connection con){
                //clear send window, send FIN, goto CLOSING
                con.send(fin);
                con.conState = CLOSING;
            }

            @Override
            void FIN(Connection con){
                //send FIN/ACK, goto CLOSED

                con.send(fin);
                con.send(ack);

                con.conState = CLOSED;
            }

            @Override
            void FINACK(Connection con){
                //protocol error
            }
        },

        CLOSING{
            @Override
            void TIMER(Connection con){
                //send FIN
                con.send(fin);
            }

            @Override
            void SYN(Connection con){
                //send SYN/ACK
                con.send(syn);
                con.send(ack);
            }

            @Override
            void DATA(Connection con){
                //send FIN
                con.send(fin);

            }

            @Override
            void STP(Connection con){
                //send FIN
                con.send(fin);

            }

            @Override
            void FIN(Connection con){
                //send FIN/ACK, goto CLOSED
                con.send(fin);
                con.send(ack);

                con.conState = CLOSED;
            }

            @Override
            void FINACK(Connection con){
                //goto CLOSED
                con.conState = CLOSED;
            }

        };

        int fin = UdpPacket.FIN;
        int ack = UdpPacket.ACK;
        int syn = UdpPacket.SYN;
        int stp = UdpPacket.STP;

        //All of the potential actions to be overriden depending on the current state of the connection
        void CONNECT(Connection conAir){}

        void ACCEPT(Connection conAir){}

        void RECV(Connection conAir){}

        int SEND(Connection conAir){return 1;}

        void CLOSE(Connection conAir){}

        void TIMER(Connection conAir){}

        void SYN(Connection conAir){}

        void STP(Connection conAir){}

        void FIN(Connection conAir){}

        void FINACK(Connection conAir){}

        void SYNACK(Connection conAir){}

        void ACK(Connection conAir){}

        void DATA(Connection con){}

    }

    //Sliding window implementation required

    //protected static class Window{}

}
