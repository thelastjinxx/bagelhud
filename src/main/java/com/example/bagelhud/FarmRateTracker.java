package com.example.bagelhud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Counts how many of a target item you gain in your inventory and shows
 * per-minute / per-hour rates plus estimated money per hour.
 * Chest mode: open your farm's output chest each visit; whatever it gained since last time counts.
 * Only open the output chest (any other chest would be counted too).
 * Target: the item in your hand when you press J. Price: read from the item's lore if the server shows one.
 * Press the keybind to start/stop a session, Shift+key to reset.
 */
public class FarmRateTracker implements ClientModInitializer {
    // --- Edit these ---
    static Item TARGET = Items.BAMBOO_BLOCK;   // auto-set to the item in your hand when you start a session
    static double PRICE_EACH = 77.1;           // fallback if no price is found on the item
    // Matches the BagelSMP hover line "Price: <number>" (also handles $, commas and k/m/b)
    static final Pattern PRICE_RE = Pattern.compile(
            "(?i)(price)\\D{0,20}?\\$?\\s*([\\d,]+(?:\\.\\d+)?)\\s*([kmb])?");
    static boolean priceFound = false;
    // ------------------

    // GUI-controlled settings (saved by Config)
    static int hudX = 6, hudY = 6;
    static float hudScale = 1.0f;
    static boolean showHud = true;
    static boolean useManualPrice = false;

    static boolean running = false;
    private static long startMs = 0;
    private static long elapsedBeforeMs = 0;
    static long gained = 0;
    private static int lastCount = -1;
    private static int lastChestCount = -1; // chest count the last time you saw it

    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("bagelhud", "main"));
    private static KeyBinding toggleKey;
    private static KeyBinding guiKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.bagelhud.toggle", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J, CATEGORY));

        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.bagelhud.gui", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K, CATEGORY));
        Config.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (guiKey.wasPressed()) {
                client.setScreen(new TrackerScreen());
            }
            while (toggleKey.wasPressed()) {
                boolean shift = InputUtil.isKeyPressed(client.getWindow(),
                        GLFW.GLFW_KEY_LEFT_SHIFT);
                if (shift) reset();
                else toggle();
            }
            if (!running || client.player == null) return;
            if (client.player.age % 40 == 0) refreshPrice(client);

            // Chest mode: while a chest/barrel is open, measure what the farm added since you last saw it
            if (client.currentScreen instanceof GenericContainerScreen gs) {
                int chest = 0;
                for (Slot slot : gs.getScreenHandler().slots) {
                    if (slot.inventory instanceof PlayerInventory) continue;
                    if (slot.getStack().isOf(TARGET)) chest += slot.getStack().getCount();
                }
                if (lastChestCount >= 0 && chest > lastChestCount) gained += chest - lastChestCount;
                lastChestCount = chest; // updated every tick, so taking items out is never counted as farm output
                lastCount = -1;         // skip inventory counting so items you take out aren't counted twice
                return;
            }

            int count = client.player.getInventory().count(TARGET);
            // Only count increases so dropping/storing items doesn't lower the total
            if (lastCount >= 0 && count > lastCount) gained += count - lastCount;
            lastCount = count;
        });

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.of("bagelhud", "farm_rates"),
                (ctx, tickCounter) -> {
                    if (!showHud) return;
                    MinecraftClient mc = MinecraftClient.getInstance();
                    double mins = elapsedMs() / 60000.0;
                    double perMin = mins > 0.05 ? gained / mins : 0;
                    String[] lines = {
                            (running ? "Farm: RUNNING" : "Farm: paused"),
                            String.format("Gained: %d", gained),
                            String.format("%.1f /min  |  %.0f /hr", perMin, perMin * 60),
                            String.format("%s @ $%,.2f ea%s", TARGET.getName().getString(), PRICE_EACH, (priceFound && !useManualPrice) ? "" : " (manual)"),
                            String.format("$%,.0f /hr", perMin * 60 * PRICE_EACH),
                            String.format("Time: %d:%02d", (int) mins, (int) ((elapsedMs() / 1000) % 60))
                    };
                    ctx.getMatrices().pushMatrix();
                    ctx.getMatrices().translate(hudX, hudY);
                    ctx.getMatrices().scale(hudScale, hudScale);
                    int y = 0;
                    for (String l : lines) {
                        ctx.drawTextWithShadow(mc.textRenderer, l, 0, y, 0xFFFFFFFF);
                        y += 10;
                    }
                    ctx.getMatrices().popMatrix();
                });
    }

    /** Looks through the item's lore for a price; returns -1 if none found. */
    private static double readPrice(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return -1;
        for (Text line : lore.lines()) {
            Matcher m = PRICE_RE.matcher(line.getString());
            if (m.find()) {
                double v = Double.parseDouble(m.group(2).replace(",", ""));
                String suf = m.group(3);
                if (suf != null) {
                    switch (suf.toLowerCase()) {
                        case "k" -> v *= 1_000;
                        case "m" -> v *= 1_000_000;
                        case "b" -> v *= 1_000_000_000;
                    }
                }
                return v;
            }
        }
        return -1;
    }

    private static void refreshPrice(MinecraftClient client) {
        if (client.player == null || useManualPrice) return;
        // check the held item, then the rest of the inventory, then an open chest
        var inv = client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(TARGET)) {
                double p = readPrice(st);
                if (p > 0) { PRICE_EACH = p; priceFound = true; return; }
            }
        }
        if (client.currentScreen instanceof GenericContainerScreen gs) {
            for (Slot slot : gs.getScreenHandler().slots) {
                if (slot.getStack().isOf(TARGET)) {
                    double p = readPrice(slot.getStack());
                    if (p > 0) { PRICE_EACH = p; priceFound = true; return; }
                }
            }
        }
    }

    static long elapsedMs() {
        return elapsedBeforeMs + (running ? System.currentTimeMillis() - startMs : 0);
    }

    static void toggle() {
        if (running) {
            elapsedBeforeMs = elapsedMs();
            running = false;
        } else {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && !mc.player.getMainHandStack().isEmpty()) {
                TARGET = mc.player.getMainHandStack().getItem();
                priceFound = false;
            }
            startMs = System.currentTimeMillis();
            lastCount = -1;
            lastChestCount = -1;
            running = true;
        }
    }

    static void reset() {
        running = false;
        elapsedBeforeMs = 0;
        gained = 0;
        lastCount = -1;
        lastChestCount = -1;
    }
}
