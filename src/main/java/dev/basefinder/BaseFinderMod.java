package dev.basefinder;

import com.mojang.blaze3d.platform.InputConstants;
import dev.basefinder.config.BaseFinderConfig;
import dev.basefinder.detect.DetectionManager;
import dev.basefinder.gui.ClickGuiScreen;
import dev.basefinder.render.EspRenderer;
import dev.basefinder.render.LabelOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Entry point. Wires detection, rendering, config and GUI together. */
public final class BaseFinderMod implements ClientModInitializer {
	public static final String MOD_ID = "basefinder";

	private static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	private static DetectionManager detectionRef;

	/** How many things are currently highlighted (shown in the menu). */
	public static int targetCount() {
		return detectionRef == null ? 0 : detectionRef.snapshot().size();
	}

	@Override
	public void onInitializeClient() {
		BaseFinderConfig.get();

		DetectionManager detection = new DetectionManager();
		detectionRef = detection;
		EspRenderer renderer = new EspRenderer(detection);
		LabelOverlay labels = new LabelOverlay(detection);

		// Rebindable under Options > Controls > Key Binds > "Base Finder ESP".
		KeyMapping openGui = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.basefinder.open_gui", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, CATEGORY));

		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> detection.scanner().onChunkLoad(chunk));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> detection.scanner().onChunkUnload(chunk));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> detection.reset());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openGui.consumeClick()) {
				client.setScreen(new ClickGuiScreen());
			}
			detection.tick(client);
		});

		LevelRenderEvents.END_EXTRACTION.register(renderer::extract);
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(renderer::draw);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "labels"), labels::extract);

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			renderer.close();
			BaseFinderConfig.get().save();
		});
	}
}
