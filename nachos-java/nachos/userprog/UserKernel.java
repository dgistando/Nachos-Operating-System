package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.util.LinkedList;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
    /**
     * Allocate a new user kernel.
     */
    public UserKernel() {
	super();
    }

    /**
     * Initialize this kernel. Creates a synchronized console and sets the
     * processor's exception handler.
     */
    public void initialize(String[] args) {
		super.initialize(args);

		availablePages = new LinkedList<>();

		console = new SynchConsole(Machine.console());


		int numPages = Machine.processor().getNumPhysPages();
		for(int page=0; page<numPages ; page++)
			availablePages.add(page);

		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() { exceptionHandler(); }
			});
    }

    /**
     * Test the console device.
     */	
    public void selfTest() {
	super.selfTest();
	/*
	System.out.println("Testing the console device. Typed characters");
	System.out.println("will be echoed until q is typed.");

	char c;

	do {
	    c = (char) console.readByte(true);
	    console.writeByte(c);
	}
	while (c != 'q');

	System.out.println("");*/
    }

    /**
     * Returns the current process.
     *
     * @return	the current process, or <tt>null</tt> if no process is current.
     */
    public static UserProcess currentProcess() {
	if (!(KThread.currentThread() instanceof UThread))
	    return null;
	
	return ((UThread) KThread.currentThread()).process;
    }

    /**
     * The exception handler. This handler is called by the processor whenever
     * a user instruction causes a processor exception.
     *
     * <p>
     * When the exception handler is invoked, interrupts are enabled, and the
     * processor's cause register contains an integer identifying the cause of
     * the exception (see the <tt>exceptionZZZ</tt> constants in the
     * <tt>Processor</tt> class). If the exception involves a bad virtual
     * address (e.g. page fault, TLB miss, read-only, bus error, or address
     * error), the processor's BadVAddr register identifies the virtual address
     * that caused the exception.
     */
    public void exceptionHandler() {
	Lib.assertTrue(KThread.currentThread() instanceof UThread);

	UserProcess process = ((UThread) KThread.currentThread()).process;
	int cause = Machine.processor().readRegister(Processor.regCause);
	process.handleException(cause);
    }

    /**
     * Start running user programs, by creating a process and running a shell
     * program in it. The name of the shell program it must run is returned by
     * <tt>Machine.getShellProgramName()</tt>.
     *
     * @see	nachos.machine.Machine #getShellProgramName
     */
    public void run() {
	super.run();

	UserProcess process = UserProcess.newUserProcess();
	
	String shellProgram = Machine.getShellProgramName();	
	Lib.assertTrue(process.execute(shellProgram, new String[] { }));

	KThread.currentThread().finish();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
		super.terminate();
    }

    //Page management functions//
	public static void addPhysicalPage(int page){
    	Lib.assertTrue(page >= 0 && page < Machine.processor().getNumPhysPages());
    	Machine.interrupt().disable();

    	availablePages.addFirst(page);

		Machine.interrupt().enable();
	}

	public static int[]allocateSpecificNumPages(int num){
    	Machine.interrupt().disable();

    	if(availablePages.size() < num){//don't have enough pages
			System.out.println("Not enough pages. Please allocate more. BUY MORE RAM");
    		Machine.interrupt().enable();
    		return null;
		}

		int[] numFree = new int[num];

    	for(int i=0; i<num; i++)//get all existing pages needed
    		numFree[i] = availablePages.remove();

    	Machine.interrupt().enable();
    	return numFree;
	}

	public static int getFreePage(){
		int pageNumber = 0;
		Machine.interrupt().disable();

		pageNumber = (availablePages.isEmpty()) ? -1 : availablePages.getFirst();

		Machine.interrupt().enable();
		return pageNumber;
	}
	/////////////////////////////

    /** Globally accessible reference to the synchronized console. */
    public static SynchConsole console;

    // dummy variables to make javac smarter
    private static Coff dummy1 = null;

    //Added some extras
	private static int offset, offsetMask;
	private static Lock kernelLock;
	private static LinkedList<Integer> availablePages;

}
