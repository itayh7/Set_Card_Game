package bguspl.set.ex;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.stream.Collectors;

import bguspl.set.Env;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    //our fields:
    protected final Vector<Vector<Integer>> placedTokens;
    
    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        
        
        
        this.placedTokens = new Vector<Vector<Integer>>();
        for(int i = 0; i < env.config.players; i++)
        	placedTokens.add(new Vector<Integer>());
        
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        
        
        Integer removedCard = slotToCard[slot]; 
        if(removedCard != null) {
	        for(int i = 0; i < env.config.players; i++)
	        	removeToken(i, slot);

	        cardToSlot[removedCard] = null;
	        slotToCard[slot] = null;
	        env.ui.removeCard(slot);
        }
    }

    /**
     * Returns player's tokens by ID
     *
     * @param player   - the player the token belongs to
     * @return - List of slots which the player puts token on
     */
    public List<Integer> getPlayerTokens(int player){
    	return placedTokens.get(player);
    }
    
    /**
     * Returns slots which have cards on
     *
     * @return - Linked list of slots which have cards on
     */
    public List<Integer> getCardsOnTable() {
    	List<Integer> output = new LinkedList<Integer>();
        for (Integer card : slotToCard)
            if (card != null)
            	output.add(card);
        return output;
    }
    
    /**
     * Returns slots without cards
     *
     * @return - Linked list of slots without cards
     */
    public List<Integer> getEmptySlots() {
    	List<Integer> output = new LinkedList<Integer>();
        for (int i = 0; i<slotToCard.length; i++)
            if (slotToCard[i] == null)
            	output.add(i);
        return output;
    }
    
    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     * 
     * @pre - the player doesnt have the slot in placedTokens
     * @post - the player has the slot in placedTokens
     * @post - the player's name appears on the slot in the ui
     */
    public synchronized void placeToken(int player, int slot) {
    	if(slotToCard[slot] != null && placedTokens.get(player).size() < 3) {
	    	placedTokens.get(player).add(slot);
	    	env.ui.placeToken(player, slot);
    	}
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     * 
     * @post - player doesn't have a token on the slot
     * @post - the player's name doesn't appear on the slot in the ui
     */
    public synchronized boolean removeToken(int player, int slot) {
    	Vector<Integer> currPlayerVector = placedTokens.get(player);
    	boolean removed = currPlayerVector.remove((Integer)slot);

    	if(removed)
    		env.ui.removeToken(player, slot);
    	

        return removed;
    }
}
