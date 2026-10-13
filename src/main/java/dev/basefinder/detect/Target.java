package dev.basefinder.detect;

import dev.basefinder.config.EspCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * An immutable, ready-to-render thing to highlight. The detection side builds a list of these and the
 * renderer only reads it, so no world access happens while drawing.
 *
 * @param blockPos location of a block target, or null for entities
 */
public record Target(AABB box, double cx, double cy, double cz, int rgb, EspCategory category, String name,
					 BlockPos blockPos, double distance) {
}
