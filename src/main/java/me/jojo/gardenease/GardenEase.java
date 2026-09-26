package me.jojo.gardenease;

import me.jojo.gardenease.commands.FarmMacroCommand;
import me.jojo.gardenease.commands.RecordingManager;
import me.jojo.gardenease.config.ConfigManager;
import me.jojo.gardenease.macro.EmergencyHotkey;
import me.jojo.gardenease.macro.ProfileHotkey;
import me.jojo.gardenease.render.ResumeHighlightRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public class GardenEase implements ClientModInitializer {
    public static final String MOD_ID = "gardenease";
    public static final String MOD_NAME = "GardenEase";
    public static final String MOD_VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // --- SOUND REGISTRATION START ---
    public static final Identifier COOKIE_CLICK_ID = Identifier.fromNamespaceAndPath(MOD_ID, "cookie_click");
    public static final SoundEvent COOKIE_CLICK_EVENT = SoundEvent.createFixedRangeEvent(COOKIE_CLICK_ID, 16.0f);
    // --- SOUND REGISTRATION END ---

    public static KeyMapping EMERGENCY_STOP_KEY;
    public static KeyMapping QUICK_RESUME_KEY;
    public static KeyMapping START_PROFILE_KEY;

    @Override
    public void onInitializeClient() {
     /*   String uuid = net.minecraft.client.MinecraftClient.getInstance().getSession().getUuidOrNull().toString();

        if (!isAuthorized(uuid)) {
            LOGGER.error("************************************************");
            LOGGER.error("LICENSE NOT FOUND OR EXPIRED!");
            LOGGER.error("Your UUID: {}", uuid);
            LOGGER.error("Go to Discord to buy a LICENSE: https://discord.gg/eb8uCbVP2K");
            LOGGER.error("************************************************");
            return; // Stop initialization
        }*/

        LOGGER.info("Initializing {} v{}...", MOD_NAME, MOD_VERSION);

        // Register the sound so Minecraft recognizes "gardenease:cookie_click"
        Registry.register(BuiltInRegistries.SOUND_EVENT, COOKIE_CLICK_ID, COOKIE_CLICK_EVENT);

        ConfigManager.load();

        KeyMapping.Category farmingCategory = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "keys")
        );

        EMERGENCY_STOP_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.gardenease.emergency_stop",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_0,
            farmingCategory
        ));
        QUICK_RESUME_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.gardenease.quick_resume",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            farmingCategory
        ));
        START_PROFILE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.gardenease.start_profile",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            farmingCategory
        ));

        EmergencyHotkey.register();
        MouseGrabManager.register();
        RecordingManager.init();
        ResumeHighlightRenderer.register();
        ProfileHotkey.register();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            FarmMacroCommand.register(dispatcher, registryAccess);
        });

        LOGGER.info("{} v{} initialized successfully!", MOD_NAME, MOD_VERSION);
    }
/*
    private boolean isAuthorized(String uuid) {
        try {
            java.net.URL url = new java.net.URL("http://193.149.164.240:2175/verify-uuid"); 
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            // Send UUID in JSON body
            String jsonInput = "{\"uuid\":\"" + uuid + "\"}";
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonInput.getBytes());
                os.flush();
            }

            java.util.Scanner scanner = new java.util.Scanner(conn.getInputStream());
            StringBuilder response = new StringBuilder();
            while (scanner.hasNextLine()) response.append(scanner.nextLine());
            scanner.close();

            // Check if authorized
            return response.toString().contains("\"authorized\":true");
        } catch (Exception e) {
            LOGGER.error("Failed to verify license: " + e.getMessage());
            return false;
        }
    }*/
}
