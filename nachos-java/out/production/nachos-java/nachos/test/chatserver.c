#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

#define BUFFERSIZE	64

void server(int conSockFd, int port){
    //need to accept the ACK sent back from client
    int clientIp = inetAddr(conSockFd);
    char buffer[BUFFERSIZE] = {0};

    while(1){
        char* str = readFd(conSockFd, buffer, BUFFERSIZE);//reafFd is an added syscall. see syscall.h and starts.s
        printf("client[%d]: %s", clientIp, str);
    }
}

/*
* This is the chatserver, or at least the start of it.
* This version basically takes a  conneciton from one client
* and accepts connection solely from them.
*
*/
int main(){
    printf("incoming...\n");
    int port = 0;

    //listening on server
    //would be in a while if multiple clients want ot connect
        //In that case you would also have a seperate thread
        //handle the connection to an individual client and one
        //managing more connections
    int conSockFd = accept(0);
    //will send ack from accpet back to client
    //server function should send SYNACK packet.
    if(conSockFd != -1){
        server(conSockFd, port);
    }

    return 0;
}
