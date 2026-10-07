package com.example.bagelhud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Settings + controls screen. Open with K. */
public class TrackerScreen extends Screen {
    private TextFieldWidget priceField;
    private ButtonWidget startButton;
    private ButtonWidget showButton;
    private ButtonWidget manualButton;

    public TrackerScreen() {
        super(Text.literal("BagelHUD"));
    }

    @Override
    protected void init() {
        int w = 200;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 80;

        startButton = ButtonWidget.builder(startLabel(), b -> {
            FarmRateTracker.toggle();
            b.setMessage(startLabel());
        }).dimensions(x, y, 98, 20).build();
        addDrawableChild(startButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset session"), b -> FarmRateTracker.reset())
                .dimensions(x + 102, y, 98, 20).build());

        y += 26;
        showButton = ButtonWidget.builder(showLabel(), b -> {
            FarmRateTracker.showHud = !FarmRateTracker.showHud;
            b.setMessage(showLabel());
        }).dimensions(x, y, w, 20).build();
        addDrawableChild(showButton);

        y += 26;
        addDrawableChild(new SliderWidget(x, y, w, 20, Text.empty(), FarmRateTracker.hudX / (double) Math.max(1, this.width)) {
            { updateMessage(); }
            @Override protected void updateMessage() {
                setMessage(Text.literal("HUD X: " + (int) (value * width_())));
            }
            @Override protected void applyValue() {
                FarmRateTracker.hudX = (int) (value * width_());
            }
            private int width_() { return TrackerScreen.this.width; }
        });

        y += 22;
        addDrawableChild(new SliderWidget(x, y, w, 20, Text.empty(), FarmRateTracker.hudY / (double) Math.max(1, this.height)) {
            { updateMessage(); }
            @Override protected void updateMessage() {
                setMessage(Text.literal("HUD Y: " + (int) (value * height_())));
            }
            @Override protected void applyValue() {
                FarmRateTracker.hudY = (int) (value * height_());
            }
            private int height_() { return TrackerScreen.this.height; }
        });

        y += 22;
        addDrawableChild(new SliderWidget(x, y, w, 20, Text.empty(), (FarmRateTracker.hudScale - 0.5) / 2.0) {
            { updateMessage(); }
            @Override protected void updateMessage() {
                setMessage(Text.literal(String.format("HUD size: %.1fx", 0.5 + value * 2.0)));
            }
            @Override protected void applyValue() {
                FarmRateTracker.hudScale = (float) (0.5 + value * 2.0);
            }
        });

        y += 28;
        manualButton = ButtonWidget.builder(manualLabel(), b -> {
            FarmRateTracker.useManualPrice = !FarmRateTracker.useManualPrice;
            b.setMessage(manualLabel());
        }).dimensions(x, y, w, 20).build();
        addDrawableChild(manualButton);

        y += 24;
        priceField = new TextFieldWidget(this.textRenderer, x, y, w, 20, Text.literal("Price"));
        priceField.setText(String.valueOf(FarmRateTracker.PRICE_EACH));
        priceField.setChangedListener(t -> {
            try {
                double v = Double.parseDouble(t.replace(",", "").trim());
                if (v >= 0) FarmRateTracker.PRICE_EACH = v;
            } catch (NumberFormatException ignored) {
            }
        });
        addDrawableChild(priceField);

        y += 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(x, y, w, 20).build());
    }

    private Text startLabel() {
        return Text.literal(FarmRateTracker.running ? "Pause" : "Start");
    }

    private Text showLabel() {
        return Text.literal("HUD: " + (FarmRateTracker.showHud ? "ON" : "OFF"));
    }

    private Text manualLabel() {
        return Text.literal("Price: " + (FarmRateTracker.useManualPrice ? "manual (type below)" : "auto from item"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 100, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Target: " + FarmRateTracker.TARGET.getName().getString()
                        + "  (hold an item and press Start to change)"),
                this.width / 2, this.height / 2 - 92 + 190, 0xFFAAAAAA);
    }

    @Override
    public void close() {
        Config.save();
        MinecraftClient.getInstance().setScreen(null);
    }
}
