package dev.basefinder.gui;

import dev.basefinder.config.BaseFinderConfig;
import dev.basefinder.config.EspCategory;
import dev.basefinder.detect.BlockSets;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Dark-themed settings screen: search field, per-category toggles with hex color boxes, sliders and a
 * block list. Rows are paged (no scrolling) and filtered live by the search box.
 */
public final class ClickGuiScreen extends Screen {
	private static final int PANEL_W = 340;
	private static final int ROW_H = 24;
	private static final int ROWS_PER_PAGE = 10;
	private static final int BG = 0xF0121317;
	private static final int HEADER = 0xFF1C1E26;
	private static final int ACCENT = 0xFF7C5CFF;

	private interface RowBuilder {
		void build(int x, int y, int width);
	}

	private record Row(String label, RowBuilder builder) {
	}

	private final BaseFinderConfig cfg = BaseFinderConfig.get();
	private final List<Row> allRows = new ArrayList<>();
	private String filter = "";
	private int page;
	private EditBox searchBox;

	public ClickGuiScreen() {
		super(Component.literal("Base Finder ESP"));
	}

	@Override
	protected void init() {
		allRows.clear();
		buildRows();

		int left = (this.width - PANEL_W) / 2;
		int top = Math.max(10, (this.height - (ROWS_PER_PAGE * ROW_H + 96)) / 2);

		searchBox = new EditBox(this.font, left + 12, top + 30, PANEL_W - 24, 18, Component.literal("Search"));
		searchBox.setHint(Component.literal("Search settings..."));
		searchBox.setValue(filter);
		searchBox.setResponder(text -> {
			filter = text;
			page = 0;
			rebuildPage();
		});
		this.addRenderableWidget(searchBox);

		rebuildPage();
	}

	/** Re-creates only the row widgets, keeping the search box (and its focus) intact. */
	private void rebuildPage() {
		List<AbstractWidget> keep = new ArrayList<>();
		for (var child : this.children()) {
			if (child == searchBox) {
				continue;
			}
			if (child instanceof AbstractWidget w) {
				keep.add(w);
			}
		}
		for (AbstractWidget w : keep) {
			this.removeWidget(w);
		}

		int left = (this.width - PANEL_W) / 2;
		int top = Math.max(10, (this.height - (ROWS_PER_PAGE * ROW_H + 96)) / 2);
		List<Row> rows = filtered();
		int pages = Math.max(1, (rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
		page = Math.max(0, Math.min(page, pages - 1));

		int start = page * ROWS_PER_PAGE;
		for (int i = start; i < Math.min(rows.size(), start + ROWS_PER_PAGE); i++) {
			int y = top + 56 + (i - start) * ROW_H;
			rows.get(i).builder().build(left + 12, y, PANEL_W - 24);
		}

		int footerY = top + 56 + ROWS_PER_PAGE * ROW_H + 4;
		Button prev = Button.builder(Component.literal("<"), b -> {
			page--;
			rebuildPage();
		}).bounds(left + 12, footerY, 24, 18).build();
		prev.active = page > 0;
		Button next = Button.builder(Component.literal(">"), b -> {
			page++;
			rebuildPage();
		}).bounds(left + 40, footerY, 24, 18).build();
		next.active = page < pages - 1;
		this.addRenderableWidget(prev);
		this.addRenderableWidget(next);
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
			.bounds(left + PANEL_W - 12 - 70, footerY, 70, 18).build());
	}

	private List<Row> filtered() {
		String q = filter.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return allRows;
		}
		List<Row> out = new ArrayList<>();
		for (Row r : allRows) {
			if (r.label().toLowerCase(Locale.ROOT).contains(q)) {
				out.add(r);
			}
		}
		return out;
	}

