package dev.lightesp;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class LightEspClient implements ClientModInitializer {
	private static KeyMapping menuKey;
	private static KeyMapping toggleKey;
	private static KeyMapping freecamKey;

	@Override
	public void onInitializeClient() {
		LightEspConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("lightesp", "main"));

		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.lightesp.menu", InputConstants.KEY_RSHIFT, category));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.lightesp.toggle", InputConstants.KEY_K, category));
		freecamKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.lightesp.freecam", InputConstants.KEY_F4, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (menuKey.consumeClick()) {
				client.gui.setScreen(new LightEspScreen(null));
			}
			while (toggleKey.consumeClick()) {
				LightEspConfig cfg = LightEspConfig.get();
				cfg.enabled = !cfg.enabled;
				if (!cfg.enabled) LightScanner.clear();
				LightEspConfig.save();
				if (client.player != null) {
					client.player.sendSystemMessage(Component.literal("Light ESP: " + (cfg.enabled ? "ON" : "OFF")));
				}
			}
			while (freecamKey.consumeClick()) {
				Freecam.toggle(client);
			}
			Freecam.tick(client);
			LightScanner.tick(client);
			StorageScanner.tick(client);
		});

		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			Freecam.frame(Minecraft.getInstance());
			LightRenderer.render();
		});
	}
}
