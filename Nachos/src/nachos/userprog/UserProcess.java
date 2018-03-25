package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;

import java.util.*;

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
		fileTable = new OpenFile[16];
		processID = tpid++;

		fileTable[0] = UserKernel.console.openForReading();
		fileTable[1] = UserKernel.console.openForWriting();

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

		UThread thread = new UThread(this);

		thread.setName(name).fork();
		System.out.println("adding child id: "+thread.process.processID);
		processedThreadMap.put(thread.process.processID, thread);

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
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {System.out.println("READVMEM");

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
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) { System.out.println("WRITEVMEM");
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		byte[] memory = Machine.processor().getMemory();


		int firstPage = Processor.pageFromAddress(vaddr); //Should be the first virtual page

		int basePageOffset = Processor.offsetFromAddress(vaddr); //offset from the first page

		int lastPage = Processor.pageFromAddress(vaddr + length); //Should be last page because you have needed data



		TranslationEntry entry = checkPageTable(firstPage, true);



		if(entry == null) return 0;



		//At this point we should be clear to copy everything

		int amount = Math.min(length, Math.abs(pageSize - basePageOffset));

		System.arraycopy(data, offset, memory,

				Processor.makeAddress(entry.ppn,basePageOffset),

				amount);

		//since we wrote we have to move the offset down the page for future writes

		offset += amount;



		for(int i = firstPage+1; i<=lastPage; i++){

			entry = checkPageTable(i, true);



			if(entry == null) return amount;



			int len = Math.min(length - amount, pageSize);


			//public static void arraycopy(
			// Object src,
			//int srcPos,
			//Object dest,
			//int destPos,
			//int length)

			System.arraycopy(data, offset, memory, Processor.makeAddress(entry.ppn, 0), len);



			offset += len;

			amount += len;

		}
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

		// load sections
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			//Might need to do this in load instead of here.
			for(int i=0; i<section.getLength(); i++){
				int vpn = section.getFirstVPN() + i;
				int ppn = physicalPages[vpn];

				pageTable[vpn] = new TranslationEntry(vpn, ppn, true, section.isReadOnly(), false, false);
				section.loadPage(i,ppn);
			}

			/**for (int i=0; i<section.getLength(); i++) {
				int vpn = section.getFirstVPN()+i;
				//Go to page table and get the proper sections
				TranslationEntry entry = pageTable[vpn];
				entry.readOnly = section.isReadOnly();
				//The page table entry shows you where
				//the physical segment it
				int ppn = entry.ppn;
				//Load the physical page segment.
				section.loadPage(i, ppn);

				// for now, just assume virtual addresses=physical addresses//not for Proj2
				//section.loadPage(i, vpn);
			}*/
		}

		for(int i=numPages-stackPages-1; i<numPages; i++){
			pageTable[i] = new TranslationEntry(i, physicalPages[i], true, false, false, false);
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() { //Unload Sections
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
		//check if processID is root
		if(this.processID != 0)
		{
			return 0;
		}
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	private int handleCreate(int name) {
		String FileName = readVirtualMemoryString(name, 256);
		//check to make sure file name is valid
		if(FileName == null)
		{
			return -1;
		}
		//find a free file descriptor
		int fileIndex = -1;
		for(int i = 2; i < 16; i++)
		{
			if(fileTable[i] == null)
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
			fileTable[fileIndex] = file;
			return fileIndex;
		}
	}

	private int handleOpen(int name) {
		String FileName = readVirtualMemoryString(name, 256);
		//error if file name is not valid
		if(FileName == null)
		{
			return -1;
		}

		//find a free file descriptor
		int fileIndex = -1;
		for(int i = 2; i < 16; i++)
		{
			if(fileTable[i] == null)
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
			fileTable[fileIndex] = file;
			return fileIndex;
		}
	}

	private int handleClose(int fd) {
		//if file descriptor not within valid range or the descriptor is null
		//then there is an error
		if((fd < 0) || (fd > 15) || fileTable[fd] == null)
		{
			return -1;
		}
		//close the file associated with file descriptor
		fileTable[fd].close();
		//free the file descriptor
		fileTable[fd] = null;
		return 0;
	}

	private int handleRead(int fileDescriptor, int buffer, int size){



		if (fileDescriptor < 0 || fileDescriptor > 15){

			return -1;		// return -1 on error

		}



		if (size < 0){

			return -1; 		// return -1 on error

		}



		OpenFile file;



		if (fileTable[fileDescriptor] == null){

			return -1;		// return -1 on error

		}

		else{

			file = fileTable[fileDescriptor];

		}



		byte tempbuff[] = new byte[size];



		int readSize = file.read(tempbuff, 0, size);



		if (readSize < 0){

			return -1;		// return -1 on error

		}



		int counter = writeVirtualMemory(buffer, tempbuff, 0, size);

		return counter;



	}

	private int handleWrite(int fileDescriptor, int buffer, int size){



		if (fileDescriptor < 0 || fileDescriptor > 15){

			return -1;		// return -1 on error

		}



		if (size < 0){

			return -1; 		// return -1 on error

		}



		OpenFile file;



		if (fileTable[fileDescriptor] == null){

			return -1;		// return -1 on error

		}

		else{

			file = fileTable[fileDescriptor];

		}



		byte tempbuff[] = new byte[size];

		int readSize = file.read(tempbuff, 0, size);

		int counter = file.write(tempbuff, 0, readSize);



		if (counter < 0){

			return -1;		// return -1 on error

		}

		return counter;



	}

	private int handleUnlink(String name) {
		boolean succeeded = ThreadedKernel.fileSystem.remove(name);
		//if the file was not removed return error
		if(!succeeded)
		{
			return -1;
		}
		return 0;
	}


	private int handleExec(int filePtr, int argc, int argv) {
		System.out.println("EXECUTE_CALLED");


		/** Check to make sure argc is positive */

		if (argc < 0){
			System.out.println("INVALID ARG stuff");
			return INVALID;

		}



		/** Buffer and  String array will hold the arguments from the file */

		byte [] bufferArgs = new byte[argc * 256];

		String[] stringArgs = new String[argc];



		String filename = readVirtualMemoryString(filePtr, 256);



		/** Check filename */

		if (filename == null){
			System.out.println("INVALID FILENAME");
			Lib.debug(dbgProcess, "INVALID FILENAME");

			return INVALID;

		}



		for(int i = 0; i < argc; i++) {

			byte[] bufferPtr = new byte[4];

			int offsetArg = (argv + i) * 4;

			readVirtualMemory(offsetArg, bufferPtr);

			int argAddr = Lib.bytesToInt(bufferPtr, 0);

			stringArgs[i] = readVirtualMemoryString(argAddr, 256);

		}


		System.out.println("creating child process");
		UserProcess child = new UserProcess();
		childID = child.processID;

		System.out.print("childID: "+ childID);
		System.out.println("my pid: "+processID+" child id: "+child.processID);

		if(child.execute(filename, stringArgs) && childID == child.processID){
			System.out.println("Supposed to add stuff here!! ");
			this.childProcesses.add(child.processID);

			System.out.println("My id: " + processID);
			System.out.println("Child id: " + child.processID);

			return SUCCESS;
		}



		return EXCEPTION;

	}

	private int handleJoin(int pid, int status) {
		System.out.println("Join pid:"+pid +" and "+status);

		/** Check to make sure this a valid process and exists */

		if(!childProcesses.contains(pid))
			System.out.println("NOT THE RIGHT PID!!");
		if(!processedThreadMap.containsKey(pid))
			System.out.println("MAP DOESNT HAVE KEY!!");

		if (!childProcesses.contains(pid) || !processedThreadMap.containsKey(pid)) {

			return INVALID;

		}

		/** Declare byte array*/

		byte[] byteProcessStatus = new byte[256];



		/** Extract the child process and join it, and remove
		 *  from child set upon finishing */
		UThread childProcess = processedThreadMap.get(pid);

		System.out.println("My id: " + processID);
		System.out.println("Child id: " + childProcess.process.processID);

		System.out.println("Kthread join");
		childProcess.join();
		System.out.println("Removing process from set");
		childProcesses.remove(pid);






		Lib.bytesFromInt(byteProcessStatus, 0, childProcess.process.processStatus);


		//System.out.println("reading the virtual memory");

		/** ASK TA IF THIS IS NEEDED */
		//readVirtualMemory(status, byteProcessStatus);
		writeVirtualMemory(status, byteProcessStatus);

		if(childProcess.process.exitStatus == SUCCESS) {
			System.out.println("Should be join success");
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
		System.out.println("Exit pid: "+processID);


		processStatus = status;



		/** Close all remaining open files*/

		for (int i = 0; i < fileTable.length; i++){

			OpenFile file = fileTable[i];

			if (file != null) {

				file.close();

			}

		}


		System.out.println("unloading sections");
		this.unloadSections();

		System.out.println("removing process ID from map");
		processedThreadMap.remove(this.processID);
		System.out.println("sizeMap: "+processedThreadMap.size());

		/*
		//check if called exit on init thread
		//if so exit and close Kernel
		if(this.processID == 0){
			Kernel.kernel.terminate();
		}else {
			exitStatus = SUCCESS;
			//if not, a thread must want to exit
			KThread.currentThread().finish();
		}*/



		/** If this is the last process, terminate Kernel*/

		if(processedThreadMap.isEmpty()){

			Kernel.kernel.terminate();

		}



		exitStatus = SUCCESS;

		UThread.finish();
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
			case syscallHalt:
				return handleHalt();
			case syscallCreate:
				return handleCreate(a0);
			case syscallOpen:
				return handleOpen(a0);
			case syscallRead:
				return handleRead(a0,a1,a2);
			case syscallWrite:
				return handleWrite(a0,a1,a2);
			case syscallJoin:
				return handleJoin(a0,a1);
			case syscallExit:
				handleExit(a0);
				return exitStatus;
			case syscallExec:
				return handleExec(a0,a1,a2);
			case syscallUnlink:
				if(a0 < 0)
				{
					return -1;
				}
				String name = readVirtualMemoryString(a0,256);
				//if the name is invalid return error
				if(name == null)
				{
					return -1;
				}
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
				System.out.println("!!Exception Triggered: " + Processor.exceptionNames[cause]+"!!");
				Lib.debug(dbgProcess, "Unexpected exception: " +
						Processor.exceptionNames[cause]);
				Lib.assertNotReached("Unexpected exception");
		}
	}


	private final int EXCEPTION = 0;

	private final int SUCCESS = 1;

	private final int INVALID = 2;

	public static HashMap<Integer, UThread> processedThreadMap = new HashMap<Integer, UThread>();

	HashSet<Integer> childProcesses = new HashSet<Integer>();

	private int processStatus;

	private int exitStatus = EXCEPTION;

	private int childID;


	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	private int processID = 0;
	//Made it static because it needs to be the same for all processes
	private static int tpid=0;
	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;
	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';

	private OpenFile[] fileTable;

}
