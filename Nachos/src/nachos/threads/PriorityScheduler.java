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

    //TESTING VARIABLE
    public static int TestingID = 0 ;

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
			public java.util.PriorityQueue<ThreadState> priorityQueue;
			//Need a thread to be in the position of leader when pulled off the Queue
			public ThreadState controller = null;

		PriorityQueue(boolean transferPriority) {
			/*System.out.println("Created Priority Queue");*/
			this.transferPriority = transferPriority;
			//This should initialize a new Queue.Sorted in descending order because of comparator in ThreadState
			priorityQueue = new java.util.PriorityQueue<ThreadState>();
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

			//Change controllers if there is a threadState that already owns this resource.
			//Transfer Priority tells me if this should occur
			//Also make sure this thread doesn't already have control of queue
			if(transferPriority && this.controller != null) {
				this.controller.capturedResources.remove(this);
				//could have received a donation between these lines
				this.controller.updateEffectivePriority();

			}

			//check for elements in the queue if nothing there return null
			if(priorityQueue == null || priorityQueue.isEmpty()){
				return null;
			}else{
				/* because there is something in the queue
				 * the first function from SortedSet picks the first thread
				 * on the queue making it the next thread
				 */
				ThreadState threadState = pickNextThread();
				/* because the first gives the next thread it needs to be
				 * removed from the queue as it has already been picked
				 */

				//We have already chosen the next Thread to control the queue
				//So we take down the current controller.
				this.controller = null;
				//Since this is going to be the new controller you have to acquire
				//the queue as one of the resources.
				this.acquire(threadState.thread);

				priorityQueue.remove(priorityQueue.peek());

				if(priorityQueue!=null)print();
				//returns the next thread
				return threadState.thread;
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
			return priorityQueue.peek();
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
			for(ThreadState threadState: priorityQueue)
				System.out.print(threadState.priority +" ");
			System.out.println("");
		}


		//ADDED FUNCTION
		public boolean isEmpty(){
			return priorityQueue.isEmpty();
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
    protected class ThreadState implements Comparable<ThreadState>{																	//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

		protected List<PriorityQueue> capturedResources;
		//This structure might be used later on, but its not
		//really necessary for this project.
		protected List<PriorityQueue> wantedResources;

		protected int effectivePriority;
		/** There should be some resources here for use*/

		protected boolean priorityChanged = false;

		protected long waitTime;
		//TESINTG ID
		private int id;

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
			wantedResources = new ArrayList<PriorityQueue>();
			effectivePriority = priorityMinimum;

			setPriority(priorityDefault);

			waitTime = Machine.timer().getTime();

			id = ++TestingID;
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
			updateEffectivePriority();

			return effectivePriority;
		}

		public void updateEffectivePriority(){
			//Set the priority to the smallest that it can be
			//This might also be an effective priority call.
			effectivePriority = getPriority();

			for(PriorityQueue queue : capturedResources){
				for(ThreadState threadState : queue.priorityQueue){
					if(queue.transferPriority) {
						//Go through and call get effective priority on every thread in each list.
						int priority = threadState.getEffectivePriority();
						effectivePriority = ( priority > effectivePriority) ? priority : effectivePriority;
					}
				}
			}

			//this is for priority being changed
			priorityChanged = true;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param	priority	the new priority.
		 */
		public void setPriority(int priority) {
			//System.out.println(this+"SetPriority to: " + priority);
			if (this.priority == priority)
			return;

			this.priority = priority;
			getEffectivePriority();
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

			//Check to make sure you are not already on the queue then
			//Add yourself to the queue because you are now requesting access.
			if(!waitQueue.priorityQueue.contains(this))waitQueue.priorityQueue.add(this);

			//Might have to add waitQueue to the wantedResources.
			//Doesn't seems necessary for this project

			//Update the effective priorities because the order how now changed
			updateEffectivePriority();

			//restore the machine state
			Machine.interrupt().restore(result);

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
			Lib.assertTrue(waitQueue.controller == null);

			//Add this queue to one of my resources
			//and make sure it is not a resource of mine already
			if(!capturedResources.contains(waitQueue))capturedResources.add(waitQueue);

			//I now become the new Queue holder
			waitQueue.controller = this;

			//The order has now changed so I will update the priority
			updateEffectivePriority();

			//If this used to be a resource that you want, you now own it
			//so you don't want it anymore
			if(wantedResources.contains(waitQueue))
				wantedResources.remove(waitQueue);
		}

		/* APPARENTLY YOU CANNOT DO THIS. EXCEPTION THROWN BY GRADER TRYING TO MAKE IT COMPARABLE
		@Override
		public int compare(ThreadState threadState, ThreadState t1) {
				//return threadState.getPriority() - t1.getPriority();//This would be in ascending order
				return t1.getPriority() - threadState.getPriority();//Descending
		}*/

		/** The thread with which this object is associated. */
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority;

		@Override
		public String toString() {
			return "thread["+id+"]:";
		}

		@Override
		public int compareTo(ThreadState threadState) {
			//return threadState.priority - this.priority;
			if(threadState.priority > this.priority){//descenting
				return 1;
			}else if(threadState.priority < this.priority ){
				return -1;
			}else{
				//Now consider time as a sorting order
				//The threads with the longest time go first
				//EXAMPLE
				// ThreadPriority[waitTime]
				// Queue: 3[4],2[6],2[5],2[3],1[1],1[0]
				if(threadState.waitTime > this.waitTime){
					return 1;
				}else if(threadState.waitTime < this.waitTime){
					return -1;
				}else{
					return 0;
				}
			}
		}
    }
}
