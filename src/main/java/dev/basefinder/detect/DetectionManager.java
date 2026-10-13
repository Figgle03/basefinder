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
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the scanner cache plus nearby entities into one immutable list of Targets every few ticks.
 * The renderer and label overlay only read that list.
 */
public final class DetectionManager {
	private static final int REFRESH_TICKS = 5;

	private final ChunkScanner scanner = new ChunkScanner();
	private volatile List<Target> snapshot = List.of();
	private int tick;
	private Object lastDimension;

	public ChunkScanner scanner() {
		return scanner;
	}

	public List<Target> snapshot() {
		return snapshot;
	}

	public void reset() {
		scanner.clear();
		snapshot = List.of();
		lastDimension = null;
	}

	/**
	 * Containers that sit close together are summarised by one "Base" marker. This is a simple grid
	 * heuristic (32 x 32 block cells), so a large base can be split across neighbouring cells.
	 */
	private static List<Target> groupBases(List<Target> in, BaseFinderConfig cfg, double px, double py, double pz) {
		Map<Long, List<Target>> cells = new HashMap<>();
		for (Target t : in) {
			if (!t.container() || t.blockPos() == null) {
				continue;
			}
			long key = ((long) (t.blockPos().getX() >> 5) << 32) ^ ((t.blockPos().getZ() >> 5) & 0xFFFFFFFFL);
			cells.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
		}
		Set<Target> grouped = Collections.newSetFromMap(new IdentityHashMap<>());
		List<Target> extra = new ArrayList<>();
		for (List<Target> cell : cells.values()) {
			if (cell.size() < cfg.baseMinContainers) {
				continue;
			}
			double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
			double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
			for (Target t : cell) {
				minX = Math.min(minX, t.box().minX);
				minY = Math.min(minY, t.box().minY);
				minZ = Math.min(minZ, t.box().minZ);
				maxX = Math.max(maxX, t.box().maxX);
				maxY = Math.max(maxY, t.box().maxY);
				maxZ = Math.max(maxZ, t.box().maxZ);
				grouped.add(t);
			}
			double cx = (minX + maxX) / 2;
			double cy = (minY + maxY) / 2;
			double cz = (minZ + maxZ) / 2;
			double dx = cx - px;
			double dy = cy - py;
			double dz = cz - pz;
			BlockPos center = new BlockPos((int) Math.floor(cx), (int) Math.floor(cy), (int) Math.floor(cz));
			extra.add(new Target(new AABB(minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1),
				cx, cy, cz, cfg.color(EspCategory.STORAGE), EspCategory.STORAGE,
				"Base: " + cell.size() + " containers", center, Math.sqrt(dx * dx + dy * dy + dz * dz),
				true, true, false));
		}
		List<Target> result = new ArrayList<>(in.size() + extra.size());
		for (Target t : in) {
			result.add(grouped.contains(t) ? t.withoutLabel() : t);
		}
		result.addAll(extra);
		return result;
	}

	public void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		BaseFinderConfig cfg = BaseFinderConfig.get();
		if (level == null || player == null || !cfg.masterEnabled) {
			snapshot = List.of();
			return;
		}

		// Remembered chunks belong to one dimension only.
		Object dimension = level.dimension();
		if (!dimension.equals(lastDimension)) {
			if (lastDimension != null) {
				scanner.clear();
			}
			lastDimension = dimension;
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
				if (!entry.unloaded && hit.block() != null && level.getBlockState(p).getBlock() != hit.block()) {
					continue;
				}
				// User filters for storage types and structure parts.
				if (hit.category() == EspCategory.STORAGE) {
					String key = BlockSets.storageKey(hit.block());
					if (key != null && !cfg.storageAllowed(key)) {
						continue;
					}
				} else if (hit.category() == EspCategory.STRUCTURE) {
					boolean door = "possible entrance".equals(hit.name());
					if (door ? !cfg.structureShowDoors : !cfg.structureShowMarkers) {
						continue;
					}
				}
				EspCategory shown = null;
				boolean underground = !entry.unloaded && hit.category() != EspCategory.BLOCK
					&& p.getY() < py - 2 && !level.canSeeSky(p.above());
				if (ug && underground) {
					shown = EspCategory.UNDERGROUND;
				} else if (cfg.isOn(hit.category())) {
					shown = hit.category();
				}
				if (shown == null) {
					continue;
				}
				int rgb = shown == EspCategory.BLOCK ? BlockSets.colorFor(hit.block()) : cfg.color(shown);
				out.add(new Target(new AABB(p), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
					rgb, shown, hit.name(), p, Math.sqrt(d2), true, true,
					hit.category() == EspCategory.STORAGE));
			}
		}

		if (cfg.groupBases) {
			out = groupBases(out, cfg, px, py, pz);
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
					cfg.color(EspCategory.ENTITY), EspCategory.ENTITY, e.getName().getString(), null, Math.sqrt(d2),
					true, true, false));
			}
		}

		out.sort(Comparator.comparingDouble(Target::distance));
		if (out.size() > cfg.maxTargets) {
			out = new ArrayList<>(out.subList(0, cfg.maxTargets));
		}
		snapshot = List.copyOf(out);
	}
}
