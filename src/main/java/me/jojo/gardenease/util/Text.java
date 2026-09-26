package me.jojo.gardenease.util;

import net.minecraft.network.chat.Component;

/**
 * Small source-compatibility helper for the old Yarn Text.literal call sites.
 * Official mappings expose the same operation through Component.literal.
 */
public final class Text {
    private Text() {
    }

    public static Component literal(String value) {
        return Component.literal(value);
    }
}