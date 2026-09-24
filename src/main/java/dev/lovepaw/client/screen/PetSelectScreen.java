package dev.lovepaw.client.screen;

import dev.lovepaw.client.PetManager;
import dev.lovepaw.config.ClientConfig;
import dev.lovepaw.pet.PetDefinition;
import dev.lovepaw.pet.PetRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The pet picker: every installed pet, one page at a time, with the current one
 * marked. Deliberately plain — it is a list of names, and the interesting part
 * (what a pet looks like) is standing next to the player already.
 */
public class PetSelectScreen extends Screen {
    private static final int ROWS = 8;
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING = 2;

    private List<PetDefinition> pets = List.of();
    private int page;

    public PetSelectScreen() {
        super(Component.translatable("lovepaw.screen.title"));
    }

    @Override
    protected void init() {
        pets = PetRegistry.get().sorted();

        int pageCount = Math.max(1, (pets.size() + ROWS - 1) / ROWS);
        page = Math.min(page, pageCount - 1);

        int left = width / 2 - BUTTON_WIDTH / 2;
        int top = 40;

        ResourceLocation selected = PetManager.get().localSelection();

        addRenderableWidget(Button.builder(
                        label(Component.translatable("lovepaw.screen.none"), selected == null),
                        button -> choose(null))
                .bounds(left, top, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        int listTop = top + BUTTON_HEIGHT + 6;
        int first = page * ROWS;
        for (int i = 0; i < ROWS && first + i < pets.size(); i++) {
            PetDefinition pet = pets.get(first + i);
            boolean active = pet.id().equals(selected);
            addRenderableWidget(Button.builder(
                            label(Component.literal(pet.displayName()), active),
                            button -> choose(pet.id()))
                    .bounds(left, listTop + i * (BUTTON_HEIGHT + SPACING), BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(tooltip(pet)))
                    .build());
        }

        int bottom = height - 28;
        if (pets.size() > ROWS) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> turnPage(-1))
                    .bounds(left, bottom - 24, 40, BUTTON_HEIGHT)
                    .build());
            addRenderableWidget(Button.builder(Component.literal(">"), button -> turnPage(1))
                    .bounds(left + BUTTON_WIDTH - 40, bottom - 24, 40, BUTTON_HEIGHT)
                    .build());
        }

        addRenderableWidget(Button.builder(Component.translatable("lovepaw.screen.settings"),
                        button -> minecraft.setScreen(new PetSettingsScreen(this)))
                .bounds(left + 44, bottom - 24, BUTTON_WIDTH - 88, BUTTON_HEIGHT)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable(ClientConfig.showOtherPlayersPets()
                                ? "lovepaw.screen.others_shown"
                                : "lovepaw.screen.others_hidden"),
                        button -> {
                            ClientConfig.setShowOtherPlayersPets(!ClientConfig.showOtherPlayersPets());
                            rebuildWidgets();
                        })
                .bounds(left, bottom, BUTTON_WIDTH - 84, BUTTON_HEIGHT)
                .build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(left + BUTTON_WIDTH - 80, bottom, 80, BUTTON_HEIGHT)
                .build());
    }

    private static Component label(Component name, boolean selected) {
        return selected ? Component.literal("> ").append(name).append(" <") : name;
    }

    private static Component tooltip(PetDefinition pet) {
        Component author = pet.author().isBlank()
                ? Component.empty()
                : Component.literal("\n").append(Component.translatable("lovepaw.screen.author", pet.author()));
        return Component.literal(pet.id().toString()).append(author);
    }

    private void turnPage(int direction) {
        int pageCount = Math.max(1, (pets.size() + ROWS - 1) / ROWS);
        page = Math.floorMod(page + direction, pageCount);
        rebuildWidgets();
    }

    private void choose(ResourceLocation petId) {
        PetManager.get().setLocalSelection(petId);
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);

        if (pets.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("lovepaw.screen.empty"),
                    width / 2, height / 2, 0xA0A0A0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
