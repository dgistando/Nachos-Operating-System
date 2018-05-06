package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import sun.nio.cs.ext.MacHebrew;

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

	console = new SynchConsole(Machine.console());

	for(int i=0; i< Machine.processor().getNumPhysPages();i++)
		availablePhysicalPages.add(i);

	Machine.processor().setExceptionHandler(new Runnable() {
		public void run() { exceptionHandler(); }
	    });
    }

    /**
     * Test the console device.
     */	
    public void selfTest() {
	super.selfTest();

	System.out.println("Testing the console device. Typed characters");
	System.out.println("will be echoed until q is typed.");

	char c;

	do {
	    //c = (char) console.readByte(true);
	    c = 'q';//Did this so the console wont run forever
	    console.writeByte(c);

	}
	while (c != 'q');

	System.out.println("");
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
     * @see	nachos.machine.Machine#getShellProgramName
     */
    public void run() {
	super.run();

	UserProcess process = UserProcess.newUserProcess();
	
	String shellProgram = Machine.getShellProgramName();	
	Lib.assertTrue(process.execute(shellProgram, new String[] { }));

	KThread.currentThread().finish();
    }

    //Need to add physical pages
	public static void addPhysicalPage(int ppn){
		Lib.assertTrue(ppn >= 0 && ppn < Machine.processor().getNumPhysPages());
		Machine.interrupt().disable();

		availablePhysicalPages.addFirst(ppn);
		Machine.interrupt().enable();
	}


	public static int[] allocateSpecificNumPages(int num){
	    Machine.interrupt().disable();

	    if(availablePhysicalPages.size() < num){
	        Machine.interrupt().enable();
	        System.out.println("Not enough physical pages. edit available pages in .conf file");
	        return null;
        }

        int[] numFree = new int[num];

	    for(int i=0; i<num; i++)
	        numFree[i] = availablePhysicalPages.remove();

	    Machine.interrupt().enable();

	    return numFree;
    }

    // release physical pages
	public static int getFreePage(){
		int pageNumber = 0;
		Machine.interrupt().disable();

		pageNumber = (availablePhysicalPages.isEmpty())? -1 : availablePhysicalPages.getFirst();

		Machine.interrupt().enable();
		return pageNumber;
	}


    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
	super.terminate();
    }

    /** Globally accessible reference to the synchronized console. */
    public static SynchConsole console;

    // dummy variables to make javac smarter
    private static Coff dummy1 = null;


    //Represents list of available pages for each proccess
    public static LinkedList<Integer> availablePhysicalPages = new LinkedList<Integer>();
}
