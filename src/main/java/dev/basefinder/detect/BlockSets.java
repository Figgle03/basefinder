package dev.basefinder.detect;

import dev.basefinder.config.BaseFinderConfig;
import dev.basefinder.config.EspCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves configured block ids, per-block colors and the "looks man-made" block set. All cached. */
public final class BlockSets {
	private static Set<Block> wanted = Set.of();
	private static List<String> wantedSource = null;
	private static Set<Block> artificial;
	private static final Map<Block, Integer> COLORS = new HashMap<>();
	private static int version;

	private BlockSets() {
	}

	/** Blocks selected in the Blocks tab. Storage blocks are skipped here; the Storage tab handles them. */
	public static Set<Block> wanted() {
		List<String> ids = BaseFinderConfig.get().blocks;
		if (wantedSource == null || !wantedSource.equals(ids)) {
			Set<Block> set = new HashSet<>();
			for (String raw : ids) {
				Identifier id = Identifier.tryParse(raw.trim());
				if (id != null) {
					BuiltInRegistries.BLOCK.getOptional(id).ifPresent(b -> {
						if (storageKey(b) == null) {
							set.add(b);
						}
					});
				}
			}
			wanted = set;
			wantedSource = List.copyOf(ids);
		}
		return wanted;
	}

	/** Bumped whenever the selected blocks change, so cached chunks get rescanned. */
	public static int version() {
		return version;
	}

	/** Call after the selected block list changes. */
	public static void invalidate() {
		wantedSource = null;
		COLORS.clear();
		version++;
	}

	/** Call after only colors changed. */
	public static void invalidateColors() {
		COLORS.clear();
	}

	public static int colorFor(Block block) {
		Integer cached = COLORS.get(block);
		if (cached == null) {
			BaseFinderConfig cfg = BaseFinderConfig.get();
			Integer own = cfg.blockColor(BuiltInRegistries.BLOCK.getKey(block).toString());
			cached = own != null ? own & 0xFFFFFF : cfg.color(EspCategory.BLOCK);
			COLORS.put(block, cached);
		}
		return cached;
	}

	/** Blocks whose names suggest construction (planks, bricks, glass, doors, concrete...). */
	public static Set<Block> artificial() {
		if (artificial == null) {
			Set<Block> set = new HashSet<>();
			for (Block block : BuiltInRegistries.BLOCK) {
				String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
				if (path.contains("planks") || path.contains("brick") || path.contains("concrete")
					|| path.contains("glass") || path.endsWith("_door") || path.endsWith("_trapdoor")
					|| path.contains("polished") || path.contains("cut_") || path.contains("smooth_")) {
					set.add(block);
				}
			}
			artificial = set;
		}
		return artificial;
	}

	public static boolean isDoor(Block block) {
		String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		return path.endsWith("_door") || path.endsWith("_trapdoor");
	}

	public static boolean isStorage(BlockEntity be) {
		return be instanceof ChestBlockEntity      // chests and trapped chests
			|| be instanceof BarrelBlockEntity
			|| be instanceof ShulkerBoxBlockEntity
			|| be instanceof EnderChestBlockEntity
			|| be instanceof HopperBlockEntity
			|| be instanceof DispenserBlockEntity; // droppers extend dispensers
	}

	/** Storage type key for a block (matches BaseFinderConfig.STORAGE_KEYS), or null if it is not storage. */
	public static String storageKey(Block block) {
		if (block == null) {
			return null;
		}
		String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		if (path.endsWith("shulker_box")) {
			return "shulker_box";
		}
		for (String key : BaseFinderConfig.STORAGE_KEYS) {
			if (key.equals(path)) {
				return key;
			}
		}
		return null;
	}

	public static String niceName(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getPath().replace('_', ' ');
	}
}
