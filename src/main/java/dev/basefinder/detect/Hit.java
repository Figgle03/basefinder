package dev.basefinder.detect;

import dev.basefinder.config.EspCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/** One cached detection inside a loaded chunk. Immutable. */
public record Hit(BlockPos pos, EspCategory category, String name, Block block) {
}
