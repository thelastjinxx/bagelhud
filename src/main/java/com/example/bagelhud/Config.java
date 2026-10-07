package com.example.bagelhud;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Saves the GUI settings to config/bagelhud.properties. */
public class Config {
    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("bagelhud.properties");
    }

    static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f)) {
            Properties p = new Properties();
            p.load(r);
            FarmRateTracker.hudX = Integer.parseInt(p.getProperty("hudX", "6"));
            FarmRateTracker.hudY = Integer.parseInt(p.getProperty("hudY", "6"));
            FarmRateTracker.hudScale = Float.parseFloat(p.getProperty("hudScale", "1.0"));
            FarmRateTracker.showHud = Boolean.parseBoolean(p.getProperty("showHud", "true"));
            FarmRateTracker.useManualPrice = Boolean.parseBoolean(p.getProperty("useManualPrice", "false"));
            FarmRateTracker.PRICE_EACH = Double.parseDouble(p.getProperty("price", "77.1"));
        } catch (IOException | NumberFormatException ignored) {
        }
    }

    static void save() {
        Properties p = new Properties();
        p.setProperty("hudX", String.valueOf(FarmRateTracker.hudX));
        p.setProperty("hudY", String.valueOf(FarmRateTracker.hudY));
        p.setProperty("hudScale", String.valueOf(FarmRateTracker.hudScale));
        p.setProperty("showHud", String.valueOf(FarmRateTracker.showHud));
        p.setProperty("useManualPrice", String.valueOf(FarmRateTracker.useManualPrice));
        p.setProperty("price", String.valueOf(FarmRateTracker.PRICE_EACH));
        try (Writer w = Files.newBufferedWriter(file())) {
            p.store(w, "BagelHUD settings");
        } catch (IOException ignored) {
        }
    }
}
