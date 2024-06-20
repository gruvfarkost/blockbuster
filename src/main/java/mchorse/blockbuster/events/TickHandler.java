package mchorse.blockbuster.events;

import mchorse.mclib.utils.DummyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.*;

public class TickHandler
{
    /**
     * The Set is identity based so the same Runnable instance cannot be executed multiple times for one event.
     */
    private final Map<TickIdentifier, Set<Runnable>> runnables = new HashMap<>();

    public void addRunnable(Class<? extends TickEvent> eventType, Side side, Runnable runnable)
    {
        this.addRunnable(eventType, side, TickEvent.Phase.START, runnable);
    }

    public void addRunnable(Class<? extends TickEvent> eventType, Side side, TickEvent.Phase phase, Runnable runnable)
    {
        this.runnables.computeIfAbsent(new TickIdentifier(eventType, phase, side),
                        k -> Collections.newSetFromMap(new IdentityHashMap<>()))
                .add(runnable);
    }

    public void removeRunnable(Class<? extends TickEvent> eventType, Side side, TickEvent.Phase phase, Runnable runnable)
    {
        TickIdentifier identifier = new TickIdentifier(eventType, phase, side);

        Set<Runnable> runnableSet = this.runnables.get(identifier);
        if (runnableSet != null)
        {
            runnableSet.remove(runnable);

            if (runnableSet.isEmpty())
            {
                this.runnables.remove(identifier);
            }
        }
    }

    public void removeRunnable(Class<? extends TickEvent> eventType, Side side, Runnable runnable)
    {
        this.removeRunnable(eventType, side, TickEvent.Phase.START, runnable);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWorldTick(TickEvent.WorldTickEvent event)
    {
        this.runRunnables(new TickIdentifier(TickEvent.WorldTickEvent.class, event.phase, event.side));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        Class<? extends TickEvent> eventType = TickEvent.PlayerTickEvent.class;

        /*
         * The PlayerTickEvent is called for every player on a server.
         * Here we do a workaround to call an event type only once on the client side during playertick.
         */
        if (event.side == Side.CLIENT && Minecraft.getMinecraft().player == event.player)
        {
            eventType = WorldClientTickEvent.class;
        }

        this.runRunnables(new TickIdentifier(eventType, event.phase, event.side));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientTick(TickEvent.ClientTickEvent event)
    {
        this.runRunnables(new TickIdentifier(TickEvent.ClientTickEvent.class, event.phase, event.side));
    }

    private void runRunnables(TickIdentifier identifier)
    {
        Set<Runnable> runnableSet = this.runnables.get(identifier);

        if (runnableSet == null) return;

        for (Iterator<Runnable> i = runnableSet.iterator(); i.hasNext();) {
            final Runnable runnable = i.next();

            runnable.run();
            if (runnable instanceof IRunnable &&  ((IRunnable) runnable).shouldRemove())
            {
                i.remove();
            }
        }

        if (runnableSet.isEmpty())
        {
            this.runnables.remove(identifier);
        }
    }

    public interface IRunnable extends Runnable
    {
        public boolean shouldRemove();
    }

    public static class TickIdentifier
    {
        public final Class<? extends TickEvent> eventType;
        public final TickEvent.Phase eventPhase;
        public final Side side;

        public TickIdentifier(Class<? extends TickEvent> eventType, TickEvent.Phase eventPhase, Side side)
        {
            this.eventType = eventType;
            this.eventPhase = eventPhase;
            this.side = side;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof TickIdentifier)) return false;
            TickIdentifier that = (TickIdentifier) o;
            return Objects.equals(eventType, that.eventType) && eventPhase == that.eventPhase && side == that.side;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(eventType, eventPhase, side);
        }
    }

    /**
     * Forge's WorldTickEvent is only fired for servers. I don't want to ASM bytecode inject my own tick event.
     * There is PlayerTickEvent but it is fired for every player on a server leading to weird behavior if a method should
     * only be executed once per tick. This class is a sort of Adapter workaround.
     */
    public static class WorldClientTickEvent extends TickEvent.WorldTickEvent {
        public WorldClientTickEvent(Phase phase, World world) {
            super(Side.CLIENT, phase, world);
        }
    }
}
