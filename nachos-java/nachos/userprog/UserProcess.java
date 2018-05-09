package nachos.userprog; ///NEW/////

import nachos.machine.*;
import nachos.network.MailMessage;
import nachos.threads.*;
import nachos.userprog.*;

import javax.jws.soap.SOAPBinding;
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
public class UserProcess implements Comparable{
    /**
     * Allocate a new process.
     */
    public UserProcess() {
	    fileDescriptors = new OpenFile[16];

	    boolean interrupt = Machine.interrupt().disable();

	    processId = processIdCounter++;
	    if(parentProcess == null){
			stdin = UserKernel.console.openForReading();
			stdout = UserKernel.console.openForWriting();
		}else{
			stdin = parentProcess.stdin;
			stdout= parentProcess.stdout;
		}

	    Machine.interrupt().restore(interrupt);

	    fileDescriptors[0] = stdin;
	    fileDescriptors[1] = stdout;

	    childProcesses = new HashMap<>();
	    //exitStats = new HashMap<>();

	    parentProcess = null;
	    lock = new Lock();

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

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() { }

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
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {//System.out.println("READVMEM===========");

		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int firstPage = Processor.pageFromAddress(vaddr); //Should be the first virtual page
		int basePageOffset = Processor.offsetFromAddress(vaddr); //offset from the first page
		int lastPage = Processor.pageFromAddress(vaddr + length); //Should be last page because you have needed data

		TranslationEntry entry = checkPageTable(firstPage, false);

		if(entry == null) return 0;

		int amount = Math.min(length, pageSize - basePageOffset);
		int desPos = Processor.makeAddress(entry.ppn,basePageOffset);

		System.arraycopy(memory, desPos, data, offset, amount);
		offset += amount;

		for(int i = firstPage + 1; i<= lastPage; i++){
			entry = checkPageTable(i, false);
			if (entry == null) return amount;
			int len = Math.min(length - amount, pageSize);
			System.arraycopy(memory, Processor.makeAddress(entry.ppn, 0), data, offset, len);
			offset += len;
			amount += len;
		}
		//System.out.print("====================");
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
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) { //System.out.println("WRITEVMEM===========");
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		byte[] memory = Machine.processor().getMemory();
		int firstPage = Processor.pageFromAddress(vaddr); //Should be the first virtual page
		int basePageOffset = Processor.offsetFromAddress(vaddr); //offset from the first page
		int lastPage = Processor.pageFromAddress(vaddr + length); //Should be last page because you have needed data

		TranslationEntry entry = checkPageTable(firstPage, true);

		if(entry == null) return 0;

		//At this point we should be clear to copy everything
		int amount = Math.min(length, Math.abs(pageSize - basePageOffset));
		int desPos = Processor.makeAddress(entry.ppn,basePageOffset);


		System.arraycopy(data, offset, memory, desPos, amount);

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
		//System.out.print("====================");
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
	protected boolean loadSections() { System.out.println("LOADSECTIONS");
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		int[] physicalPages = UserKernel.allocateSpecificNumPages(numPages);
		pageTable = new TranslationEntry[numPages];

		// load all the coff sections
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");
			//Go through each page of the coff section
			for(int i=0; i<section.getLength(); i++){
				int vpn = section.getFirstVPN() + i;
				int ppn = physicalPages[vpn];//map vpn tp physical page number
				//find that page in memory
				pageTable[vpn] = new TranslationEntry(vpn, ppn, true, section.isReadOnly(), false, false);
				//Load the page from physical memory
				section.loadPage(i,ppn);
			}
		}
		//For all the rest of the pages, just set them to new unwritable sections
		for(int i=numPages-stackPages-1; i<numPages; i++){
			pageTable[i] = new TranslationEntry(i, physicalPages[i], true, false, false, false);
		}
		return true;
	}


    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
	protected void unloadSections() {
		coff.close();//make sure to close the file
		for(int i=0; i < numPages; i++){
			UserKernel.addPhysicalPage(pageTable[i].ppn);
			pageTable[i] = null;
		}
		pageTable = null;
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

	if(this.processId != 0){
		System.out.println("ISN'T THE ROOT PROCESS!!");
		return 0;
	}

	Machine.halt();
	
	Lib.assertNotReached("Machine.halt() did not halt machine!");
	return 0;
    }

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int exitStatus){
		System.out.print("("+processId+")"+" EXITING...");

		//Close all the open files
		//might not be able to do this
		for(OpenFile file : fileDescriptors)
			if(file != null)file.close();
		//Assign exit status to return to parent
		this.exitStatus = exitStatus;
		System.out.println(" status: "+exitStatus);

		this.unloadSections();
		//Set all child parent processes to null
		//childProcesses.forEach(x -> x.parentProcess=null);
		//childProcesses.forEach((x,y) -> y.parentProcess=null);// >:(((((((( they wont let me do it
		//so much uglier
		for(Integer key : childProcesses.keySet())childProcesses.get(key).parentProcess=null;

		//remove all the child
		childProcesses.clear();

		if(this.processId == 0)
			Kernel.kernel.terminate();
		else
			UThread.finish();

		return 0;
	}

