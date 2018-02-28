package nachos.threads;

import nachos.machine.*;
import nachos.machine.Timer;

import java.util.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	
     private PriorityQueue<TimedThread> waitQueue;
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {

	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });

	waitQueue = new PriorityQueue<TimedThread>();
    }

    //This is nothing
	/*@Override
	public int compare(TimedThread timedThread, TimedThread t1) {
		//return timedThread.time - t1.time;
		if(timedThread.time > t1.time){
			return 1;
		}else if(timedThread.time < t1.time){
			return -1;
		}else{
			return 0;
		}*/

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
    	long currentTime = Machine.timer().getTime();
		boolean res = Machine.interrupt().disable();

		while(!waitQueue.isEmpty() && waitQueue.peek().time <= currentTime){
			TimedThread thread = waitQueue.poll();
			KThread kThread = thread.thread;
			if(kThread != null)kThread.ready();
		}

		KThread.yield();
    	Machine.interrupt().restore(res);
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		long wakeTime = Machine.timer().getTime() + x;
		//while (wakeTime > Machine.timer().getTime()) //shouldn't do this
		//	KThread.yield();

		boolean interruptStatus = Machine.interrupt().disable();

		TimedThread thread = new TimedThread(KThread.currentThread(), wakeTime);
		waitQueue.add(thread);
		KThread.sleep();

		Machine.interrupt().restore(interruptStatus);
	}


	protected class TimedThread implements Comparable{
    	protected KThread thread;
    	protected long time;

		public TimedThread(KThread thread) {
			this.thread = thread;
			time = Machine.timer().getTime();
		}

		public TimedThread(KThread thread,long time){
			this.thread = thread;
			this.time = time;
		}

		@Override
		public int compareTo(Object o) {
				if(this.time > ((TimedThread)o).time ){
					return 1;
				}else if(((TimedThread)o).time == this.time){
					return 0;
				}else
					return -1;
			}
		}
}