	private void buildRows() {
		allRows.add(new Row("Master switch enabled", (x, y, w) ->
			toggle(x, y, w, "Enabled", () -> cfg.masterEnabled, v -> cfg.masterEnabled = v)));

		for (EspCategory c : EspCategory.values()) {
			allRows.add(new Row(c.displayName + " color toggle", (x, y, w) -> {
				toggle(x, y, w - 76, c.displayName, () -> cfg.enabled.getOrDefault(c.name(), false),
					v -> cfg.setOn(c, v));
				colorBox(x + w - 70, y, c);
			}));
		}

		String[][] storage = {
			{"chest", "Chests"}, {"trapped_chest", "Trapped chests"}, {"barrel", "Barrels"},
			{"shulker_box", "Shulker boxes"}, {"ender_chest", "Ender chests"}, {"hopper", "Hoppers"},
			{"dispenser", "Dispensers"}, {"dropper", "Droppers"}
		};
		for (String[] st : storage) {
			allRows.add(new Row("Storage " + st[1], (x, y, w) ->
				toggle(x, y, w, "Storage: " + st[1], () -> cfg.storageAllowed(st[0]),
					v -> cfg.storageTypes.put(st[0], v))));
		}

		allRows.add(new Row("Structure markers", (x, y, w) ->
			toggle(x, y, w, "Structure: markers", () -> cfg.structureShowMarkers,
				v -> cfg.structureShowMarkers = v)));
		allRows.add(new Row("Structure entrances doors", (x, y, w) ->
			toggle(x, y, w, "Structure: entrances/doors", () -> cfg.structureShowDoors,
				v -> cfg.structureShowDoors = v)));

		for (String id : BaseFinderConfig.BLOCK_CATALOG) {
			String nice = id.substring(id.indexOf(':') + 1).replace('_', ' ');
			allRows.add(new Row("Block " + nice, (x, y, w) ->
				toggle(x, y, w, "Block: " + nice, () -> cfg.blocks.contains(id), v -> {
					if (v) {
						if (!cfg.blocks.contains(id)) {
							cfg.blocks.add(id);
						}
					} else {
						cfg.blocks.remove(id);
					}
					BlockSets.invalidate();
				})));
		}

		for (String id : BaseFinderConfig.XRAY_CATALOG) {
			String nice = id.substring(id.indexOf(':') + 1).replace('_', ' ');
			allRows.add(new Row("Xray ore " + nice, (x, y, w) ->
				toggle(x, y, w, "Xray: " + nice, () -> cfg.xrayBlocks.contains(id), v -> {
					if (v) {
						if (!cfg.xrayBlocks.contains(id)) {
							cfg.xrayBlocks.add(id);
						}
					} else {
						cfg.xrayBlocks.remove(id);
					}
					BlockSets.invalidate();
				})));
		}

		allRows.add(new Row("Freecam speed", (x, y, w) ->
			slider(x, y, w, "Freecam speed", 1, 20, () -> cfg.freecamSpeed, v -> cfg.freecamSpeed = v)));

		allRows.add(new Row("Custom block ids", (x, y, w) -> {
			EditBox box = new EditBox(this.font, x, y, w, 18, Component.literal("Custom blocks"));
			box.setMaxLength(2000);
			box.setHint(Component.literal("Custom ids: minecraft:bed, ..."));
			box.setValue(String.join(", ", cfg.customBlocks));
			box.setResponder(text -> {
				List<String> ids = new ArrayList<>();
				for (String s : text.split(",")) {
					if (!s.isBlank()) {
						ids.add(s.trim());
					}
				}
				cfg.customBlocks = ids;
				BlockSets.invalidate();
			});
			this.addRenderableWidget(box);
		}));

		allRows.add(new Row("Range", (x, y, w) ->
			slider(x, y, w, "Range", 16, 512, () -> cfg.range, v -> cfg.range = v)));
		allRows.add(new Row("Max targets", (x, y, w) ->
			slider(x, y, w, "Max targets", 100, 5000, () -> cfg.maxTargets, v -> cfg.maxTargets = v)));
		allRows.add(new Row("Fill opacity", (x, y, w) ->
			slider(x, y, w, "Fill opacity %", 5, 90, () -> cfg.fillOpacity, v -> cfg.fillOpacity = v)));
		allRows.add(new Row("Structure threshold sensitivity", (x, y, w) ->
			slider(x, y, w, "Structure threshold", 10, 400, () -> cfg.structureThreshold,
				v -> cfg.structureThreshold = v)));
		allRows.add(new Row("Label range distance", (x, y, w) ->
			slider(x, y, w, "Label range", 8, 512, () -> cfg.labelRange, v -> cfg.labelRange = v)));
		allRows.add(new Row("Max labels", (x, y, w) ->
			slider(x, y, w, "Max labels", 1, 64, () -> cfg.maxLabels, v -> cfg.maxLabels = v)));
		allRows.add(new Row("Labels", (x, y, w) ->
			toggle(x, y, w, "Labels", () -> cfg.showLabels, v -> cfg.showLabels = v)));
		allRows.add(new Row("Label names", (x, y, w) ->
			toggle(x, y, w, "Show names", () -> cfg.showNames, v -> cfg.showNames = v)));
		allRows.add(new Row("Distance labels", (x, y, w) ->
			toggle(x, y, w, "Show distance", () -> cfg.showDistance, v -> cfg.showDistance = v)));
		allRows.add(new Row("Coordinates display", (x, y, w) ->
			toggle(x, y, w, "Show coordinates", () -> cfg.showCoords, v -> cfg.showCoords = v)));
		allRows.add(new Row("Entity mobs include other entities", (x, y, w) ->
			toggle(x, y, w, "Entity ESP: include mobs", () -> cfg.entityIncludeMobs,
				v -> cfg.entityIncludeMobs = v)));
	}

