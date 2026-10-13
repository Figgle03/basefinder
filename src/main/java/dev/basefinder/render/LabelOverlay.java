package dev.basefinder.render;

import dev.basefinder.config.BaseFinderConfig;
import dev.basefinder.detect.DetectionManager;
import dev.basefinder.detect.Target;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Draws distance / coordinate labels on the HUD. World positions are projected to the screen with plain
 * vector math (camera forward/right/up plus the FOV option), so no matrix access is needed.
 * The FOV used is the base option value, so labels drift slightly during sprint/speed FOV effects.
 */
public final class LabelOverlay {
	private final DetectionManager detection;

	public LabelOverlay(DetectionManager detection) {
		this.detection = detection;
	}

	public void extract(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		BaseFinderConfig cfg = BaseFinderConfig.get();
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || !cfg.masterEnabled || !cfg.showLabels || mc.options.hideGui) {
			return;
		}
		List<Target> targets = detection.snapshot();
		if (targets.isEmpty()) {
			return;
		}

		float partial = tickCounter.getGameTimeDeltaPartialTick(false);
		net.minecraft.world.entity.Entity view = mc.getCameraEntity() != null ? mc.getCameraEntity() : player;
		Vec3 eye = view.getEyePosition(partial);
		Vec3 forward = view.getViewVector(partial).normalize();
		Vec3 right = forward.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0E-6) {
			return; // looking straight up or down
		}
		right = right.normalize();
		Vec3 up = right.cross(forward).normalize();

		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();
		double tanHalfFov = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
		double aspect = (double) w / h;

		int drawn = 0;
		for (Target t : targets) {
			if (drawn >= cfg.maxLabels || t.distance() > cfg.labelRange) {
				break;
			}
			Vec3 d = new Vec3(t.cx() - eye.x, t.cy() + 0.7 - eye.y, t.cz() - eye.z);
			double depth = d.dot(forward);
			if (depth <= 0.1) {
				continue;
			}
			double nx = d.dot(right) / depth / (tanHalfFov * aspect);
			double ny = d.dot(up) / depth / tanHalfFov;
			if (Math.abs(nx) > 1.0 || Math.abs(ny) > 1.0) {
				continue;
			}
			int sx = (int) (w / 2.0 + nx * w / 2.0);
			int sy = (int) (h / 2.0 - ny * h / 2.0);

			StringBuilder text = new StringBuilder(cfg.showNames ? t.name() : "");
			if (cfg.showDistance) {
				text.append(" ").append(Math.round(t.distance())).append("m");
			}
			if (cfg.showCoords && t.blockPos() != null) {
				text.append(" [").append(t.blockPos().getX()).append(' ').append(t.blockPos().getY())
					.append(' ').append(t.blockPos().getZ()).append(']');
			} else if (cfg.showCoords) {
				text.append(" [").append((int) Math.floor(t.cx())).append(' ').append((int) Math.floor(t.cy()))
					.append(' ').append((int) Math.floor(t.cz())).append(']');
			}
			String s = text.toString().trim();
			if (s.isEmpty()) {
				continue;
			}
			int tw = mc.font.width(s);
			int x = sx - tw / 2;
			graphics.fill(x - 2, sy - 2, x + tw + 2, sy + mc.font.lineHeight + 1, 0x99000000);
			graphics.text(mc.font, s, x, sy, 0xFF000000 | t.rgb(), true);
			drawn++;
		}
	}
}
