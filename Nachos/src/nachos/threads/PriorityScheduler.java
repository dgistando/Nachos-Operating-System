package nachos.threads;

import nachos.machine.*;

import java.util.*;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {

    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
	}
    
    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMaximum)
	    return false;

	setPriority(thread, priority+1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    public boolean decreasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMinimum)
	    return false;

	setPriority(thread, priority-1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;    

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {

    	//This is the main queue that holds the threads to be executed.
		public SortedSet<ThreadState> priorityQueue;
		//This is a queue made just for the threads waiting to execute.
		public LinkedList<ThreadState> waitQueue;

	PriorityQueue(boolean transferPriority) {
		this.transferPriority = transferPriority;
		//This should initialize a new Queue
		priorityQueue = new TreeSet<ThreadState>(new Comparator<ThreadState>() {
			@Override
			public int compare(ThreadState threadState, ThreadState t1) {
				return threadState.getPriority() - t1.getPriority();
			}
		});

		waitQueue = new LinkedList<ThreadState>();
	}

	public void waitForAccess(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).waitForAccess(this);
	}

	public void acquire(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).acquire(this);
	}

	public KThread nextThread() {
	    //disable the interrupts
	    Lib.assertTrue(Machine.interrupt().disabled());
		//check for elements in the queue if nothing there return null
		if(priorityQueue == null || priorityQueue.isEmpty()){
			return null;
		}else{
			/* because there is something in the queue
			 * the first function from SortedSet picks the first thread
			 * on the queue making it the next thread
			 */
			ThreadState thread = priorityQueue.first();
			/* because the first gives the next thread it needs to be
			 * removed from the queue as it has already been picked
			 */
			priorityQueue.remove(priorityQueue.first());
			//returns the next thread
			return thread.thread;
		}
	}

	/**
	 * Return the next thread that <tt>nextThread()</tt> would return,
	 * without modifying the state of this queue.
	 *
	 * @return	the next thread that <tt>nextThread()</tt> would
	 *		return.
	 */
	protected ThreadState pickNextThread() {
	    /* check to see if something in priorityQueue
	     *if it is empty return null
	     */
	    if(priorityQueue.isEmpty())return null;
	    /* the next thread that this function would pick
	     * is the first one on the queue
	     */
	    return priorityQueue.first();
	    /*int maxPriority = priorityMinimum;
	    ThreadState thread = null;
	    //basically do the max for the priorities of threads
	    for(ThreadState entity : waitQueue){
	    	int priority = entity.getEffectivePriority();

	    	if(thread == null || priority > maxPriority){
	    		thread = entity;
	    		maxPriority = priority;
			}
		}*/

	}
	
	public void print() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    // implement me (if you want)
		for(ThreadState threadState: priorityQueue)
			System.out.print(threadState);
	}
	/**
	 * <tt>true</tt> if this queue should transfer priority from waiting
	 * threads to the owning thread.
	 */
	public boolean transferPriority;
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {																			//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
    	protected List<PriorityQueue> capturedResources;
    	protected PriorityQueue wantedResources;
    	protected int effectivePriority;
		/** There should be some resources here for use*/
		//protected ThreadQueue othersQueues = newThreadQueue(false);
		//protected List<ThreadState> waitQueue = new ArrayList<>();

		protected boolean priorityChanged = false;


	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
	public ThreadState(KThread thread) {
	    this.thread = thread;
	    //Creating new resourced list
	    capturedResources = new ArrayList<PriorityQueue>();
	    wantedResources = new PriorityQueue(false);
	    effectivePriority = priorityMinimum;

	    setPriority(priorityDefault);
	}

	/**
	 * Return the priority of the associated thread.
	 *
	 * @return	the priority of the associated thread.
	 */
	public int getPriority() {
	    return priority;
	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {
	    // implement me

		//need to somehow show that a priority change occurred to change
		//it back after running.
		
		//set the effective priority as the smallest priority
		int effectivePriority = priorityMinimum;

		for(PriorityQueue priorityQueue : capturedResources) {
			for (ThreadState entity : priorityQueue.waitQueue) {
				/* this implements priority donation as it changes the priority of the current
				 * thread after having gone through if it finds a higher priority it becomes
				 * the effective priority
				 */
				effectivePriority = (this.effectivePriority > entity.priority) ? this.effectivePriority : entity.priority;
			}
		}
		//this is for priority being changed
		priorityChanged = true;

	    return effectivePriority;
	}

	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
	    if (this.priority == priority)
		return;
	    
	    this.priority = getEffectivePriority();
	}

	/**
	 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	 * the associated thread) is invoked on the specified priority queue.
	 * The associated thread is therefore waiting for access to the
	 * resource guarded by <tt>waitQueue</tt>. This method is only called
	 * if the associated thread cannot immediately obtain access.
	 *
	 * @param	waitQueue	the queue that the associated thread is
	 *				now waiting on.
	 *
	 * @see	nachos.threads.ThreadQueue#waitForAccess
	 */
	public void waitForAccess(PriorityQueue waitQueue) {
	    // implement me
		//Lib.assertTrue(Machine.interrupt().disable());//COMMENTED OUT THIS LINE. THIS CHECK FAILS INITIALLY
		//disable the interrupts
		boolean result = Machine.interrupt().disable();
		//make sure that it is not in the waitQueue already
		if(!waitQueue.waitQueue.contains(this))
			/* because the thread cannot obtain access to what it needs
			 * it should be added to the waitQueue
			 */
			waitQueue.waitQueue.add(this);

		/*if it has obtained access to what it needs it needs to be
		 * removed 
		 */
		if(capturedResources.contains(waitQueue))
			capturedResources.remove(waitQueue);


	}

	/**
	 * Called when the associated thread has acquired access to whatever is
	 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
	 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
	 * <tt>thread</tt> is the associated thread), or as a result of
	 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
	 *
	 * @see	nachos.threads.ThreadQueue#acquire
	 * @see	nachos.threads.ThreadQueue#nextThread
	 */
	public void acquire(PriorityQueue waitQueue) {
	    // implement me

		//make sure thread is not on the waitQueue 
		if(waitQueue.waitQueue.contains(this))waitQueue.waitQueue.remove(this);
		
		//and make sure waitqueue is not a resource already
		if(!capturedResources.contains(waitQueue))capturedResources.add(waitQueue);
		
		this.getEffectivePriority();

		if(waitQueue == wantedResources){
			wantedResources = null;
		}

	}

		/** The thread with which this object is associated. */
	protected KThread thread;
	/** The priority of the associated thread. */
	protected int priority;

    }
}
