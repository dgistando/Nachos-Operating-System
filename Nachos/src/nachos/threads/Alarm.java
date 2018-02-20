package nachos.threads;

import nachos.machine.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	
     private List<TimedThread> waitQueue;
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
	    
		waitQueue = new ArrayList<TimedThread>();
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
		KThread.currentThread().yield();

		if(waitQueue.isEmpty())
			return;
		long time = Machine.timer().getTime();

		Iterator<TimedThread> it = waitQueue.iterator();
		while(it.hasNext()){
			TimedThread thread = it.next();
			if(thread.time <= time){
				thread.thread.ready();
				waitQueue.remove(thread);
			}
		}
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
		while (wakeTime > Machine.timer().getTime())
			KThread.yield();

		boolean interruptStatus = Machine.interrupt().disable();

		TimedThread thread = new TimedThread(KThread.currentThread(), wakeTime);
		waitQueue.add(thread);
		KThread.sleep();

		Machine.interrupt().restore(interruptStatus);
	}


	public class TimedThread{
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

	}

}
