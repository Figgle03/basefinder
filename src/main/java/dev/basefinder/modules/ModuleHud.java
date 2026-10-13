package dev.basefinder.modules;

import dev.basefinder.config.BaseFinderConfig;
import dev.basefinder.config.EspCategory;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Coordinates readout (bottom left) and the enabled-module list (top right). */
public final class ModuleHud {
	private static final int ACCENT = 0xFF7C5CFF;

	private record Entry(String text, int color) {
	}

	public void extract(GuiGraphicsExtractor g, DeltaTracker tickCounter) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		BaseFinderConfig cfg = BaseFinderConfig.get();
		if (player == null || mc.options.hideGui) {
			return;
		}
		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();

		if (cfg.moduleOn("coords")) {
			String s = String.format(Locale.ROOT, "XYZ %d %d %d  %s", (int) Math.floor(player.getX()),
				(int) Math.floor(player.getY()), (int) Math.floor(player.getZ()), player.getDirection().getName());
			int tw = mc.font.width(s);
			g.fill(2, h - 14, tw + 8, h - 2, 0x99000000);
			g.fill(2, h - 14, 4, h - 2, ACCENT);
			g.text(mc.font, s, 6, h - 12, 0xFFFFFFFF, true);
		}

		if (cfg.moduleOn("modulelist")) {
			List<Entry> list = new ArrayList<>();
			for (EspCategory c : EspCategory.values()) {
				if (cfg.isOn(c)) {
					list.add(new Entry(c.displayName, 0xFF000000 | cfg.color(c)));
				}
			}
			if (cfg.moduleOn("zoom")) {
				list.add(new Entry("Zoom", ACCENT));
			}
			if (cfg.moduleOn("fullbright")) {
				list.add(new Entry("Fullbright", ACCENT));
			}
			if (cfg.moduleOn("togglesprint")) {
				list.add(new Entry("Toggle Sprint", ACCENT));
			}
			list.sort(Comparator.comparingInt((Entry e) -> mc.font.width(e.text())).reversed());
			int y = 3;
			for (Entry e : list) {
				int tw = mc.font.width(e.text());
				g.fill(w - tw - 10, y, w - 2, y + 11, 0x99000000);
				g.fill(w - 4, y, w - 2, y + 11, e.color());
				g.text(mc.font, e.text(), w - tw - 7, y + 2, e.color(), true);
				y += 11;
			}
		}
	}
}
