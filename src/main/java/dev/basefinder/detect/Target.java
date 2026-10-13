package dev.basefinder.detect;

import dev.basefinder.config.EspCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * An immutable, ready-to-render thing to highlight. The detection side builds a list of these and the
 * renderer only reads it, so no world access happens while drawing.
 *
 * @param blockPos  location of a block target, or null for entities
 * @param showLabel false for containers that are summarised by a grouped "Base" marker
 * @param showBox   whether a box is drawn at all
 * @param container true for an individual storage block (candidates for grouping)
 */
public record Target(AABB box, double cx, double cy, double cz, int rgb, EspCategory category, String name,
					 BlockPos blockPos, double distance, boolean showLabel, boolean showBox, boolean container) {

	/** A grouped summary marker for several containers close together. */
	public boolean isBase() {
		return category == EspCategory.STORAGE && !container && blockPos != null;
	}

	public Target withoutLabel() {
		return new Target(box, cx, cy, cz, rgb, category, name, blockPos, distance, false, showBox, container);
	}
}
