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
 * Draws compact labels on the HUD: dark pill, colored accent bar, white name, colored distance, grey coordinates.
 * World positions are projected with plain vector math (camera forward/right/up plus the FOV option).
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
		Vec3 eye = player.getEyePosition(partial);
		Vec3 forward = player.getViewVector(partial).normalize();
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
		int lineH = mc.font.lineHeight;

		int drawn = 0;
		for (Target t : targets) {
			if (drawn >= cfg.maxLabels) {
				break;
			}
			if (!t.showLabel() || t.distance() > cfg.labelRange) {
				continue;
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

			String name = cfg.showNames ? t.name() : "";
			String dist = cfg.showDistance ? Math.round(t.distance()) + "m" : "";
			String coords = "";
			if (cfg.showCoords) {
				int cx = t.blockPos() != null ? t.blockPos().getX() : (int) Math.floor(t.cx());
				int cy = t.blockPos() != null ? t.blockPos().getY() : (int) Math.floor(t.cy());
				int cz = t.blockPos() != null ? t.blockPos().getZ() : (int) Math.floor(t.cz());
				coords = cx + " " + cy + " " + cz;
			}
			if (name.isEmpty() && dist.isEmpty() && coords.isEmpty()) {
				continue;
			}

			int gap = 5;
			int nameW = name.isEmpty() ? 0 : mc.font.width(name);
			int distW = dist.isEmpty() ? 0 : mc.font.width(dist);
			int coordW = coords.isEmpty() ? 0 : mc.font.width(coords);
			int total = nameW + distW + coordW;
			int parts = (nameW > 0 ? 1 : 0) + (distW > 0 ? 1 : 0) + (coordW > 0 ? 1 : 0);
			total += gap * Math.max(0, parts - 1);

			int x0 = sx - total / 2;
			int accent = 0xFF000000 | t.rgb();
			graphics.fill(x0 - 6, sy - 3, x0 + total + 4, sy + lineH + 2, 0xB30E1015);
			graphics.fill(x0 - 6, sy - 3, x0 - 4, sy + lineH + 2, accent);

			int cursor = x0;
			if (nameW > 0) {
				graphics.text(mc.font, name, cursor, sy, 0xFFFFFFFF, true);
				cursor += nameW + gap;
			}
			if (distW > 0) {
				graphics.text(mc.font, dist, cursor, sy, accent, true);
				cursor += distW + gap;
			}
			if (coordW > 0) {
				graphics.text(mc.font, coords, cursor, sy, 0xFF8A8F9C, true);
			}
			drawn++;
		}
	}
}
