package dev.lovepaw.client.screen;

import dev.lovepaw.client.PetManager;
import dev.lovepaw.config.ClientConfig;
import dev.lovepaw.config.PetOverrides;
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
 * How big the player's own pet is.
 *
 * <p>There is one slider because there is one thing here worth deciding. How a
 * pet moves comes from the game — a cat, a wolf, an allay — and no longer from
 * a screenful of numbers nobody could guess good values for.
 *
 * <p>The world keeps running behind this screen on purpose: the slider changes
 * the live pet on the next tick, so you watch it take effect rather than
 * guessing and reopening.
 */
public class PetSettingsScreen extends Screen {
    private static final int WIDTH = 220;

    private final Screen parent;

    public PetSettingsScreen(Screen parent) {
        super(Component.translatable("lovepaw.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        PetRenderSettings render = currentRender();

        int left = width / 2 - WIDTH / 2;
        int top = 46;

        addRenderableWidget(new Setting(left, top, WIDTH,
                "lovepaw.settings.scale", 0.25f, 3f, 0.05f,
                render.scale(), PetSettingsScreen::multiplier,
                value -> update(overrides -> overrides.withScale(value))));

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
