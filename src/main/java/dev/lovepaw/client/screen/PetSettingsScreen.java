package dev.lovepaw.client.screen;

import dev.lovepaw.client.PetManager;
import dev.lovepaw.config.ClientConfig;
import dev.lovepaw.config.PetOverrides;
import dev.lovepaw.pet.PetBehaviourSettings;
import dev.lovepaw.pet.PetDefinition;
import dev.lovepaw.pet.PetRegistry;
import dev.lovepaw.pet.PetRenderSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Tweaks for the player's own pet, applied over whatever its pack asks for.
 *
 * <p>The world keeps running behind this screen on purpose: a slider changes
 * the live pet on the next tick, so you can watch it take effect rather than
 * guessing and reopening.
 */
public class PetSettingsScreen extends Screen {
    private static final int WIDTH = 220;
    private static final int ROW = 22;

    private final Screen parent;

    public PetSettingsScreen(Screen parent) {
        super(Component.translatable("lovepaw.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        PetBehaviourSettings behaviour = currentBehaviour();
        PetRenderSettings render = currentRender();

        int left = width / 2 - WIDTH / 2;
        int top = 46;

        addRenderableWidget(new Setting(left, top, WIDTH,
                "lovepaw.settings.anchor_radius", 1f, 24f, 0.5f,
                behaviour.anchorRadius(), PetSettingsScreen::blocks,
                value -> update(overrides -> overrides.withAnchorRadius(value))));

        addRenderableWidget(new Setting(left, top + ROW, WIDTH,
                "lovepaw.settings.prediction", 0f, 3f, 0.1f,
                behaviour.predictionSeconds(), PetSettingsScreen::seconds,
                value -> update(overrides -> overrides.withPredictionSeconds(value))));

        addRenderableWidget(new Setting(left, top + ROW * 2, WIDTH,
                "lovepaw.settings.wander_radius", 1.5f, 16f, 0.5f,
                behaviour.wanderRadius(), PetSettingsScreen::blocks,
                value -> update(overrides -> overrides.withWanderRadius(value))));

        addRenderableWidget(new Setting(left, top + ROW * 3, WIDTH,
                "lovepaw.settings.sit_chance", 0f, 1f, 0.05f,
                behaviour.sitChance(), PetSettingsScreen::percent,
                value -> update(overrides -> overrides.withSitChance(value))));

        addRenderableWidget(new Setting(left, top + ROW * 4, WIDTH,
                "lovepaw.settings.scale", 0.25f, 3f, 0.05f,
                render.scale(), PetSettingsScreen::multiplier,
                value -> update(overrides -> overrides.withScale(value))));

        addRenderableWidget(Button.builder(
                        Component.translatable(behaviour.wander()
                                ? "lovepaw.settings.wander_on"
                                : "lovepaw.settings.wander_off"),
                        button -> {
                            update(overrides -> overrides.withWander(!currentBehaviour().wander()));
                            rebuildWidgets();
                        })
                .bounds(left, top + ROW * 5 + 4, WIDTH, 20)
                .build());

        int bottom = height - 28;
        addRenderableWidget(Button.builder(Component.translatable("lovepaw.settings.reset"), button -> {
                    ClientConfig.setOverrides(PetOverrides.NONE);
                    ClientConfig.save();
                    rebuildWidgets();
                })
                .bounds(left, bottom, WIDTH - 84, 20)
                .build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(left + WIDTH - 80, bottom, 80, 20)
                .build());
    }

    private PetBehaviourSettings currentBehaviour() {
        PetDefinition pet = selectedPet();
        PetBehaviourSettings base = pet != null ? pet.behaviour() : PetBehaviourSettings.DEFAULT;
        return ClientConfig.overrides().applyTo(base);
    }

    private PetRenderSettings currentRender() {
        PetDefinition pet = selectedPet();
        PetRenderSettings base = pet != null ? pet.render() : PetRenderSettings.DEFAULT;
        return ClientConfig.overrides().applyTo(base);
    }

    private PetDefinition selectedPet() {
        ResourceLocation id = PetManager.get().localSelection();
        return id == null ? null : PetRegistry.get().get(id).orElse(null);
    }

    private void update(java.util.function.UnaryOperator<PetOverrides> change) {
        ClientConfig.setOverrides(change.apply(ClientConfig.overrides()));
    }

    @Override
    public void onClose() {
        ClientConfig.save();
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("lovepaw.settings.hint"),
                width / 2, 31, 0x909090);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String blocks(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String seconds(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String percent(float value) {
        return Math.round(value * 100) + "%";
    }

    private static String multiplier(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @FunctionalInterface
    private interface Formatter {
        String format(float value);
    }

    @FunctionalInterface
    private interface Sink {
        void accept(float value);
    }

    private static final class Setting extends AbstractSliderButton {
        private final String translationKey;
        private final float min;
        private final float max;
        private final float step;
        private final Formatter formatter;
        private final Sink sink;

        Setting(int x, int y, int width, String translationKey,
                float min, float max, float step, float current,
                Formatter formatter, Sink sink) {
            super(x, y, width, 20, Component.empty(), clamp01((current - min) / (max - min)));
            this.translationKey = translationKey;
            this.min = min;
            this.max = max;
            this.step = step;
            this.formatter = formatter;
            this.sink = sink;
            updateMessage();
        }

        private static double clamp01(double value) {
            return Math.max(0, Math.min(1, value));
        }

        private float snapped() {
            float raw = (float) (min + value * (max - min));
            return Math.round(raw / step) * step;
        }

        @Override
        protected void updateMessage() {
            if (translationKey == null) {
                return;
            }
            setMessage(Component.translatable(translationKey, formatter.format(snapped())));
        }

        @Override
        protected void applyValue() {
            if (sink == null) {
                return;
            }
            sink.accept(snapped());
        }
    }
}