	private void toggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set) {
		Button button = Button.builder(toggleText(label, get.getAsBoolean()), b -> {
			set.accept(!get.getAsBoolean());
			b.setMessage(toggleText(label, get.getAsBoolean()));
		}).bounds(x, y, w, 18).build();
		this.addRenderableWidget(button);
	}

	private static Component toggleText(String label, boolean on) {
		return Component.literal(label + ": " + (on ? "ON" : "OFF"));
	}

	private void colorBox(int x, int y, EspCategory c) {
		EditBox box = new EditBox(this.font, x, y, 70, 18, Component.literal("Color"));
		box.setMaxLength(7);
		box.setValue(String.format("#%06X", cfg.color(c)));
		box.setResponder(text -> {
			String t = text.startsWith("#") ? text.substring(1) : text;
			if (t.length() == 6) {
				try {
					cfg.setColor(c, Integer.parseInt(t, 16));
				} catch (NumberFormatException ignored) {
					// keep the previous color until the text is a valid hex value
				}
			}
		});
		this.addRenderableWidget(box);
	}

	private void slider(int x, int y, int w, String label, int min, int max, IntSupplier get, IntConsumer set) {
		this.addRenderableWidget(new IntSlider(x, y, w, label, min, max, get.getAsInt(), set));
	}

	private static final class IntSlider extends AbstractSliderButton {
		private final String label;
		private final int min;
		private final int max;
		private final IntConsumer setter;

		IntSlider(int x, int y, int w, String label, int min, int max, int value, IntConsumer setter) {
			super(x, y, w, 18, Component.empty(), (value - min) / (double) (max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.setter = setter;
			updateMessage();
		}

		private int current() {
			return min + (int) Math.round(this.value * (max - min));
		}

		@Override
		protected void updateMessage() {
			if (label == null) {
				return; // called by the superclass constructor before fields are set
			}
			setMessage(Component.literal(label + ": " + current()));
		}

		@Override
		protected void applyValue() {
			setter.accept(current());
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// Fully custom dark background instead of the default blurred menu background.
		graphics.fill(0, 0, this.width, this.height, 0x99000000);

		int left = (this.width - PANEL_W) / 2;
		int top = Math.max(10, (this.height - (ROWS_PER_PAGE * ROW_H + 96)) / 2);
		int bottom = top + 56 + ROWS_PER_PAGE * ROW_H + 28;

		graphics.fill(left - 1, top - 1, left + PANEL_W + 1, bottom + 1, ACCENT);
		graphics.fill(left, top, left + PANEL_W, bottom, BG);
		graphics.fill(left, top, left + PANEL_W, top + 24, HEADER);
		graphics.text(this.font, "BASE FINDER ESP", left + 12, top + 8, 0xFFFFFFFF, true);
		graphics.text(this.font, "client-side only", left + PANEL_W - 12 - this.font.width("client-side only"),
			top + 8, 0xFF8A8F9C, false);

		int pages = Math.max(1, (filtered().size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
		String info = "Page " + (page + 1) + "/" + pages;
		graphics.text(this.font, info, left + 72, top + 56 + ROWS_PER_PAGE * ROW_H + 9, 0xFF8A8F9C, false);

		for (var child : this.children()) {
			if (child instanceof AbstractWidget widget) {
				widget.extractRenderState(graphics, mouseX, mouseY, delta);
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		cfg.save();
		super.onClose();
	}
}
