package nachos.threads;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
    private Lock conditionLock;
    private ThreadQueue waitQueue;
    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock	the lock associated with this condition
     *				variable. The current thread must hold this
     *				lock whenever it uses <tt>sleep()</tt>,
     *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
        this.conditionLock = conditionLock;
        this.waitQueue = ThreadedKernel.scheduler.newThreadQueue(true);
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {System.out.println(" sleep \n");
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        boolean res = Machine.interrupt().disable();

        /**
         * waitQueue waits for access of the current thread and
         * then releases the lock and puts the current thread
         *  to sleep
         */
        waitQueue.waitForAccess(KThread.currentThread());
        conditionLock.release();
        KThread.sleep();

        conditionLock.acquire();
        Machine.interrupt().restore(res);
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {System.out.println(" wake\n");
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        boolean res = Machine.interrupt().disable();

        /**
         * grab the next thread on the wait queue
         * ensure thread isn't null, then ready that thread
         */
        KThread thread = waitQueue.nextThread();
        if(thread != null) thread.ready();
        Machine.interrupt().restore(res);
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {System.out.println(" wakeAll\n");
    	Lib.assertTrue(conditionLock.isHeldByCurrentThread());

        boolean res = Machine.interrupt().disable();

        /**
         * wakes up all the threads in the waitQueue
         * this will continue until we dequeue an empty
         * queue and return a null thread
         */
    	KThread thread = waitQueue.nextThread();
    	while(thread != null){
    	    thread.ready();
    	    thread = waitQueue.nextThread();
        }

        Machine.interrupt().restore(res);
    }

}