	/**
	 * Handle the exec() system call
	 */
	private int handleExec(int file, int argc, int argv){
		System.out.print("EXECUTING...");

		if(argc < 1) return INVALID;

		String filename = readVirtualMemoryString(file, MAXSTRLEN);

		if(filename == null || !filename.endsWith(".coff")) return INVALID;

		String[] args = new String[argc];

		for(int i=0; i<argc; i++){
			byte[] buffer = new byte[4];
			if(readVirtualMemory(argv + i * 4, buffer) != 4)return INVALID;
			if((args[i] = readVirtualMemoryString(Lib.bytesToInt(buffer, 0), MAXSTRLEN)) == null) return INVALID;
		}

		System.out.println("Read arguments creating Child... XD");

		//This is so we can use a child class process like new Process
		UserProcess child = UserProcess.newUserProcess();
		System.out.println("mpid: "+ processId);
		System.out.println("cpid: "+ child.processId);

		if(child.execute(filename, args)){
			childProcesses.put(child.processId,child);
			child.parentProcess = this;
			return child.processId;
		}

		return INVALID;
	}

	/**
	 * Handle the join() system call
	 */
	private int handleJoin(int pid, int status){
		System.out.print("JOINING...");
		if(pid < 0 || status < 0) return INVALID;

		if(!childProcesses.containsKey(pid)) {
			System.out.println("No child with id:" + pid);
			return INVALID;
		}
		UserProcess child = childProcesses.remove(pid);

		//Not living thread
		if(child == null) return INVALID;

		child.thread.join();

		child.parentProcess = null;

		lock.acquire();
		int exStatus = child.exitStatus;
		lock.release();

		byte[] buffer;

		//maybe add a check here
		buffer=Lib.bytesFromInt(exStatus);

		return(writeVirtualMemory(status, buffer) != 4) ? EXCEPTION : 1;
	}

	/**
	 *  Handle the create() system call
	 */
	private int handleCreate(int name) {
		String FileName = readVirtualMemoryString(name, MAXSTRLEN);
		//check to make sure file name is valid
		if(FileName == null)
		{
			return -1;
		}
		//find a free file descriptor
		int fileIndex = -1;
		for(int i = 2; i < 16; i++)
		{
			if(fileDescriptors[i] == null)
			{
				fileIndex = i;
			}
		}
		//error if there is no free file descriptor
		if(fileIndex == -1)
		{
			return -1;
		}

		OpenFile file = ThreadedKernel.fileSystem.open(FileName, true);
		//error if file cannot be created
		if(file == null)
		{
			return -1;
		}
		//create the file with the associated file descriptor
		else
		{
			fileDescriptors[fileIndex] = file;
			return fileIndex;
		}
	}

	/**
	 * Handle the open() system call
	 */
	private int handleOpen(int name) {
		String FileName = readVirtualMemoryString(name, MAXSTRLEN);
		//error if file name is not valid
		if(FileName == null)
		{
			return -1;
		}

		//find a free file descriptor
		int fileIndex = -1;
		for(int i = 2; i < 16; i++)
		{
			if(fileDescriptors[i] == null)
			{
				fileIndex = i;
			}
		}

		//error if no file descriptor is free
		if(fileIndex == -1)
		{
			return -1;
		}

		OpenFile file = ThreadedKernel.fileSystem.open(FileName, false);

		//error if file cannot be created
		if(file == null)
		{
			return -1;
		}
		else
		{
			fileDescriptors[fileIndex] = file;
			return fileIndex;
		}
	}

