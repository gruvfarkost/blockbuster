package mchorse.blockbuster_pack.morphs;

import dz.betterlights.BetterLightsMod;
import dz.betterlights.lighting.lightcasters.LightCaster;
import dz.betterlights.lighting.lightcasters.StaticLightCaster;
import dz.betterlights.lighting.lightcasters.features.ILightConfig;
import dz.betterlights.utils.BetterLightsConstants;
import dz.betterlights.utils.ConfigProperty;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.RenderingHandler;
import mchorse.blockbuster.client.render.tileentity.TileEntityGunItemStackRenderer;
import mchorse.blockbuster.client.render.tileentity.TileEntityModelItemStackRenderer;
import mchorse.blockbuster.common.entity.BetterLightsDummyEntity;
import mchorse.blockbuster.common.entity.ExpirableDummyEntity;
import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.client.render.VertexBuilder;
import mchorse.mclib.config.values.*;
import mchorse.mclib.utils.*;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.utils.Animation;
import mchorse.metamorph.api.morphs.utils.IAnimationProvider;
import mchorse.metamorph.api.morphs.utils.IMorphGenerator;
import mchorse.metamorph.api.morphs.utils.ISyncableMorph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;

import static dz.betterlights.utils.ConfigProperty.EnumPropertyType.COLOR_PICKER;

public class BetterLightsMorph extends BetterLightsMorphTemplate implements IAnimationProvider, ISyncableMorph, IMorphGenerator {
    private ExpirableDummyEntity dummy;
    /**
     * Cast this to LightCaster
     * Type is Object as the BetterLights dependency is optional.
     */
    private Object lightCaster;
    private final Vector3d position = new Vector3d();
    private final Vector3d prevPosition = new Vector3d();
    private final Matrix4d rotation = new Matrix4d();
    /**
     * Helper variable to detect a render cycle and store if the morph was rendered in hands, gui etc.
     * true when the last update tick happened. Is set to false when a render cycle starts.
     */
    private boolean updateTick = false;
    private boolean renderedInHand = false;
    private boolean renderedItemGui = false;
    private boolean enableAlways = false;
    /**
     * Animation and stuff - is set to the lightcaster
     */
    private final BetterLightsProperties properties = new BetterLightsProperties();
    private BetterLightsAnimation animation = new BetterLightsAnimation();
    /**
     * The values of this morph set by GUI and serialization
     */
    private final ValueSerializer values = new ValueSerializer();
    /**
     * Cached so in case the mod is not loaded the old nbt will be serialized so no data is lost.
     */
    private NBTTagCompound nbt;

    public BetterLightsMorph() {
        super();
        this.name = "betterLights";
        this.values.copy(BetterLightsProperties.TEMPLATE);
    }

    public ValueSerializer getValueManager()
    {
        return this.values;
    }

    public boolean isEnableAlways() {
        return this.enableAlways;
    }

    public void setEnableAlways(boolean enableAlways) {
        this.enableAlways = enableAlways;
    }

    public boolean isMorphEnabled() {
        return Blockbuster.enableBetterLights.get() || this.enableAlways;
    }

    @Optional.Method(modid = BetterLightsConstants.ID)
    public LightCaster getLightcaster()
    {
        /*
         * keep a lightcaster instance no matter what the blockbuster config enable setting is.
         * if the config is disabled, the dummy entity will die out and remove the lightcaster from betterlights.
         * We need a lightcaster instance to render the spotlight cone.
         */
        if (this.lightCaster == null) {
            LightCaster lightCaster = new StaticLightCaster();
            lightCaster.setPermanent(true);

            this.lightCaster = lightCaster;
        }

        return (LightCaster) this.lightCaster;
    }

    @Override
    @Optional.Method(modid = BetterLightsConstants.ID)
    public void update(EntityLivingBase target)
    {
        this.updateTick = true;
        if (target.world.isRemote && (!this.renderedItemGui || this.renderedInHand) && this.isMorphEnabled())
        {
            /* bruh this is for everything else except immersive editor as it does not update and creates a new instance wtf */
            this.createDummyEntitiy(target);
            this.dummy.setLifetime(this.dummy.getAge() + 1);
        }

        this.animation.update();

        super.update(target);
    }

