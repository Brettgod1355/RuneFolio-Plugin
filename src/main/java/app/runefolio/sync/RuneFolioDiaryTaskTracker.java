package app.runefolio.sync;

import java.util.function.BooleanSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;

/** Debounces passive diary changes; never retains another character's task state. */
@Singleton
final class RuneFolioDiaryTaskTracker
{
    private final Client client;
    private final EventBus eventBus;
    private BooleanSupplier syncAction;
    private boolean dirty;
    private int readyTicks;

    @Inject
    RuneFolioDiaryTaskTracker(Client client, EventBus eventBus)
    {
        this.client = client;
        this.eventBus = eventBus;
    }

    void startUp(BooleanSupplier action)
    {
        syncAction = action;
        reset();
        eventBus.register(this);
    }

    void shutDown()
    {
        eventBus.unregister(this);
        syncAction = null;
        reset();
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) { reset(); }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) { reset(); }

    private void reset()
    {
        dirty = true;
        readyTicks = 0;
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (RuneFolioDiaryTaskFlags.observes(event.getVarpId(), event.getVarbitId())) dirty = true;
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.JOURNALSCROLL) dirty = true;
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null
            || !RuneFolioWorldPolicy.supports(client.getWorldType()))
        {
            reset();
            return;
        }
        if (readyTicks < 2) readyTicks++;
        if (readyTicks < 2 || !dirty || syncAction == null) return;
        dirty = false;
        // Connection/manual/periodic snapshots handle a disconnected callback.
        syncAction.getAsBoolean();
    }
}
