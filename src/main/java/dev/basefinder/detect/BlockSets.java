package dev.basefinder.detect;

import dev.basefinder.config.BaseFinderConfig;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Resolves configured block ids and the built-in "looks man-made" block set. Both are cached. */
public final class BlockSets {
	private static Set<Block> wanted = Set.of();
	private static List<String> wantedSource = null;
	private static Set<Block> artificial;

	private BlockSets() {
	}

	/** Blocks selected for Block ESP, re-resolved only when the configured id list changes. */
	public static Set<Block> wanted() {
		List<String> ids = BaseFinderConfig.get().blocks;
		if (wantedSource == null || !wantedSource.equals(ids)) {
			Set<Block> set = new HashSet<>();
			for (String raw : ids) {
				Identifier id = Identifier.tryParse(raw.trim());
				if (id != null) {
					BuiltInRegistries.BLOCK.getOptional(id).ifPresent(set::add);
				}
			}
			wanted = set;
			wantedSource = List.copyOf(ids);
		}
		return wanted;
	}

	/** Forces the next call to wanted() to re-read the config. */
	public static void invalidate() {
		wantedSource = null;
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

	public static String niceName(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getPath().replace('_', ' ');
	}
}
