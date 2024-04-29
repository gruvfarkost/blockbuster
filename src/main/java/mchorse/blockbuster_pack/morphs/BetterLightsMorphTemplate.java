package mchorse.blockbuster_pack.morphs;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;

import javax.annotation.Nullable;

/**
 * Declare the optional methods here, so they don't cause NoSuchMethod Exceptions when the mod is not loaded
 */
public abstract class BetterLightsMorphTemplate extends AbstractMorph {
    protected void createDummyEntitiy() { }

    protected void createDummyEntitiy(@Nullable EntityLivingBase target) { }

    protected void addToWorld() { }

    protected void updateLightcaster() {}

    @Override
    public void render(EntityLivingBase entity, double x, double y, double z, float entityYaw, float partialTicks) {}
}
