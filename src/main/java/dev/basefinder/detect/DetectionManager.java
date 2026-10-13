package dev.basefinder.detect;

import dev.basefinder.config.BaseFinderConfig;
import dev.basefinder.config.EspCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns the scanner cache plus nearby entities into one immutable list of Targets every few ticks.
 * The renderer and label overlay only read that list.
 */
public final class DetectionManager {
	private static final int REFRESH_TICKS = 5;

	private final ChunkScanner scanner = new ChunkScanner();
	private volatile List<Target> snapshot = List.of();
	private int tick;

	public ChunkScanner scanner() {
		return scanner;
	}

	public List<Target> snapshot() {
		return snapshot;
	}

	public void reset() {
		scanner.clear();
		snapshot = List.of();
	}

	public void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		BaseFinderConfig cfg = BaseFinderConfig.get();
		if (level == null || player == null || !cfg.masterEnabled) {
			snapshot = List.of();
			return;
		}

		scanner.tick(level, player.getX(), player.getZ());

		if (++tick % REFRESH_TICKS != 0) {
			return;
		}

		double px = player.getX();
		double py = player.getY();
		double pz = player.getZ();
		double range = cfg.range;
		double range2 = range * range;
		List<Target> out = new ArrayList<>();

		boolean ug = cfg.isOn(EspCategory.UNDERGROUND);
		for (ChunkScanner.Entry entry : scanner.entries()) {
			double dxc = entry.cx * 16 + 8 - px;
			double dzc = entry.cz * 16 + 8 - pz;
			if (Math.abs(dxc) > range + 16 || Math.abs(dzc) > range + 16) {
				continue;
			}
			for (Hit hit : entry.hits) {
				BlockPos p = hit.pos();
				double dx = p.getX() + 0.5 - px;
				double dy = p.getY() + 0.5 - py;
				double dz = p.getZ() + 0.5 - pz;
				double d2 = dx * dx + dy * dy + dz * dz;
				if (d2 > range2) {
					continue;
				}
				// Drop cached hits whose block was broken or replaced since the last scan.
				if (hit.block() != null && level.getBlockState(p).getBlock() != hit.block()) {
					continue;
				}
				EspCategory shown = null;
				boolean underground = hit.category() != EspCategory.BLOCK
					&& p.getY() < py - 2 && !level.canSeeSky(p.above());
				if (ug && underground) {
					shown = EspCategory.UNDERGROUND;
				} else if (cfg.isOn(hit.category())) {
					shown = hit.category();
				}
				if (shown == null) {
					continue;
				}
				out.add(new Target(new AABB(p), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
					cfg.color(shown), shown, hit.name(), p, Math.sqrt(d2)));
			}
		}

		if (cfg.isOn(EspCategory.ENTITY)) {
			for (Entity e : level.entitiesForRendering()) {
				if (e == player || (!(e instanceof Player) && !cfg.entityIncludeMobs)) {
					continue;
				}
				double d2 = e.distanceToSqr(player);
				if (d2 > range2) {
					continue;
				}
				AABB box = e.getBoundingBox();
				out.add(new Target(box, e.getX(), e.getY() + e.getBbHeight() / 2.0, e.getZ(),
					cfg.color(EspCategory.ENTITY), EspCategory.ENTITY, e.getName().getString(), null, Math.sqrt(d2)));
			}
		}

		out.sort(Comparator.comparingDouble(Target::distance));
		if (out.size() > cfg.maxTargets) {
			out = new ArrayList<>(out.subList(0, cfg.maxTargets));
		}
		snapshot = List.copyOf(out);
	}
}
