package dev.basefinder.modules;

import dev.basefinder.config.BaseFinderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Small client-side quality-of-life modules: zoom, fullbright and toggle sprint. */
public final class Modules {
	private static int savedFov = -1;
	private static boolean nightVisionOwned;

	private Modules() {
	}

	public static void tick(Minecraft mc, boolean zoomHeld) {
		LocalPlayer player = mc.player;
		BaseFinderConfig cfg = BaseFinderConfig.get();
		if (player == null) {
			savedFov = -1;
			nightVisionOwned = false;
			return;
		}

		// Zoom: lower the FOV while the zoom key is held, then restore the previous value.
		boolean zoom = cfg.moduleOn("zoom") && zoomHeld && mc.screen == null;
		if (zoom) {
			if (savedFov < 0) {
				savedFov = mc.options.fov().get();
			}
			mc.options.fov().set(cfg.zoomFov);
		} else if (savedFov >= 0) {
			mc.options.fov().set(savedFov);
			savedFov = -1;
		}

		// Fullbright: a client-side night vision effect that is removed again when switched off.
		if (cfg.moduleOn("fullbright")) {
			if (!player.hasEffect(MobEffects.NIGHT_VISION)) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1_000_000, 0, false, false));
				nightVisionOwned = true;
			}
		} else if (nightVisionOwned) {
			player.removeEffect(MobEffects.NIGHT_VISION);
			nightVisionOwned = false;
		}

		// Toggle sprint: hold sprint automatically while walking forward.
		if (cfg.moduleOn("togglesprint") && mc.screen == null && mc.options.keyUp.isDown()
			&& !player.isShiftKeyDown()) {
			mc.options.keySprint.setDown(true);
		}
	}
}
