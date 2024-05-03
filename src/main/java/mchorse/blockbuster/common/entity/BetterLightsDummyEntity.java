package mchorse.blockbuster.common.entity;

import dz.betterlights.BetterLightsMod;
import dz.betterlights.lighting.lightcasters.LightCaster;
import net.minecraft.world.World;

public class BetterLightsDummyEntity extends ExpirableDummyEntity
{
    private LightCaster caster;

    public BetterLightsDummyEntity(World worldIn, LightCaster caster, int lifetime)
    {
        this(worldIn, caster, lifetime, 0, 0);
    }

    public BetterLightsDummyEntity(World worldIn, LightCaster caster, int lifetime, float height, float width)
    {
        super(worldIn, lifetime, height, width);
        this.caster = caster;
    }

    public LightCaster getLightCaster()
    {
        return this.caster;
    }

    @Override
    public void onEntityUpdate()
    {
        super.onEntityUpdate();

        if (!this.world.isRemote || this.caster == null) return;

        if (this.isDead)
        {
            BetterLightsMod.getLightManager().removeLightCaster(this.world, this.caster, false);
        }
    }
}
