package mchorse.blockbuster_pack.client.gui;

import dz.betterlights.lighting.lightcasters.LightCaster;
import dz.betterlights.lighting.lightcasters.features.ILightConfig;
import dz.betterlights.utils.BetterLightsConstants;
import dz.betterlights.utils.ConfigProperty;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster_pack.morphs.BetterLightsMorph;
import mchorse.blockbuster_pack.morphs.BetterLightsMorphTemplate;
import mchorse.blockbuster_pack.morphs.LightMorph;
import mchorse.mclib.client.gui.framework.elements.GuiCollapseSection;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.GuiScrollElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiColorElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.modals.GuiMessageModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiModal;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiLabel;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.config.values.*;
import mchorse.mclib.utils.BetterLightsHelper;
import mchorse.mclib.utils.Color;
import mchorse.mclib.utils.Direction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiAnimation;
import mchorse.metamorph.client.gui.editor.GuiMorphPanel;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.vecmath.Vector3f;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static dz.betterlights.utils.ConfigProperty.EnumPropertyType.COLOR_PICKER;

@SideOnly(Side.CLIENT)
public class GuiBetterLightsMorph extends GuiAbstractMorph<BetterLightsMorph>
{
    public GuiBetterLightsMorph(Minecraft mc)
    {
        super(mc);

        this.defaultPanel = new GuiBetterLightsMorphPanel(mc, this);
        this.registerPanel(this.defaultPanel, IKey.lang("blockbuster.gui.betterlights_morph.name"), Icons.GEAR);
    }

    @Override
    public boolean canEdit(AbstractMorph morph)
    {
        return morph instanceof BetterLightsMorph;
    }

    public static class GuiBetterLightsMorphPanel extends GuiMorphPanel<BetterLightsMorph, GuiBetterLightsMorph>
    {
        private GuiAnimation animation;
        /**
         * List of consumers used to apply the morph's values to the UI elements.
         * The lambda expressions will contain the reference to the respective UI element, since they
         * are dynamically generated.
         */
        private final List<Consumer<BetterLightsMorph>> apply = new ArrayList<>();
        private GuiToggleElement enableAlways;

        public GuiBetterLightsMorphPanel(Minecraft mc, GuiBetterLightsMorph editor)
        {
            super(mc, editor);

            this.animation = new GuiAnimation(mc, true);
            this.animation.flex().column(0).padding(0);
            this.animation.ignored.removeFromParent();
            this.animation.interpolations.removeFromParent();
            this.enableAlways = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.betterlights_morph.enable_always"),
                    (b) -> this.morph.setEnableAlways(b.isToggled()));

            GuiElement left = new GuiElement(mc);
            left.flex().relative(this)
                    .x(0F)
                    .w(200)
                    .h(1F)
                    .column(0)
                    .vertical().stretch().padding(10);

            GuiElement sponsors = Elements.label(IKey.lang("blockbuster.gui.betterlights_morph.sponsored_title"));
            sponsors.tooltip(IKey.comp(IKey.lang("blockbuster.gui.betterlights_morph.sponsored_tooltip"),
                    IKey.str("\n\nMarlon\nHerr Bergmann\nAreon Pictures\nJunder\nPhoenixMedia\nLouis Angerer\nGewenzsko\nKatzen48")));

            left.add(sponsors);

            this.add(left);

            if (BetterLightsHelper.isBetterLightsLoaded())
            {
                GuiScrollElement scroll = new GuiScrollElement(mc);

                scroll.flex().relative(this)
                        .x(1F)
                        .w(200)
                        .h(1F)
                        .anchorX(1F)
                        .column(5)
                        .vertical().stretch().scroll().padding(10);

                scroll.add(this.enableAlways, this.animation);

                this.generateUITree(scroll, BetterLightsMorph.BetterLightsProperties.valueTree);
                this.add(scroll, this.animation.interpolations);
            }
            else
            {
                GuiModal modal = new GuiBetterLightsMessage(this.mc, IKey.lang("blockbuster.gui.betterlights_morph.not_installed_message"));
                modal.flex().relative(this)
                        .x(0.5F)
                        .y(0.5F)
                        .anchorX(0.5F)
                        .anchorY(0.5F)
                        .w(128)
                        .h(128);
                this.add(modal);
            }
        }

