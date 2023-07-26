package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OurDealerTest {

    Player[] players;
    Dealer dealer;
    Table table;


    @BeforeEach
    void setUp() {

        Properties properties = new Properties();
        properties.put("Rows", "2");
        properties.put("Columns", "2");
        properties.put("FeatureSize", "3");
        properties.put("FeatureCount", "4");
        properties.put("TableDelaySeconds", "0");
        properties.put("PlayerKeys1", "81,87,69,82");
        properties.put("PlayerKeys2", "85,73,79,80");
        properties.put("ComputerPlayers", "2");
        properties.put("HumanPlayers", "2");
        properties.put("PenaltyFreezeSeconds", "3");

        MockLogger logger = new MockLogger();
        Config config = new Config(logger, properties);
        

        Env env = new Env(logger, config, new MockUserInterface(), new MockUtil());
        table = new Table(env);
        players = new Player[config.players];
        dealer = new Dealer(env, table, players);
        for (int i = 0; i < players.length; i++)
            players[i] = new Player(env, dealer, table, i, i < env.config.humanPlayers);
    }


    private void fillAllSlots() {
        for (int i = 0; i < table.slotToCard.length; ++i) {
            table.slotToCard[i] = i;
            table.cardToSlot[i] = i;
        }
    }
    


    @Test
    void checkIfSetCardsRemoved() {
        fillAllSlots();
        List<Integer> cardsToRemove = dealer.getCardsToRemove();
        cardsToRemove.add(0);
        cardsToRemove.add(1);
        cardsToRemove.add(2);
        dealer.removeCardsFromTable();
        for (int i = 0; i < table.slotToCard.length; ++i) {
            assertEquals(i<3 ? true : false, table.slotToCard[i] == null);
        }

    }

    @Test
    void checkTie() {

        for (int i = 0; i < players.length; i++)
            players[i].point();
        
        assertEquals(players.length, dealer.getWinners().length);

    }


    static class MockUserInterface implements UserInterface {
        @Override
        public void dispose() {}
        @Override
        public void placeCard(int card, int slot) {}
        @Override
        public void removeCard(int slot) {}
        @Override
        public void setCountdown(long millies, boolean warn) {}
        @Override
        public void setElapsed(long millies) {}
        @Override
        public void setScore(int player, int score) {}
        @Override
        public void setFreeze(int player, long millies) {}
        @Override
        public void placeToken(int player, int slot) {}
        @Override
        public void removeTokens() {}
        @Override
        public void removeTokens(int slot) {}
        @Override
        public void removeToken(int player, int slot) {}
        @Override
        public void announceWinner(int[] players) {}
    };

    static class MockUtil implements Util {
        @Override
        public int[] cardToFeatures(int card) {
            return new int[0];
        }

        @Override
        public int[][] cardsToFeatures(int[] cards) {
            return new int[0][];
        }

        @Override
        public boolean testSet(int[] cards) {
            return false;
        }

        @Override
        public List<int[]> findSets(List<Integer> deck, int count) {
            return null;
        }

        @Override
        public void spin() {}
    }

    static class MockLogger extends Logger {
        protected MockLogger() {
            super("", null);
        }
    }
}
