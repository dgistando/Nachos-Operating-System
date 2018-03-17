package nachos.threads;
import nachos.ag.BoatGrader;
import nachos.machine.Machine;

import java.util.ArrayList;
import java.util.List;

public class Boat
{
    static BoatGrader bg;

    public static int childCountOahu;
    public static int adultCountOahu;
    public static int childCountMolokai;
    public static int adultCountMolokai;

    public static boolean passengerChild;
    public static boolean pilotChild;
    public static boolean done;
    public static boolean boatAtOahu;

    public static Condition2 passenger;
    public static Condition2 pilot;
    public static Condition2 doneCon;
    public static Condition2 runCon;

    public static Lock runLock;
    public static Lock passengerLock;
    public static Lock pilotLock;
    public static Lock doneLock;


    private static List<KThread> waitingChildren;

    public static void selfTest()
    {
        BoatGrader b = new BoatGrader();

        System.out.println("\n ***Testing Boats with only 2 children***");
        begin(0, 2, b);

    }

    public static void begin(int adults, int children, BoatGrader b )
    {
        // Store the externally generated autograder in a class
        // variable to be accessible by children.
        bg = b;

        // Instantiate global variables here

        childCountOahu = 0;
        adultCountOahu = 0;
        childCountMolokai = 0;
        adultCountMolokai = 0;

        passengerChild = false;
        pilotChild = false;
        done = false;
        boatAtOahu = true;

        runLock = new Lock();
        passengerLock = new Lock();
        pilotLock = new Lock();
        doneLock = new Lock();

        //DONE
        passenger = new Condition2(passengerLock);
        pilot = new Condition2(pilotLock);
        doneCon = new Condition2(doneLock);
        runCon = new Condition2(runLock);

        doneLock.acquire();

        for(int i=0; i < children ;i++){
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    ChildItinerary();
                }
            };
            KThread thread = new KThread(runnable);
            thread.setName(i+"");
            thread.fork();
        }

        for(int i=0; i < adults ;i++){
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    AdultItinerary();
                }
            };
            KThread thread = new KThread(runnable);
            thread.setName(i+"");
            thread.fork();
        }


        while((childCountMolokai + adultCountMolokai) != (adults+children)){
            doneCon.wake();
            doneCon.sleep();
        }

        done = true;
        doneCon.sleep();

    }


    /**
     * This method just defines all the adult behavior for the problem.
     * Whether they are on Molokai or on Oahu.
     * */
    static void AdultItinerary()
    {
        //bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.
        //DO NOT PUT ANYTHING ABOVE THIS LINE.

	/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
	*/
        //Everytime we call this method we increase the count of adults on Oahu because that where they start
        adultCountOahu++;

        int oahuChildren;
        int molokaiChildren;
        //Whoever the pilot is at this point needs control because they will be waking and sleeping
        pilotLock.acquire();
        //This is true at first because the problem starts out here
        boolean onOahu = true;

        while(!done){
            if(onOahu){//At the beginning of the problem. On Oahu and still not finished
                oahuChildren = childCountOahu;
                while(oahuChildren != 1 || !boatAtOahu){//The pilot needs to start moving people
                    pilot.wake();
                    pilot.sleep();
                    oahuChildren = childCountOahu;
                }


                bg.AdultRowToMolokai();
                //An adult is leaving oahu
                adultCountOahu--;
                adultCountMolokai++;

                oahuChildren = childCountOahu;
                molokaiChildren = childCountMolokai;
                //If they piloted the boat it is no longer on oahu. Molokai now.
                boatAtOahu = false;
                onOahu = false;
            }else{// The person must be on Molokai
                molokaiChildren = childCountMolokai;

                while(boatAtOahu){//If the boat is at oahu make sure people keep piloting their way to molokai
                    pilot.wake();
                    pilot.sleep();
                    molokaiChildren = childCountMolokai;
                }

                if(molokaiChildren == 0){//We have to go back and get the children when there are no more on molokai
                    bg.AdultRowToOahu();

                    adultCountOahu++;
                    adultCountMolokai--;

                    oahuChildren = childCountOahu;
                    molokaiChildren = childCountMolokai;

                    //The boat and the adult are both on molokai
                    boatAtOahu = true;
                    onOahu = true;
                }
            }
            pilot.wake();
            pilot.sleep();
        }
        pilot.sleep();
    }

    /**
     * This defines the behavior of the children for the Boat problem
     *
     *Not input or output
     *
     * */
    static void ChildItinerary()
    {
        //bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
        //DO NOT PUT ANYTHING ABOVE THIS LINE.
        boolean onOahu = true; // pilot is at Oahu

        childCountOahu++;

        int oahuChildren = childCountOahu;
        int oahuAdult = adultCountOahu;

        pilotLock.acquire(); // acquire lock
        pilot.wake(); // wake pilot
        pilot.sleep(); // sleep pilot

        while(!done){
            /**
             *
             * **/
            if(onOahu){
                oahuChildren = childCountOahu;
                oahuAdult = adultCountOahu;

                if(boatAtOahu){
                    pilot.wake(); // wake pilot
                    pilot.sleep(); // sleep pilot

                    oahuChildren = childCountOahu;
                    oahuAdult = adultCountOahu;
                    // checks to see if there is other children on Oahu
                    if(oahuChildren > 1){

                        if(!pilotChild){
                            pilotChild = true;
                            while(!passengerChild){
                                pilot.wake(); // wake pilot
                                pilot.sleep(); // sleep pilot
                            }
                            bg.ChildRowToMolokai(); // calls boatGrader

                            childCountOahu--; // decrement child count on Oahu
                            childCountMolokai++; // increment child count on Molokai

                            boatAtOahu = false; // boat is at Molokai
                            onOahu = false; // person is at molokai

                            pilotChild = false; // adult is pilot

                            pilot.wake(); // wake pilot
                            pilot.sleep(); // sleep pilot

                            oahuChildren = childCountOahu;
                            oahuAdult = adultCountOahu;
                        }else{
                            if(!passengerChild){
                                passengerChild = true; // child is passenger meaning child is also pilot

                                pilot.wake(); // wake pilot
                                pilot.sleep(); // sleep pilot

                                bg.ChildRideToMolokai(); // calls boat grader

                                childCountOahu--; // decrement child count on Oahu
                                childCountMolokai++; // increment child count on Molokai

                                oahuChildren = childCountOahu;
                                oahuAdult = adultCountOahu;

                                boatAtOahu = false; // boat at Molokai
                                onOahu = false; // pilot at Molokai

                                passengerChild = false; // adult is pilot
                            }
                        }
                    }
                    //check to see if number of adults on Oahu is greater than 0
                    else if(oahuAdult > 0){
                        oahuChildren = childCountOahu; //set number of children to children counter
                        oahuAdult = adultCountOahu; //set number of adults to adult counter
                        pilot.wakeAll(); //wake pilot
                        pilot.sleep(); //pilot has rowed boat
                    }else{
                        //if there are no more adults a child rows to Molokai
                        bg.ChildRowToMolokai();

                        //decrease child count on Oahu
                        childCountOahu--;
                        //increase child count on Molokai
                        childCountMolokai++;

                        //set number of children to children counter
                        oahuChildren = childCountOahu;
                        //set number of children to children counter
                        oahuAdult = adultCountOahu;

                        //because child has rowed to Molokai boat and child would no longer be on Oahu
                        boatAtOahu = false;
                        onOahu = false;

                        //interupt the machine
                        boolean res = Machine.interrupt().disable();

                        doneLock.acquire();
                        doneCon.wake();

                        doneLock.release();

                        //restore the machine
                        Machine.interrupt().restore(res);

                    }
                }
            }
            else{ //child is now on Molokai

                //check to see if the total number of people is greater than 0
                if((oahuChildren + oahuAdult) == 0){
                    //interrupt machine
                    boolean res = Machine.interrupt().disable();
                    doneLock.acquire();
                    doneCon.wake();
                    doneLock.release();
                    //restore machine
                    Machine.interrupt().restore(res);
                    //checks to see if the boat is not Oahu and there is no passenger
                }else if(!boatAtOahu && !passengerChild){
                    //because there is no passenger the child would ride to Oahu
                    bg.ChildRideToOahu();
                    //increase child count on Oahu
                    childCountOahu++;
                    //because the child rode to Oahu Molokai's child count decreases
                    childCountMolokai--;

                    oahuChildren = childCountOahu;
                    oahuAdult = adultCountOahu;

                    //boat and child are back on Oahu
                    boatAtOahu = true;
                    onOahu = true;
                }
            }

            //interrupt the machine
            boolean res = Machine.interrupt().disable();
            doneLock.acquire();
            doneCon.wake();
            doneLock.release();
            //restore the machine
            Machine.interrupt().restore(res);

            pilot.wake();
            pilot.sleep();
        }

        //interrupt the machine
        boolean res = Machine.interrupt().disable();
        doneLock.acquire();
        doneCon.wake();
        doneLock.release();
        //restore the machine
        Machine.interrupt().restore(res);
        //sleep the pilot
        pilot.sleep();

    }

    static void SampleItinerary()
    {
        // Please note that this isn't a valid solution (you can't fit
        // all of them on the boat). Please also note that you may not
        // have a single thread calculate a solution and then just play
        // it back at the autograder -- you will be caught.
        System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
        bg.AdultRowToMolokai();
        bg.ChildRideToMolokai();
        bg.AdultRideToMolokai();
        bg.ChildRideToMolokai();
    }


}
