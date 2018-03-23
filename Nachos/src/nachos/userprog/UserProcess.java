package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
    	//Not sure how many files to allow open at once
    	fileTable = new OpenFile[16];

		/** Assign this process a unique ID */
		this.processID = processID++;


		int numPhysPages = Machine.processor().getNumPhysPages();
	pageTable = new TranslationEntry[numPhysPages];
	for (int i=0; i<numPhysPages; i++)
	    pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
    }
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	
	new UThread(this).setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int firstPage = Processor.pageFromAddress(vaddr); //Should be the first virtual page
		int basePageOffset = Processor.offsetFromAddress(vaddr); //offset from the first page
		int lastPage = Processor.pageFromAddress(vaddr + length); //Should be last page because you have needed data

		TranslationEntry entry = checkPageTable(firstPage, false);

		if(entry == null) return 0;

		int amount = Math.min(length, pageSize - basePageOffset);

		System.arraycopy(memory, Processor.makeAddress(entry.ppn, basePageOffset), data, offset, amount);

		offset += amount;

		for(int i = firstPage + 1; i<= lastPage; i++){
			entry = checkPageTable(i, false);

			if (entry == null) return amount;

			int len = Math.min(length - amount, pageSize);

			System.arraycopy(memory, Processor.makeAddress(entry.ppn, 0), data, offset, len);

			offset += len;
			amount += len;
		}

		// for now, just assume that virtual addresses equal physical addresses
		//if (vaddr < 0 || vaddr >= memory.length)
		//	return 0;

		//int amount = Math.min(length, memory.length-vaddr);
		//System.arraycopy(memory, vaddr, data, offset, amount);

		return amount;
    }


    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {

		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int firstPage = Processor.pageFromAddress(vaddr); //Should be the first virtual page
		int basePageOffset = Processor.offsetFromAddress(vaddr); //offset from the first page
		int lastPage = Processor.pageFromAddress(vaddr + length); //Should be last page because you have needed data

		TranslationEntry entry = checkPageTable(firstPage, true);

		if(entry == null) return 0;

		//At this point we should be clear to copy everything
		int amount = Math.min(length, pageSize - basePageOffset);
		System.arraycopy(data, offset, memory,
						 Processor.makeAddress(entry.ppn,basePageOffset),
						 amount);
		//since we wrote we have to move the offset down the page for future writes
		offset += amount;

		for(int i = firstPage+1; i<=lastPage; i++){
			entry = checkPageTable(i, true);

			if(entry == null) return amount;

			int len = Math.min(length - amount, pageSize);

			System.arraycopy(data, offset, memory, Processor.makeAddress(entry.ppn, 0), len);

			offset += len;
			amount += len;
		}

																		// for now, just assume that virtual addresses equal physical addresses
																		//if (vaddr < 0 || vaddr >= memory.length)
																		//    return 0;

																		//int amount = Math.min(length, memory.length-vaddr);
																		//System.arraycopy(data, offset, memory, vaddr, amount);

		return amount;
    }


	private TranslationEntry checkPageTable(int vpn, boolean wantWrite){

		if(vpn < 0 || vpn >= numPages) return null; //This shouldn't ever happen. Just check to see if in range

		TranslationEntry pageSegment = pageTable[vpn];

		if(pageSegment == null) return null;

		if(pageSegment.readOnly && wantWrite) return null;
		//This means its clear to write to this location
		pageSegment.used = true;

		if(wantWrite) pageSegment.dirty = true;

		return pageSegment;
	}


    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

	if (!loadSections())
	    return false;

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
	if (numPages > Machine.processor().getNumPhysPages()) {
	    coff.close();
	    Lib.debug(dbgProcess, "\tinsufficient physical memory");
	    return false;
	}

		//check to see if tou have the correct number of physical pages.

		//need to keep track of available pages

		//user kernelhas linkedlist on. int for available pages.


		/*
		// load sections
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
				  + " section (" + section.getLength() + " pages)");

			for (int i=0; i<section.getLength(); i++) {
			int vpn = section.getFirstVPN()+i;

			// for now, just assume virtual addresses=physical addresses
			section.loadPage(i, vpn);
			}
		}*/
	
	return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
    }    

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {

	Machine.halt();

	Lib.assertNotReached("Machine.halt() did not halt machine!");
	return 0;
    }

	private int handleCreate(int name) {
		String FileName = readVirtualMemoryString(name, 256);
		if(FileName == null)
		{
			return -1;
		}

		int fileIndex = -1;
		for(int i = 2; i < 16; i++)
		{
			if(fileTable[i] == null)
			{
				fileIndex = i;
			}
		}

		if(fileIndex == -1)
		{
			return -1;
		}

		OpenFile file = ThreadedKernel.fileSystem.open(FileName, true);
		if(file == null)
		{
			return -1;
		}
		else
		{
			fileTable[fileIndex] = file;
			return fileIndex;
		}
	}

	private int handleOpen(int name) {
		String FileName = readVirtualMemoryString(name, 256);
		if(FileName == null)
		{
			return -1;
		}

		int fileIndex = -1;
		for(int i = 2; i < 16; i++)
		{
			if(fileTable[i] == null)
			{
				fileIndex = i;
			}
		}

		if(fileIndex == -1)
		{
			return -1;
		}

		OpenFile file = ThreadedKernel.fileSystem.open(FileName, false);
		if(file == null)
		{
			return -1;
		}
		else
		{
			fileTable[fileIndex] = file;
			return fileIndex;
		}
	}

	private int handleClose(int fd) {
		if((fd < 0) || (fd > 15) || fileTable[fd] == null)
		{
			return -1;
		}

		fileTable[fd].close();
		fileTable[fd] = null;
		return 0;
	}



	private static final int
	syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
			case syscallClose:
				return handleClose(a0);
			case syscallJoin:
				return handleJoin(a0, a1);
			case syscallHalt:
				return handleHalt();
			case syscallCreate:
				return handleCreate(a0);
			case syscallOpen:
				return handleOpen(a0);

			//default:
			//    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			//    Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}


	/** handleJoin will suspend execution of this process (and wait) until the
	 * specified child process finishes execution.
	 *
	 * @param pid unique identifier for child process
	 * @param status value that was passed by the child process in when called by exit()
	 *               this value will determine if the child exited normally
	 * @return
	 */

	private int handleJoin(int pid, int status) {

		/** Check to make sure this a valid process and exists */
		if (!childProcesses.contains(pid) || !processedThreadMap.containsKey(pid)) {
			return INVALID;
		}

		/** Declare byte array*/
		byte[] byteProcessStatus = new byte[256];

		/** Extract the child process and join it, and remove
		 *  from child set upon finishing */
		UThread childProcess = processedThreadMap.get(pid);
		childProcess.join();
		childProcesses.remove(pid);


		Lib.bytesFromInt(byteProcessStatus, 0, childProcess.process.processStatus);

		/** ASK TA IF THIS IS NEEDED */
		readVirtualMemory(status, byteProcessStatus);


		if(childProcess.process.exitStatus == SUCCESS) {
			return SUCCESS;
		}

		return EXCEPTION;
	}

	/** handleExit will terminate this current process immediately. If this is the last
	 * process then the Kernel will also be terminated. Exit status will be passed to the parent
	 * in case join() will be called
	 * @param status this is the value returned to the parent as the child's exit status
	 */
	private void handleExit(int status){

		processStatus = status;


		// Close all remaining open files
		for (int i = 0; i < fileTable.length; i++){
			OpenFile file = fileTable[i];
			if (file != null) {
				file.close();
			}
		}

		this.unloadSections();
		processedThreadMap.remove(this.processID);

		// If this is the last process, terminate Kernel
		if(processedThreadMap.isEmpty()){
			Kernel.kernel.terminate();
		}


		exitStatus = SUCCESS;
		UThread.finish();



	}



	/**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    Lib.assertNotReached("Unexpected exception");
	}
    }


	/** Return values for our syscall functions (task 3)*/
	private final int EXCEPTION = 0;
	private final int SUCCESS = 1;
	private final int INVALID = 2;

	/** This will store our User Threads and corresponding ID number */
	HashMap<Integer, UThread> processedThreadMap = new HashMap<Integer, UThread>();

	/** This will store the IDs for the forked children of this process */
	HashSet<Integer> childProcesses = new HashSet<Integer>();

	/** Unique ID for process that will be assigned in constructor*/
	private int processID = 0;

	/** The current status of the process*/
	private int processStatus;

	/** This will help tell us if a process is exiting abnormally*/
	/** By default, it will be exception until successfully exited */
	private int exitStatus = EXCEPTION;

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    private int argc, argv;
	
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';

    OpenFile[] fileTable;
}