    @SideOnly(Side.CLIENT)
    private void updateAnimation(float partialTicks)
    {
        this.properties.from(this);

        if (this.animation.isInProgress())
        {
            this.animation.apply(this.properties, partialTicks);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    @Optional.Method(modid = BetterLightsConstants.ID)
    protected void createDummyEntitiy()
    {
        this.createDummyEntitiy(null);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @Optional.Method(modid = BetterLightsConstants.ID)
    protected void createDummyEntitiy(@Nullable EntityLivingBase target)
    {
        if ((this.dummy == null || this.dummy.isDead) && this.isMorphEnabled())
        {
            /*
             * if the rendered position is 0, it is likely that the morph has not been rendered yet
             * and the target entity position is far away from 0, if the dummy entity is set to 0 it will have update issues
             * because it is out of range, so set it to the target entity position for the start.
             */
            if (this.position.equals(new Vector3d(0, 0, 0)) && target != null)
            {
                this.position.set(target.posX, target.posY, target.posZ);
                this.prevPosition.set(target.posX, target.posY, target.posZ);
            }

            this.dummy = new BetterLightsDummyEntity(Minecraft.getMinecraft().world, this.getLightcaster(), 0);

            this.updateDummyEntityPosition();
            this.updateLightcaster();
            this.addToWorld();
        }
    }

    @Override
    @Optional.Method(modid = BetterLightsConstants.ID)
    protected void addToWorld() {
        Minecraft.getMinecraft().world.addEntityToWorld(this.dummy.getEntityId(), this.dummy);
        BetterLightsMod.getLightManager().addTemporaryLightCaster(Minecraft.getMinecraft().world, this.getLightcaster(), false);
    }

    private void updateDummyEntityPosition()
    {
        this.dummy.prevPosX = this.prevPosition.x;
        this.dummy.prevPosY = this.prevPosition.y;
        this.dummy.prevPosZ = this.prevPosition.z;
        this.dummy.lastTickPosX = this.prevPosition.x;
        this.dummy.lastTickPosY = this.prevPosition.y;
        this.dummy.lastTickPosZ = this.prevPosition.z;

        this.dummy.setPosition(this.position.x, this.position.y, this.position.z);
    }

    @Override
    @Optional.Method(modid = BetterLightsConstants.ID)
    protected void updateLightcaster()
    {
        BetterLightsProperties.APPLY.forEach((consumer) -> consumer.accept(this));
        this.getLightcaster().pos((float) this.position.x, (float) this.position.y, (float) this.position.z);
    }

    @Override
    public void renderOnScreen(EntityPlayer entityPlayer, int x, int y, float scale, float alpha) {
        float partial = Minecraft.getMinecraft().getRenderPartialTicks();
        this.updateAnimation(partial);

        GL11.glPushMatrix();
        GL11.glTranslatef(x, y - scale / 2, 0);
        GL11.glScalef(1.35F, -1.35F, 1.35F);

        RenderingUtils.renderImage(new ResourceLocation(Blockbuster.MOD_ID, "textures/spotlight.png"), scale);

        /* we can't use lightcaster here because we also want to render when the mod is not loaded */
        java.util.Optional<GenericBaseValue<?>> oValue = this.properties.values.getValue("Color");

        if (oValue.isPresent())
        {
            Color color = ((ValueColor) oValue.get()).get();

            RenderingUtils.renderImage(new ResourceLocation(Blockbuster.MOD_ID, "textures/spotlight_light.png"), scale, color);
        }

        GL11.glPopMatrix();
    }

    @Override
    @Optional.Method(modid = BetterLightsConstants.ID)
    public void render(EntityLivingBase entity, double x, double y, double z, float entityYaw, float partialTicks) {
        if (OptifineHelper.isOptifineShadowPass())
        {
            return;
        }

        this.updateAnimation(partialTicks);

        EntityLivingBase lastItemHolder = RenderingHandler.getLastItemHolder();
        ItemCameraTransforms.TransformType itemTransformType = RenderingHandler.itemTransformType;

        boolean renderedInHands = lastItemHolder != null && (itemTransformType == ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND || itemTransformType == ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND);
        boolean renderedInThirdPerson = lastItemHolder != null && (itemTransformType == ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND || itemTransformType == ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND);
        boolean itemRendering = TileEntityModelItemStackRenderer.isRendering() || TileEntityGunItemStackRenderer.isRendering();

        /* begin of a new rendering cycle */
        if (this.updateTick)
        {
            this.renderedInHand = false;
            this.renderedItemGui = false;
            this.updateTick = false;
        }

        /* set the rendering variables once per render cycle */
        if (renderedInHands || renderedInThirdPerson) this.renderedInHand = true;
        if (itemTransformType == ItemCameraTransforms.TransformType.GUI && itemRendering) this.renderedItemGui = true;

        GlStateManager.pushMatrix();
        GL11.glTranslated(x, y, z);

        Matrix4d[] transformation = MatrixUtils.getTransformation();
        transformation[1].transpose();
        this.rotation.set(transformation[1]);
        /* rendered not in first person */
        if (renderedInThirdPerson || lastItemHolder == null && (!itemRendering || itemTransformType == ItemCameraTransforms.TransformType.GROUND))
        {
            this.position.x = (float) transformation[0].m03;
            this.position.y = (float) transformation[0].m13;
            this.position.z = (float) transformation[0].m23;

        }
        /* for rendering in first person */
        else if (renderedInHands)
        {
            this.position.x = (float) Interpolations.lerp(lastItemHolder.prevPosX, lastItemHolder.posX, partialTicks);
            this.position.y = (float) Interpolations.lerp(lastItemHolder.prevPosY, lastItemHolder.posY, partialTicks) + lastItemHolder.getEyeHeight();
            this.position.z = (float) Interpolations.lerp(lastItemHolder.prevPosZ, lastItemHolder.posZ, partialTicks);

            double offsetZ = lastItemHolder.getEntityBoundingBox().maxZ - lastItemHolder.getEntityBoundingBox().minZ;
            Vector3d offset = new Vector3d(0, 0, offsetZ);
            this.rotation.transform(offset);
            this.position.add(offset);
        }

        /* for immersive editor as it does not call update method -> update age here */
        if (GuiModelRenderer.isRendering() && this.isMorphEnabled()) {
            this.createDummyEntitiy(entity);
            this.dummy.setLifetime(this.dummy.getAge() + 2);
        }

        if (this.dummy != null && this.isMorphEnabled())
        {
            this.updateDummyEntityPosition();
        }

        this.updateLightcaster();

        if ((Minecraft.getMinecraft().gameSettings.showDebugInfo || GuiModelRenderer.isRendering()) && this.lightCaster != null)
        {
            this.renderSpotlightCone(2);
        }

        this.prevPosition.set(this.position);

        GlStateManager.popMatrix();
    }

    @Optional.Method(modid = BetterLightsConstants.ID)
    protected void renderSpotlightCone(int lineThickness)
    {
        float outerRadius = this.getLightcaster().getDistance() * (float) Math.tan(Math.toRadians(this.getLightcaster().getOuterAngle()));
        float innerRadius = Math.min(outerRadius, this.getLightcaster().getDistance() * (float) Math.tan(Math.toRadians(this.getLightcaster().getInnerAngle())));
        Vector3d direction = new Vector3d(0, 0, this.getLightcaster().getDistance());
        Color color = new Color(this.getLightcaster().getColor().x, this.getLightcaster().getColor().y, this.getLightcaster().getColor().z);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        if (GuiModelRenderer.isRendering()) GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderingUtils.renderCircle(direction, direction, outerRadius, 32, color, lineThickness);
        RenderingUtils.renderCircleDotted(direction, direction, innerRadius, 32, color, lineThickness, 1);

        builder.begin(GL11.GL_LINE_STRIP, VertexBuilder.getFormat(true, false, false, false));
        GL11.glLineWidth(lineThickness);

        builder.pos(0,0,0).color(color.r, color.g, color.b, color.a).endVertex();
        builder.pos(outerRadius, 0, this.getLightcaster().getDistance()).color(color.r, color.g, color.b, color.a).endVertex();

        builder.pos(0,0,0).color(color.r, color.g, color.b, color.a).endVertex();
        builder.pos(-outerRadius, 0, this.getLightcaster().getDistance()).color(color.r, color.g, color.b, color.a).endVertex();

        builder.pos(0,0,0).color(color.r, color.g, color.b, color.a).endVertex();
        builder.pos(0, outerRadius, this.getLightcaster().getDistance()).color(color.r, color.g, color.b, color.a).endVertex();

        builder.pos(0,0,0).color(color.r, color.g, color.b, color.a).endVertex();
        builder.pos(0, -outerRadius, this.getLightcaster().getDistance()).color(color.r, color.g, color.b, color.a).endVertex();

        Tessellator.getInstance().draw();
        GlStateManager.enableTexture2D();
        if (GuiModelRenderer.isRendering()) GlStateManager.enableDepth();
    }

    /**
     * @return true, as BetterLightsMorph needs the entity in the bodypart to create a dummy entity with the correct position.
     */
    @Override
    public boolean useTargetDefault()
    {
        return true;
    }

    @Override
    public AbstractMorph create()
    {
        return new BetterLightsMorph();
    }

    @Override
    public float getWidth(EntityLivingBase entityLivingBase)
    {
        return 0;
    }

    @Override
    public float getHeight(EntityLivingBase entityLivingBase)
    {
        return 0;
    }

    @Override
    public void fromNBT(NBTTagCompound tag)
    {
        super.fromNBT(tag);

        this.nbt = tag.copy();

        this.values.fromNBT(tag);

        if (tag.hasKey("Animation"))
        {
            this.animation.fromNBT(tag.getCompoundTag("Animation"));
        }

        if (tag.hasKey("EnableAlways")) {
            this.enableAlways = tag.getBoolean("EnableAlways");
        }
    }

    /**
     * Setting this method to optional guarantees, that saved NBT will not be lost when BetterLights mod is not loaded.
     */
    @Override
    public void toNBT(NBTTagCompound tag)
    {
        super.toNBT(tag);

        if (BetterLightsHelper.isBetterLightsLoaded())
        {
            this.values.toNBT(tag);

            NBTTagCompound animation = this.animation.toNBT();

            if (!animation.hasNoTags())
            {
                tag.setTag("Animation", animation);
            }

            /*
             * Minecraft has a mechanism that prevents new ItemStack instances when the NBT is equal
             * Adding a random UUID will force a new ItemStack instance.
             */
            tag.setString("UUID", UUID.randomUUID().toString());
        }
        else if (this.nbt != null)
        {
            /* store the nbt tags again that were read in the beginning to avoid data loss */
            for (String key : this.nbt.getKeySet())
            {
                tag.setTag(key, this.nbt.getTag(key));
            }
        }

        if (this.enableAlways) tag.setBoolean("EnableAlways", this.enableAlways);
    }

    @Override
    public boolean canMerge(AbstractMorph morph0)
    {
        if (morph0 instanceof BetterLightsMorph)
        {
            BetterLightsMorph morph = (BetterLightsMorph) morph0;

            this.mergeBasic(morph0);

            if (!morph.animation.ignored)
            {
                if (this.animation.isInProgress())
                {
                    BetterLightsProperties newLast = new BetterLightsProperties();

                    newLast.from(this);

                    this.animation.apply(newLast, 0);

                    this.animation.last = newLast;
                }
                else
                {
                    this.animation.last = new BetterLightsProperties();

                    this.animation.last.from(this);
                }

                this.animation.merge(this, morph);
                this.copy(morph);
                this.animation.progress = 0;
            }
            else
            {
                this.animation.ignored = true;
            }

            return true;
        }

        return super.canMerge(morph0);
    }

    @Override
    public Animation getAnimation() {
        return this.animation;
    }

    @Override
    public boolean canGenerate() {
        return this.animation.isInProgress();
    }

    @Override
    public AbstractMorph genCurrentMorph(float partialTicks) {
        BetterLightsMorph morph = (BetterLightsMorph) this.copy();

        morph.properties.from(this);
        morph.animation.last = new BetterLightsProperties();
        morph.animation.last.from(this);

        morph.animation.apply(morph.properties, partialTicks);

        morph.animation.duration = this.animation.progress;

        return morph;
    }

    @Override
    public void pause(AbstractMorph previous, int offset)
    {
        this.animation.pause(offset);

        if (previous instanceof BetterLightsMorph)
        {
            BetterLightsMorph morph = (BetterLightsMorph) previous;

            if (morph.animation.isInProgress())
            {
                BetterLightsProperties newLast = new BetterLightsProperties();

                newLast.from(morph);
                morph.animation.apply(newLast, 1);

                this.animation.last = newLast;
            }
            else
            {
                this.animation.last = new BetterLightsProperties();

                this.animation.last.from(morph);
            }
        }
    }

    @Override
    public boolean isPaused() {
        return this.animation.paused;
    }

    @Override
    public void reset()
    {
        super.reset();

        this.animation.reset();
    }

    @Override
    public boolean equals(Object object)
    {
        boolean result = super.equals(object);

        if (object instanceof BetterLightsMorph)
        {
            BetterLightsMorph morph = (BetterLightsMorph) object;

            result = result && this.values.equalsValues(morph.values);
            result = result && Objects.equals(morph.animation, this.animation);
            result = result && this.enableAlways == morph.enableAlways;

            return result;
        }

        return result;
    }

    @Override
    public void copy(AbstractMorph from)
    {
        super.copy(from);

        if (from instanceof BetterLightsMorph)
        {
            BetterLightsMorph morph = (BetterLightsMorph) from;

            if (morph.nbt != null) this.nbt = morph.nbt.copy();
            this.values.copyValues(morph.values);
            this.animation.copy(morph.animation);
            this.animation.reset();
            this.enableAlways = morph.enableAlways;
        }
    }

    public static class BetterLightsAnimation extends Animation
    {
        public BetterLightsProperties last;

        public void merge(BetterLightsMorph last, BetterLightsMorph next)
        {
            this.merge(next.animation);

            if (this.last == null)
            {
                this.last = new BetterLightsProperties();
            }

            this.last.from(last);
        }

        public void apply(BetterLightsProperties properties, float partialTicks)
        {
            if (this.last == null)
            {
                return;
            }

            float factor = this.getFactor(partialTicks);

            properties.values.interpolateFrom(this.interp, this.last.values, factor);
        }
    }

    public static class BetterLightsProperties
    {
        /**
         * The value tree of the BetterLights Lightcaster.
         * Useful to generate other things with better handling of paths and nesting
         */
        public final static Value valueTree = new Value("");
        /**
         * Generate the values once with reflection and store them here
         * Nested values will be stored directly here, the tree is flattened.
         */
        private final static ValueSerializer TEMPLATE = new ValueSerializer();
        /**
         * Functional programming, wohooo :3, here we store functions to apply a value to the lightcaster of the BetterLightsMorph
         * This is especially needed as some values may need some conversion or special handling
         * like rotating the direction vector with the morph's rotation.
         * The Consumer uses {@link #properties} and the stored value path in the Consumer to apply the values
         * to the lightcaster.
         */
        private final static List<Consumer<BetterLightsMorph>> APPLY = new ArrayList<>();
        private final ValueSerializer values = new ValueSerializer();

        static
        {
            try
            {
                Class.forName("dz.betterlights.lighting.lightcasters.LightCaster");
                generate();
            }
            catch (Exception e) { }
        }

        @Optional.Method(modid = BetterLightsConstants.ID)
        private static void generate()
        {
            StaticLightCaster lightCaster = new StaticLightCaster();
            lightCaster.direction(0, 0, 1F);
            lightCaster.color(1F, 0.905F, 0.584F);

            generateValuesTemplate(LightCaster.class, null, valueTree, lightCaster);
        }

        /**
         * This generates the values in {@link #TEMPLATE} based on the LightCaster class.
         * Nested config properties will still be put into the {@link #TEMPLATE} directly,
         * i.e. the tree of config values will be flattened.
         * @param clazz the class to generate the values off.
         * @param parentField for nested config properties we need to build a tree of fields.
         * @param parent with this we generate new value paths for values that are nested in the lightcaster in sub configs.
         * @param defaultLightCaster contains the defautlValues.
         */
        @Optional.Method(modid = BetterLightsConstants.ID)
        private static void generateValuesTemplate(Class<?> clazz, @Nullable FieldHierarchy parentField, Value parent, LightCaster defaultLightCaster)
        {
            /* LightConfig interface has boolean methods for enabled that need to be called */
            if (ILightConfig.class.isAssignableFrom(clazz))
            {
                boolean defaultValue;
                if (parentField == null)
                {
                    defaultValue = defaultLightCaster.isEnabled();
                }
                else
                {
                    Object value = parentField.getValue(defaultLightCaster);
                    if (value == null) return;
                    defaultValue = ((ILightConfig<?>) value).isEnabled();
                }

                ValueBoolean vb = new ValueBoolean("Enabled", defaultValue);
                parent.addSubValue(vb);

                TEMPLATE.registerValue(vb).serializeNBT(vb.getPath(), true);

                APPLY.add((morph) ->
                {
                    LightCaster lightCaster = morph.getLightcaster();

                    morph.properties.values.getValue(vb.getPath()).ifPresent(genericBaseValue ->
                    {
                        Boolean bool = (Boolean) genericBaseValue.get();
                        if (parentField == null)
                        {
                            lightCaster.setEnabled(bool);
                        }
                        else
                        {
                            Object value = parentField.getValue(lightCaster);
                            if (value == null) return;
                            ((ILightConfig<?>) value).setEnabled(bool);
                        }
                    });
                });
            }

            for (Field field : clazz.getDeclaredFields())
            {
                FieldHierarchy fieldSetter = new FieldHierarchy(field);
                if (parentField != null) fieldSetter.parent = parentField;

                field.setAccessible(true);
                ConfigProperty configProperty = field.getAnnotation(ConfigProperty.class);

                /* position is special as it will be controlled by the rendered position and not by the morph itself */
                if(configProperty == null || configProperty.name().equals("Position")) continue;

                String name = configProperty.name();

                if (field.getType().equals(float.class))
                {
                    float defaultValue = 0F;

                    if (configProperty.defaultValue().isEmpty())
                    {
                        Object value = fieldSetter.getValue(defaultLightCaster);
                        defaultValue = value == null ? 0 : (float) value;
                    }
                    else
                    {
                        try
                        {
                            defaultValue = Float.parseFloat(configProperty.defaultValue());
                        }
                        catch (NumberFormatException e)
                        {
                            e.printStackTrace();
                        }
                    }

                    ValueFloat vf = new ValueFloat(name, defaultValue, configProperty.min(), configProperty.max());
                    parent.addSubValue(vf);
                    registerBasicValue(vf, fieldSetter);
                }
                else if (field.getType().equals(int.class))
                {
                    int defaultValue = 0;

                    if (configProperty.defaultValue().isEmpty())
                    {
                        Object value = fieldSetter.getValue(defaultLightCaster);
                        defaultValue = value == null ? 0 : (int) value;
                    }
                    else
                    {
                        try
                        {
                            defaultValue = Integer.parseInt(configProperty.defaultValue());
                        }
                        catch (NumberFormatException e)
                        {
                            e.printStackTrace();
                        }
                    }

                    ValueInt vf = new ValueInt(name, defaultValue, (int) configProperty.min(), (int) configProperty.max());
                    parent.addSubValue(vf);
                    registerBasicValue(vf, fieldSetter);
                }
                else if (field.getType().equals(com.jme3.math.Vector3f.class))
                {
                    Object value = fieldSetter.getValue(defaultLightCaster);
                    com.jme3.math.Vector3f defaultValue = value == null ? new com.jme3.math.Vector3f() : (com.jme3.math.Vector3f) value;

                    /* colors are usually stored as int in BB */
                    if (configProperty.type().equals(COLOR_PICKER))
                    {
                        ValueColor vc = new ValueColor(name, new Color(defaultValue.x, defaultValue.y, defaultValue.z));
                        parent.addSubValue(vc);
                        TEMPLATE.registerValue(vc).serializeNBT(vc.getPath(), true);

                        APPLY.add((morph) ->
                        {
                            LightCaster lightCaster = morph.getLightcaster();

                            morph.properties.values.getValue(vc.getPath()).ifPresent(genericBaseValue -> {
                                Color c = (Color) genericBaseValue.get();
                                fieldSetter.setField(lightCaster, new com.jme3.math.Vector3f(c.r, c.g, c.b));
                            });
                        });
                    }
                    else
                    {
                        ValueFloat vfx = new ValueFloat(name + "X", defaultValue.x);
                        ValueFloat vfy = new ValueFloat(name + "Y", defaultValue.y);
                        ValueFloat vfz = new ValueFloat(name + "Z", defaultValue.z);

                        parent.addSubValue(vfx);
                        parent.addSubValue(vfy);
                        parent.addSubValue(vfz);

                        TEMPLATE.registerValue(vfx).serializeNBT(vfx.getPath(), true);
                        TEMPLATE.registerValue(vfy).serializeNBT(vfy.getPath(), true);
                        TEMPLATE.registerValue(vfz).serializeNBT(vfz.getPath(), true);

                        APPLY.add((morph) ->
                        {
                            LightCaster lightCaster = morph.getLightcaster();

                            java.util.Optional<GenericBaseValue<?>> vx = morph.properties.values.getValue(vfx.getPath());
                            java.util.Optional<GenericBaseValue<?>> vy = morph.properties.values.getValue(vfy.getPath());
                            java.util.Optional<GenericBaseValue<?>> vz = morph.properties.values.getValue(vfz.getPath());

                            if (!vx.isPresent() || !vy.isPresent() || !vz.isPresent()) return;

                            Vector3f vec = new Vector3f(
                                    (Float) vx.get().get(),
                                    (Float) vy.get().get(),
                                    (Float) vz.get().get());

                            if (name.equals("Direction"))
                            {
                                morph.rotation.transform(vec);
                            }

                            fieldSetter.setField(lightCaster, new com.jme3.math.Vector3f(vec.x, vec.y, vec.z));
                        });
                    }
                }
                else if (field.getType().equals(boolean.class))
                {
                    boolean defaultValue;

                    if (configProperty.defaultValue().isEmpty())
                    {
                        Object value = fieldSetter.getValue(defaultLightCaster);
                        defaultValue = value != null && (boolean) value;
                    }
                    else
                    {
                        defaultValue = Boolean.parseBoolean(configProperty.defaultValue());
                    }

                    ValueBoolean vb = new ValueBoolean(name, defaultValue);
                    parent.addSubValue(vb);
                    registerBasicValue(vb, fieldSetter);
                }
                else if (ILightConfig.class.isAssignableFrom(field.getType()))
                {
                    Value subCategory = new Value(configProperty.name());
                    parent.addSubValue(subCategory);
                    generateValuesTemplate(field.getType(), fieldSetter, subCategory, defaultLightCaster);
                }
            }
        }

        private static void registerBasicValue(GenericBaseValue<?> value, FieldHierarchy fieldSetter)
        {
            TEMPLATE.registerValue(value)
                    .serializeNBT(value.getPath(), true);

            APPLY.add((morph) ->
            {
                LightCaster lightCaster = morph.getLightcaster();

                morph.properties.values.getValue(value.getPath())
                        .ifPresent(genericBaseValue -> fieldSetter.setField(lightCaster, genericBaseValue.get()));
            });
        }

        public BetterLightsProperties()
        {
            /* deep copy of the template */
            this.values.copy(TEMPLATE);
        }

        public void from(BetterLightsMorph from)
        {
            this.values.copyValues(from.values);
        }
    }

    /**
     * We need this because we only have direct access to the LightCaster instance,
     * this way we can get the instances of fields nested inside the root LightCaster instance.
     */
    private static class FieldHierarchy
    {
        private Field field;
        private FieldHierarchy parent;

        public FieldHierarchy(Field field)
        {
            this.field = field;
            this.field.setAccessible(true);
        }

        public FieldHierarchy(FieldHierarchy parent, Field field)
        {
            this.field = field;
            this.field.setAccessible(true);
            this.parent = parent;
        }

        private Object getFieldHolder(Object root)
        {
            if (this.parent != null)
            {
                return this.parent.getValue(root);
            }
            else
            {
                return root;
            }
        }

        private Object getValue(Object root)
        {
            try
            {
                if (this.parent != null)
                {
                    return this.field.get(this.parent.getValue(root));
                }

                return this.field.get(root);
            }
            catch (IllegalAccessException e)
            {
                e.printStackTrace();
            }

            return null;
        }

        public void setField(Object root, Object value)
        {
            Object fieldHolder;
            if ((fieldHolder = this.getFieldHolder(root)) != null)
            {
                try
                {
                    this.field.set(fieldHolder, value);
                }
                catch (IllegalAccessException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }
}