        private void generateUITree(GuiElement parentElement, Value value)
        {
            for (Value subValue : value.getSubValues())
            {
                String path = subValue.getPath();
                String langPath = "blockbuster.gui.betterlights_morph.options." + path.toLowerCase().replace(" ", "_");

                if (subValue instanceof ValueBoolean)
                {
                    this.addToggleElement(parentElement, path, langPath);
                }
                else if (subValue instanceof GenericNumberValue)
                {
                    this.addTrackpad(parentElement, (GenericNumberValue<?>) subValue, path, langPath);
                }
                else if (subValue instanceof ValueColor)
                {
                    this.addColor(parentElement, path, langPath);
                }

                if (subValue.getSubValues().size() != 0)
                {
                    GuiCollapseSection section = new GuiCollapseSection(this.mc, IKey.lang(langPath + ".title"));
                    section.setCollapsed(true);
                    parentElement.add(section);
                    this.generateUITree(section, subValue);
                }
            }
        }

        /**
         * @param parentElement
         * @param path the path of the value this element edits
         */
        private void addColor(GuiElement parentElement, final String path, final String langPath)
        {
            GuiColorElement color = new GuiColorElement(this.mc,
                    (value) -> this.morph.getValueManager().getValue(path).ifPresent(v -> v.setValue(new Color(value))))
                    .direction(Direction.TOP);
            color.picker.editAlpha();
            color.tooltip(IKey.lang(langPath));

            parentElement.add(color);

            this.apply.add((morph) ->
                    morph.getValueManager().getValue(path)
                            .ifPresent(v -> color.picker.setColor(((Color) v.get()).getRGBAColor())));
        }

        private void addTrackpad(GuiElement parentElement, GenericNumberValue<?> numberValue, final String path, final String langPath)
        {
            GuiTrackpadElement trackpad = new GuiTrackpadElement(this.mc,
                    (value) -> this.morph.getValueManager().getValue(path).ifPresent((v) -> v.setValue(value.floatValue())));
            trackpad.tooltip(IKey.lang(langPath + "_tooltip"));
            trackpad.limit(numberValue.getMin().doubleValue(), numberValue.getMax().doubleValue());

            boolean isInteger = numberValue.isInteger();
            if (isInteger) trackpad.integer();

            parentElement.add(trackpad);

            this.apply.add((morph) ->
                    morph.getValueManager().getValue(path)
                            .ifPresent(genericBaseValue ->
                            {
                                if (!(genericBaseValue instanceof GenericNumberValue)) return;
                                GenericNumberValue<?> numberValue0 = (GenericNumberValue<?>) genericBaseValue;
                                trackpad.setValue(isInteger ? numberValue0.get().longValue() : numberValue0.get().doubleValue());
                            }));
        }

        /**
         *
         * @param parentElement
         * @param path the path of the value this element edits
         */
        private void addToggleElement(GuiElement parentElement, final String path, final String langPath)
        {
            GuiToggleElement toggleElement = new GuiToggleElement(this.mc, IKey.lang(langPath),
                    v -> this.morph.getValueManager().getValue(path)
                            .ifPresent((value) -> value.setValue(v.isToggled())));

            parentElement.add(toggleElement);

            this.apply.add((morph) ->
                morph.getValueManager().getValue(path)
                        .ifPresent(genericBaseValue -> toggleElement.toggled((Boolean) genericBaseValue.get())));
        }

        @Override
        public void fillData(BetterLightsMorph morph)
        {
            super.fillData(morph);

            this.animation.fill(morph.getAnimation());
            this.apply.forEach(consumer -> consumer.accept(morph));
            this.enableAlways.toggled(morph.isEnableAlways());
        }
    }

    public static class GuiBetterLightsMessage extends GuiModal
    {
        public GuiBetterLightsMessage(Minecraft mc, IKey label) {
            super(mc, label);

            GuiButtonElement button = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.betterlights_morph.mod_page_button"),
                    (b) -> GuiUtils.openWebLink(BetterLightsConstants.PATREON_URL));

            this.bar.add(button);
        }
    }
}
