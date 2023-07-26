package bguspl.set.ex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import bguspl.set.Env;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;
    
    
    //our fields:
    private final BlockingQueue<Integer> playersSetsToCheck;
    private final List<Integer> cardsToRemove;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    
    private long lastReshuffle = System.currentTimeMillis();

    private Thread dealerThread;
    
    private List<Integer> hintsForAI;
    
    
    private final int RANDOM_ADDITION = 3;
    
    private boolean changingCards;
    private final Object cardsLocker;
    
    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        playersSetsToCheck = new LinkedBlockingQueue<Integer>(players.length);
        cardsToRemove = new LinkedList<Integer>();
        hintsForAI = new ArrayList<Integer>();
        changingCards = true;
        cardsLocker = new Object();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        dealerThread = Thread.currentThread();
        Thread[] playersThreads = new Thread[players.length];
        
	    for(int i = 0; i<players.length; i++) { //initialize players threads
	        playersThreads[i] = new Thread(players[i], "player_"+i);
	        playersThreads[i].start();
	    }
        	
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        
        for(int i = 0; i<players.length; i++) { //waiting for the players threads to finish before the dealer
			try {playersThreads[i].join();} 
        	catch (InterruptedException e) {} 
        }
        
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
    	terminate = true;
    	for(Player player: players)
    		player.terminate();
    	dealerThread.interrupt();
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     * 
     * @pre - 0 <= cardsToRemove.size() <= table size
     * @post - cardsToRemove is empty
     * @post - number of cards on table = number of @pre cards on table - @pre cardsToRemove.size()
     * @post - slots in @pre cardsToRemove are empty
     * @post - ui shows empty slots where the cards used to be
     */
    protected void removeCardsFromTable() {
        boolean wasRemoved = !cardsToRemove.isEmpty();
    	if(!cardsToRemove.isEmpty()) {
    		changingCards = true;
		    while(!cardsToRemove.isEmpty())
		    	table.removeCard(cardsToRemove.remove(0));
    	}
        if(wasRemoved && deck.isEmpty())
            updateHints();
        
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
    	List<Integer> slotsToPlaceCard = table.getEmptySlots();
    	Random random = new Random();
    	boolean wasPlaced = !slotsToPlaceCard.isEmpty() && !deck.isEmpty(); //true if dealer needs to place cards

		while(!slotsToPlaceCard.isEmpty() && !deck.isEmpty()) {
		    Integer emptyRandomSlot = slotsToPlaceCard.remove(random.nextInt(slotsToPlaceCard.size()));
		    Integer randomCard = deck.remove(random.nextInt(deck.size()));
		    table.placeCard(randomCard, emptyRandomSlot);
		}
    	
    	if(wasPlaced) { //reset timer, print hints if needed and update hints for the AI
    		updateTimerDisplay(true);
    		if(env.config.hints)
    			table.hints();

    		updateHints();
    	}
    	
    		
    }

    	private void updateHints() { //update hints for AI + reshuffles if needed + notify
    		Random random = new Random();
    		hintsForAI.clear();
    		List<Integer> cardsOnTable = Arrays.stream(table.slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
    		List<int[]> randomSet = env.util.findSets(cardsOnTable, 1);
    		int[] set = new int[0];
    		
    		if(!randomSet.isEmpty()) { //there is no set on table
    			set = randomSet.get(0);
    			if(env.config.turnTimeoutMillis <= 0)
    				reshuffleTime = Long.MAX_VALUE;
    		}
    		else if(env.config.turnTimeoutMillis <= 0) //Bonus Mode: there is no set on table
    			reshuffleTime = 0;

    		for(int i = 0; i<set.length; i++) //add set to hintsForAI
    			hintsForAI.add(table.cardToSlot[set[i]]);
    		
    		int hintsSize = Integer.min(env.config.featureSize+RANDOM_ADDITION,env.config.tableSize);
    		while(hintsForAI.size() < hintsSize) { //fill hintsForAI list with random cards in addition to the set
    			int randomSlot = random.nextInt(env.config.tableSize);
    			if(hintsForAI.indexOf(randomSlot) == -1)
    				hintsForAI.add(randomSlot);
    		}
    		
    		changingCards = false; //notify all players when done changing cards
    		synchronized(cardsLocker) { 
    			cardsLocker.notifyAll();
    		}
    	}
    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
    	long newTimeMillis = reshuffleTime - System.currentTimeMillis();
    	boolean warn = newTimeMillis <= env.config.turnTimeoutWarningMillis;
    	long remainedMillisToUpdateTime = (newTimeMillis) % 1000;
    	
    	
    	Integer playerToCheck = null;
    	try {
    		playerToCheck = playersSetsToCheck.poll(warn ? 30 : remainedMillisToUpdateTime+1, TimeUnit.MILLISECONDS);
            //wait until there is an element in playersSetsToCheck, or need to update timer display
    	} catch (InterruptedException e) {}
    	
    	if(playerToCheck != null) { //if player claimed a set, check his set and give him point or penalty
    		List<Integer> thisPlayerTokens = table.getPlayerTokens(playerToCheck);
        	if(thisPlayerTokens.size() == 3) {
        		List<Integer> tokensToCards = new LinkedList<Integer>();
        		for(Integer x : thisPlayerTokens)
        			tokensToCards.add(table.slotToCard[x]);
        		boolean isSet = env.util.findSets(tokensToCards, 1).size() == 1;
        		if(isSet) { 
        			for(Integer slotID: thisPlayerTokens)
        				cardsToRemove.add(slotID);
        			players[playerToCheck].point();
        		}
        		else
        			players[playerToCheck].penalty();
        		
        		
        	}
        	players[playerToCheck].setCheckingSet(false);
        		
    	}
    		
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
    	if(reset) {
    		if(env.config.turnTimeoutMillis > 0) //regular mode (timer goes down)
    			reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis+700; //the +700 is to fix the display of second 59
    		else //bonus mode
    			lastReshuffle = System.currentTimeMillis();
    	}
    		    
    	if(env.config.turnTimeoutMillis > 0) { //if on regular mode display timer
    		long newTimeMillis = reshuffleTime - System.currentTimeMillis();
        	boolean warn = newTimeMillis <= env.config.turnTimeoutWarningMillis;
        	if(newTimeMillis<0)
        		newTimeMillis=0;
    		env.ui.setCountdown(newTimeMillis, warn);
    	}
    	else if(env.config.turnTimeoutMillis == 0) //if on bonus mode displays time elapsed from last reshuffle
    		env.ui.setElapsed(System.currentTimeMillis() - lastReshuffle);
    		
    	for(int i = 0; i<players.length; i++) { //display player's freeze time and notify when his freeze time is over
    		long freezeMillis = players[i].getFreezeTime() - System.currentTimeMillis();
    		env.ui.setFreeze(i, freezeMillis);
    		if(freezeMillis <= 0 && !players[i].isCheckingSet()) //check that we don't notify while the dealer is checking the player's set
    			synchronized(players[i]) {
    				players[i].notifyAll();
    			}
    	}
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
    	changingCards = true;
	    Random random = new Random();
	    List<Integer> cardsOnTable = table.getCardsOnTable();
	    while(!cardsOnTable.isEmpty()) {
	    	Integer cardToRemove = cardsOnTable.remove(random.nextInt(cardsOnTable.size()));
	    	deck.add(cardToRemove);
	    	table.removeCard(table.cardToSlot[cardToRemove]);
	    }
    	
    }

    /**
     * Returns the slots which soon will be empty
     * 
     * @return List of the slots which soon will be empty
     */
    public List<Integer> getCardsToRemove() {
        return cardsToRemove;
    }
    
    
    public List<Integer> getHintsForAI() {
        return hintsForAI;
    }
    
    
    public boolean isChangingCards() {
        return changingCards;
    }
    
    public Object getCardsLocker() {
        return cardsLocker;
    }     
    /**
     * Returns the queue of players that have sets to check
     * 
     * @return BlockingQueue of players IDs
     */
    
    public BlockingQueue<Integer> getPlayersSetsToCheck() {
        return playersSetsToCheck;
    }
    /**
     *  Displays the winners.
     */
    private void announceWinners() {
    	env.ui.announceWinner(getWinners());
    }

    /**
     * Check who is/are the winner/s 
     * 
     * @return int array of the winners
     *
     * @post - 0 < getWinners().size() <= players.length 
     */
    protected int[] getWinners(){
        int maxPlayerScore = -1;
    	List<Integer> winners = new LinkedList<Integer>();
    	for(int i = 0; i<players.length; i++) {
    		int currPlayerScore = players[i].getScore();
    		if(currPlayerScore > maxPlayerScore) {
    			winners.clear();
    			winners.add(i);
    			maxPlayerScore = currPlayerScore;
    		}
    		else if(currPlayerScore == maxPlayerScore)
    			winners.add(i);
    	}
    	
    	int[] winnersArray = new int[winners.size()];
    	for(int i = 0; winners.size() > 0; i++) 
    		winnersArray[i] = winners.remove(0);

        return winnersArray;
    }

}
