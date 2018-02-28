package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {

    private Lock lock;
    private Condition2 speakCondition;
    private Condition2 listenCondition;
    private Condition2 firstSpeaker;

    private int listenCount;
    private int speakerCount;
    private int word;

    private boolean isWord;

    /**
     * Allocate a new communicator.
     */
    public Communicator() {
        System.out.println("New Communicator!!");
        lock = new Lock();
        speakCondition = new Condition2(lock);
        listenCondition = new Condition2(lock);
        firstSpeaker = new Condition2(lock);

        speakerCount = 0;
        listenCount = 0;

        isWord = false;
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
        System.out.print("speak");
        //Don't need to re-acquire the lock if you already have it.
        if(!lock.isHeldByCurrentThread())lock.acquire();

        speakerCount++;
        //if there is no word the first time then pass this loop
        while(isWord) {
            System.out.print("LISTEN");speakCondition.sleep();
        }

        this.word = word;
        isWord = true;
        //If here then only speaker and ready
        listenCondition.wake();

        firstSpeaker.sleep();

        speakerCount--;
        lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
        int wordReturn;

        System.out.print("listen");
        //Don't need to re-acquire the lock if you already have it.
        if(!lock.isHeldByCurrentThread())lock.acquire();

        while (!isWord || speakerCount == 0){
            System.out.print("LISTEN");listenCondition.sleep();
        }

        wordReturn = this.word;

        isWord = false;

        //Since the word was listened to
        //There is no word anymore
        speakCondition.wake();
        firstSpeaker.wake();

        lock.release();
	    return wordReturn;
    }

}
