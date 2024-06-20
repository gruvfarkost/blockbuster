package mchorse.blockbuster.utils;

import mchorse.blockbuster.events.TickHandler;
import net.minecraft.world.World;

public class ExpirableRunnable implements TickHandler.IRunnable
{
    private boolean isDead;
    private int age;
    private int lifetime;

    public ExpirableRunnable(int lifetime)
    {
        this.lifetime = lifetime;
    }

    public void setLifetime(int lifetime)
    {
        this.lifetime = lifetime;
    }

    public int getLifetime()
    {
        return this.lifetime;
    }

    public int getAge()
    {
        return this.age;
    }

    @Override
    public void run()
    {
        if (this.age >= this.lifetime)
        {
            this.isDead = true;
        }

        this.age++;
    }

    @Override
    public boolean shouldRemove()
    {
        return this.isDead;
    }
}
