package app.runefolio.sync;

import java.lang.reflect.Proxy;
import java.util.EnumSet;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.eventbus.EventBus;
import org.junit.Assert;
import org.junit.Test;

public class RuneFolioDiaryTaskTrackerTest
{
    @Test public void waitsForLoginAndResetsAcrossCharactersAndUnsupportedWorlds()
    {
        GameState[] state = {GameState.LOGIN_SCREEN};
        EnumSet<WorldType> worlds = EnumSet.noneOf(WorldType.class);
        int[] calls = {0};
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,a)->null);
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),new Class[]{Client.class},(p,m,a)->{
            switch(m.getName()) {
                case "getGameState": return state[0];
                case "getWorldType": return worlds;
                case "getLocalPlayer": return state[0] == GameState.LOGGED_IN ? player : null;
                default: return null;
            }
        });
        RuneFolioDiaryTaskTracker tracker = new RuneFolioDiaryTaskTracker(client,new EventBus());
        tracker.startUp(()->{calls[0]++;return true;});
        try {
            tracker.onGameTick(null); Assert.assertEquals(0,calls[0]);
            state[0]=GameState.LOGGED_IN;
            tracker.onGameTick(null); Assert.assertEquals(0,calls[0]);
            tracker.onGameTick(null); Assert.assertEquals(1,calls[0]);
            tracker.onGameTick(null); Assert.assertEquals(1,calls[0]);
            VarbitChanged change = new VarbitChanged(); change.setVarpId(VarPlayerID.ARDOUNGE_ACHIEVEMENT_DIARY);
            tracker.onVarbitChanged(change); tracker.onVarbitChanged(change);
            tracker.onGameTick(null); Assert.assertEquals(2,calls[0]);
            tracker.onRuneScapeProfileChanged(null);
            tracker.onGameTick(null); Assert.assertEquals(2,calls[0]);
            tracker.onGameTick(null); Assert.assertEquals(3,calls[0]);
            worlds.add(WorldType.QUEST_SPEEDRUNNING);
            tracker.onVarbitChanged(change); tracker.onGameTick(null); tracker.onGameTick(null);
            Assert.assertEquals(3,calls[0]);
            worlds.clear();tracker.onGameTick(null);tracker.onGameTick(null);Assert.assertEquals(4,calls[0]);
        } finally { tracker.shutDown(); }
        tracker.onGameTick(null); tracker.onGameTick(null); Assert.assertEquals(4,calls[0]);
    }

    @Test public void sceneLoadsDoNotResyncButLoginScreenDoes()
    {
        GameState[] state = {GameState.LOGGED_IN};
        int[] calls = {0};
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,a)->null);
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),new Class[]{Client.class},(p,m,a)->{
            switch(m.getName()) {
                case "getGameState": return state[0];
                case "getWorldType": return EnumSet.noneOf(WorldType.class);
                case "getLocalPlayer": return state[0] == GameState.LOGGED_IN ? player : null;
                default: return null;
            }
        });
        RuneFolioDiaryTaskTracker tracker = new RuneFolioDiaryTaskTracker(client,new EventBus());
        tracker.startUp(()->{calls[0]++;return true;});
        try {
            tracker.onGameTick(null); tracker.onGameTick(null); Assert.assertEquals(1,calls[0]);
            for (int i = 0; i < 3; i++) {
                tracker.onGameStateChanged(stateChange(GameState.LOADING));
                tracker.onGameStateChanged(stateChange(GameState.LOGGED_IN));
                tracker.onGameTick(null); tracker.onGameTick(null); tracker.onGameTick(null);
                Assert.assertEquals(1,calls[0]);
            }
            // A tick that lands while the scene is still loading must not re-arm a sync either.
            tracker.onGameStateChanged(stateChange(GameState.LOADING));
            state[0]=GameState.LOADING; tracker.onGameTick(null);
            state[0]=GameState.LOGGED_IN; tracker.onGameStateChanged(stateChange(GameState.LOGGED_IN));
            tracker.onGameTick(null); tracker.onGameTick(null); Assert.assertEquals(1,calls[0]);
            tracker.onGameStateChanged(stateChange(GameState.LOGIN_SCREEN));
            state[0]=GameState.LOGIN_SCREEN; tracker.onGameTick(null);
            state[0]=GameState.LOGGED_IN; tracker.onGameStateChanged(stateChange(GameState.LOGGED_IN));
            tracker.onGameTick(null); Assert.assertEquals(1,calls[0]);
            tracker.onGameTick(null); Assert.assertEquals(2,calls[0]);
        } finally { tracker.shutDown(); }
    }

    private static GameStateChanged stateChange(GameState state)
    {
        GameStateChanged event = new GameStateChanged();
        event.setGameState(state);
        return event;
    }
}