	/**
	 *  Handle the Read system call
	 */
	private int handleRead(int fileDescriptor, int buffer, int size){

		if (fileDescriptor < 0 || fileDescriptor > 15){
			return -1;		// return -1 on error
		}

		if (size < 0){
			return -1; 		// return -1 on error
		}

		OpenFile file;

		if (fileDescriptors[fileDescriptor] == null){
			return -1;		// return -1 on error
		}else{
			file = fileDescriptors[fileDescriptor];
		}

		byte tempbuff[] = new byte[size];

		int readSize = file.read(tempbuff, 0, size);

		if (readSize < 0){
			return -1;		// return -1 on error
		}
		return writeVirtualMemory(buffer, tempbuff, 0, readSize);
	}

	/**
	 *  Handle the write() system call
	 */
	private int handleWrite(int fileDescriptor, int buffer, int size){
		// return -1 on error
		if (fileDescriptor < 0 || fileDescriptor > 15) return -1;


		OpenFile file;
		if (fileDescriptors[fileDescriptor] == null){
			return -1;
		}else{
			file = fileDescriptors[fileDescriptor];
		}


		if(  size < 0)  return -1;
		if(buffer < 0)  return -1;
		if(buffer == 0) return 0;

		//loop through and increment counter and keep the buffer size small
		//enough not to run out of heap space.
		//System.out.println("687" + size);//STOPS HERE
		int maxWrite = 1024;//should put as public variable
		byte tempBuff[] = new byte[maxWrite];


		//loop through and increment counter and keep the buffer size small
		//enough not to run out of heap space.
		int byteTransferTotal = 0;
		while(size > 0){
			int transferAmount = (size < maxWrite) ? size : maxWrite;

			int readSize = readVirtualMemory(buffer, tempBuff, 0,transferAmount);//could just re-assign this variable

			int sizeWritten = file.write(tempBuff, 0, readSize);

			if(sizeWritten == -1){
				if(byteTransferTotal == 0)
					byteTransferTotal = -1;
				break;
			}

			buffer += sizeWritten;
			size -= sizeWritten;
			byteTransferTotal += sizeWritten;

			if(sizeWritten < transferAmount)
				break;
		}
		return byteTransferTotal;
	}

	/**
	 *  Handle the close() system call
	 */
	private int handleClose(int fd) {
		//if file descriptor not within valid range or the descriptor is null
		//then there is an error
		if((fd < 0) || (fd > 15) || fileDescriptors[fd] == null)
		{
			return -1;
		}
		//close the file associated with file descriptor
		fileDescriptors[fd].close();
		//free the file descriptor
		fileDescriptors[fd] = null;
		return 0;
	}

	/**
	 * Hanlde the Unlink() system call
	 */
	private int handleUnlink(String name) {
		boolean succeeded = ThreadedKernel.fileSystem.remove(name);
		//if the file was not removed return error
		if(!succeeded)
		{
			return -1;
		}
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
	case syscallHalt :
		return handleHalt();
	case syscallExit :
		 return handleExit(a0);
		//collect status us child using join//maybe it does it on its own.
		//if not, make a variable to hold them
		//syscall = 3;
		//return exitStatus;
	case syscallExec :
		return handleExec(a0,a1,a2);
	case syscallJoin :
		return handleJoin(a0,a1);
	case syscallCreate :
		return handleCreate(a0);
	case syscallOpen :
		return handleOpen(a0);
	case syscallRead :
		return handleRead(a0,a1,a2);
	case syscallWrite :
		return handleWrite(a0,a1,a2);
	case syscallClose :
		return handleClose(a0);
	case syscallUnlink :
		if(a0 < 0) return -1;
		String name = readVirtualMemoryString(a0,MAXSTRLEN);
		//if the name is invalid return error
		if(name == null) return -1;
		//remove the file
		return handleUnlink(name);

	default:
	    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	    Lib.assertNotReached("Unknown system call!");
	}
	return 0;
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

    //Added structures and variables//
	public OpenFile[] fileDescriptors;
	protected OpenFile stdin;
	protected OpenFile stdout;

	//int represents the pid of the child
	private HashMap<Integer,UserProcess> childProcesses;
	//private HashMap<Integer, Integer> exitStats;
	private UserProcess parentProcess;

	private int processId;
	private static int processIdCounter;

	private UThread thread;

	private final int EXCEPTION=0;
	private final int SUCCESS=1;
	private final int INVALID=-1;

	private static final int MAXSTRLEN = 256;

	private int exitStatus = EXCEPTION;

	private Lock lock;

	@Override
	public int compareTo(Object o) {
		return (this.processId - ((UserProcess)o).processId);
	}
}
