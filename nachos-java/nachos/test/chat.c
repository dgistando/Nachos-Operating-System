#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

#define BUFFERSIZE	64

//function for the legnth of the string
int Strlen ( const char * _str )
{
    int i = 0;
    while(_str[i++]);
    return i;
}

int main(){
    printf("connecting...\n");

    int server = 1;
    int port = 0;
    //to 1 from on port 0
    //I am 0
    //connection socket file descriptor 
    int conSockFd = connect(server,port);

    //this is just a test case to make sure that the chat is working
    //sent SYN. waiting for SYNACK
    if(conSockFd != -1){//contact must have worked. got synack!!
            sendFd(conSockFd, "hello", 5, 0);//first send will be ack
    }
    
    //this is used to continue waiting for input and a "real" chat can be simulated
    /*char buffer[BUFFERSIZE];

     while(1){
        readLine(buffer, BUFFERSIZE);

        sendFd(conSockFd, buffer, Strlen(buffer), 0);//first send will be ack
     }*/

    return 0;
}
