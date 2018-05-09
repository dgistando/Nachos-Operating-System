#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

#define BUFFERSIZE	64

void server(int conSockFd, int port){
    //need to accept the ACK sent back from client
    int clientIp = inetAddr(conSockFd);
    char buffer[BUFFERSIZE] = {0};

    while(1){
        char* str = readFd(conSockFd, buffer, BUFFERSIZE);
        printf("client[&d]: %s", clientIp, str);
    }
}

int main(){
    printf("incoming...\n");
    int port = 0;

    //listening on server
    //would be in a while if multiple clients want ot connect
    int conSockFd = accept(0);
    //will send ack from accpet back to client
    //server function should send SYNACK packet.
    if(conSockFd != -1){
        server(conSockFd, port);
    }

    return 0;
}