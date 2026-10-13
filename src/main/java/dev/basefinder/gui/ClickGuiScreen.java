package dev.basefinder.gui;

import dev.basefinder.BaseFinderMod;
import dev.basefinder.config.BaseFinderConfig;
import dev.basefinder.config.EspCategory;
import dev.basefinder.detect.BlockSets;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Dark, tabbed settings menu. Everything is drawn with plain rectangles and text. Tabs on the left, a scrolling
 * list on the right, a color picker popup, and a Blocks tab that can search and pick any block in the game.
 */
public final class ClickGuiScreen extends Screen {
	// ---- theme ----
	private static final int DIM = 0xB0000000;
	private static final int BORDER = 0xFF2A2D3A;
	private static final int PANEL = 0xFF14161D;
	private static final int SIDEBAR = 0xFF0E1015;
	private static final int HEADER = 0xFF191C25;
	private static final int ROW_HOVER = 0xFF1D2029;
	private static final int ACCENT = 0xFF7C5CFF;
	private static final int TEXT = 0xFFE6E8EE;
	private static final int MUTED = 0xFF8A8F9C;
	private static final int ON = 0xFF3DDC97;
	private static final int OFF = 0xFF3A3E4A;
	private static final int BUTTON = 0xFF232632;

	private static final int ROW_H = 24;
	private static final int SIDE_W = 112;
	private static final int HEADER_H = 30;

	private static final int[] PRESETS = {
		0xFF4D4D, 0xFF9F1A, 0xFFB020, 0xFFE14D, 0xA3E635, 0x3DDC97, 0x38D9A9, 0x4DD8FF,
		0x4DA6FF, 0x5B6CFF, 0x7C5CFF, 0xB366FF, 0xFF5CC8, 0xFF8FB3, 0xFFFFFF, 0x9AA0AE
	};

	private static final String[] ORE_IDS = {
		"minecraft:ancient_debris", "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
		"minecraft:emerald_ore", "minecraft:deepslate_emerald_ore", "minecraft:gold_ore",
		"minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore", "minecraft:iron_ore",
		"minecraft:deepslate_iron_ore", "minecraft:copper_ore", "minecraft:deepslate_copper_ore",
		"minecraft:coal_ore", "minecraft:deepslate_coal_ore", "minecraft:redstone_ore",
		"minecraft:deepslate_redstone_ore", "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
		"minecraft:nether_quartz_ore"
	};
	private static final int[] ORE_COLORS = {
		0xB5651D, 0x4DD8FF, 0x4DD8FF, 0x3DDC97, 0x3DDC97, 0xFFE14D, 0xFFE14D, 0xFFE14D, 0xD8AF93,
		0xD8AF93, 0xFF9F1A, 0xFF9F1A, 0x9AA0AE, 0x9AA0AE, 0xFF4D4D, 0xFF4D4D, 0x4D6BFF, 0x4D6BFF, 0xFFFFFF
	};
	private static final String[] BASE_IDS = {
		"minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:furnace", "minecraft:blast_furnace",
		"minecraft:smoker", "minecraft:crafting_table", "minecraft:enchanting_table", "minecraft:anvil",
		"minecraft:beacon", "minecraft:respawn_anchor", "minecraft:spawner", "minecraft:tnt",
		"minecraft:netherite_block", "minecraft:diamond_block", "minecraft:end_portal_frame"
	};

	private enum Tab {
		GENERAL("General"), STORAGE("Storage"), BLOCKS("Blocks"), ENTITIES("Entities"),
		STRUCTURES("Structures"), LABELS("Labels");

		final String title;

		Tab(String title) {
			this.title = title;
		}
	}

	private record BlockInfo(String id, String name, String search) {
	}

	private static List<BlockInfo> allBlocks;

	private static List<BlockInfo> allBlocks() {
		if (allBlocks == null) {
			List<BlockInfo> list = new ArrayList<>();
			for (Block block : BuiltInRegistries.BLOCK) {
				var key = BuiltInRegistries.BLOCK.getKey(block);
				String path = key.getPath();
				if (path.equals("air") || path.equals("cave_air") || path.equals("void_air")) {
					continue;
				}
				if (BlockSets.storageKey(block) != null) {
					continue; // storage blocks live in the Storage tab
				}
				String name = path.replace('_', ' ');
				list.add(new BlockInfo(key.toString(), name, (name + " " + key).toLowerCase(Locale.ROOT)));
			}
			list.sort(Comparator.comparing(BlockInfo::name));
			allBlocks = list;
		}
		return allBlocks;
	}

	private abstract class Row {
		final String label;

		Row(String label) {
			this.label = label;
		}

		abstract void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hover);

		boolean click(int x, int y, int w, int mx, int my) {
			return false;
		}

		boolean searchable() {
			return true;
		}
	}

	private final class HeaderRow extends Row {
		HeaderRow(String label) {
			super(label);
		}

		@Override
		boolean searchable() {
			return false;
		}

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hover) {
			g.text(font, label.toUpperCase(Locale.ROOT), x + 4, y + 11, ACCENT, false);
			g.fill(x + 4 + font.width(label) + 8, y + 15, x + w, y + 16, BORDER);
		}
	}

	private final class ToggleRow extends Row {
		private final BooleanSupplier get;
		private final Consumer<Boolean> set;

		ToggleRow(String label, BooleanSupplier get, Consumer<Boolean> set) {
			super(label);
			this.get = get;
			this.set = set;
		}

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hover) {
			if (hover) {
				g.fill(x, y, x + w, y + ROW_H - 2, ROW_HOVER);
			}
			g.text(font, fit(label, w - 50), x + 8, y + 8, TEXT, false);
			pill(g, x + w - 34, y + 6, get.getAsBoolean());
		}

		@Override
		boolean click(int x, int y, int w, int mx, int my) {
			set.accept(!get.getAsBoolean());
			return true;
		}
	}

	private final class CategoryRow extends Row {
		private final EspCategory category;

		CategoryRow(EspCategory category) {
			super(category.displayName);
			this.category = category;
		}

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hover) {
			if (hover) {
				g.fill(x, y, x + w, y + ROW_H - 2, ROW_HOVER);
			}
			g.text(font, label, x + 8, y + 8, TEXT, false);
			swatch(g, x + w - 58, y + 5, cfg.color(category));
			pill(g, x + w - 34, y + 6, cfg.enabled.getOrDefault(category.name(), false));
		}

		@Override
		boolean click(int x, int y, int w, int mx, int my) {
			if (mx >= x + w - 60 && mx < x + w - 38) {
				openPicker(category.displayName + " color", cfg.color(category), v -> cfg.setColor(category, v));
			} else {
				cfg.setOn(category, !cfg.enabled.getOrDefault(category.name(), false));
			}
			return true;
		}
	}

	private final class SliderRow extends Row {
		private final int min;
		private final int max;
		private final IntSupplier get;
		private final IntConsumer set;

		SliderRow(String label, int min, int max, IntSupplier get, IntConsumer set) {
			super(label);
			this.min = min;
			this.max = max;
			this.get = get;
			this.set = set;
		}

		int trackX(int x, int w) {
			return x + (int) (w * 0.44);
		}

		int trackW(int w) {
			return (int) (w * 0.56) - 56;
		}

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hover) {
			if (hover || activeSlider == this) {
				g.fill(x, y, x + w, y + ROW_H - 2, ROW_HOVER);
			}
			g.text(font, fit(label, (int) (w * 0.44) - 12), x + 8, y + 8, TEXT, false);
			int tx = trackX(x, w);
			int tw = trackW(w);
			double frac = (get.getAsInt() - min) / (double) (max - min);
			int knob = tx + (int) (frac * tw);
			g.fill(tx, y + 10, tx + tw, y + 13, OFF);
			g.fill(tx, y + 10, knob, y + 13, ACCENT);
			g.fill(knob - 3, y + 6, knob + 3, y + 17, TEXT);
			String v = String.valueOf(get.getAsInt());
			g.text(font, v, x + w - 6 - font.width(v), y + 8, MUTED, false);
		}

		@Override
		boolean click(int x, int y, int w, int mx, int my) {
			activeSlider = this;
			activeX = trackX(x, w);
			activeW = trackW(w);
			drag(mx);
			return true;
		}

		void drag(int mx) {
			double frac = Math.max(0, Math.min(1, (mx - activeX) / (double) activeW));
			set.accept(min + (int) Math.round(frac * (max - min)));
		}
	}

	private final BaseFinderConfig cfg = BaseFinderConfig.get();
	private final Map<Tab, List<Row>> rows = new EnumMap<>(Tab.class);
	private final List<BlockInfo> blocksView = new ArrayList<>();

	private Tab tab = Tab.GENERAL;
	private String filter = "";
	private boolean selectedOnly;
	private int scroll;
	private EditBox searchBox;

	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;

	private SliderRow activeSlider;
	private int activeX;
	private int activeW;

	// color picker popup
	private boolean popup;
	private String popupTitle = "";
	private int popupColor;
	private IntConsumer popupSetter;
	private boolean draggingHue;

	public ClickGuiScreen() {
		super(Component.literal("Base Finder ESP"));
		buildRows();
		refilterBlocks();
	}

	// ------------------------------------------------------------------ rows

	private void buildRows() {
		List<Row> general = new ArrayList<>();
		general.add(new HeaderRow("ESP categories"));
		for (EspCategory c : EspCategory.values()) {
			general.add(new CategoryRow(c));
		}
		general.add(new HeaderRow("Scanning"));
		general.add(new SliderRow("Range (blocks)", 16, 512, () -> cfg.range, v -> cfg.range = v));
		general.add(new SliderRow("Max highlights", 100, 10000, () -> cfg.maxTargets, v -> cfg.maxTargets = v));
		general.add(new SliderRow("Fill opacity %", 5, 90, () -> cfg.fillOpacity, v -> cfg.fillOpacity = v));
		rows.put(Tab.GENERAL, general);

		List<Row> storage = new ArrayList<>();
		storage.add(new HeaderRow("Storage types"));
		String[][] types = {
			{"chest", "Chests"}, {"trapped_chest", "Trapped chests"}, {"barrel", "Barrels"},
			{"shulker_box", "Shulker boxes"}, {"ender_chest", "Ender chests"}, {"hopper", "Hoppers"},
			{"dispenser", "Dispensers"}, {"dropper", "Droppers"}
		};
		for (String[] t : types) {
			storage.add(new ToggleRow(t[1], () -> cfg.storageAllowed(t[0]), v -> cfg.storageTypes.put(t[0], v)));
		}
		rows.put(Tab.STORAGE, storage);

		List<Row> entities = new ArrayList<>();
		entities.add(new HeaderRow("Entity ESP"));
		entities.add(new ToggleRow("Include mobs and animals", () -> cfg.entityIncludeMobs,
			v -> cfg.entityIncludeMobs = v));
		rows.put(Tab.ENTITIES, entities);

		List<Row> structures = new ArrayList<>();
		structures.add(new HeaderRow("Structure ESP"));
		structures.add(new ToggleRow("Structure markers", () -> cfg.structureShowMarkers,
			v -> cfg.structureShowMarkers = v));
		structures.add(new ToggleRow("Entrances (doors)", () -> cfg.structureShowDoors,
			v -> cfg.structureShowDoors = v));
		structures.add(new SliderRow("Sensitivity (blocks per chunk)", 10, 400, () -> cfg.structureThreshold,
			v -> cfg.structureThreshold = v));
		rows.put(Tab.STRUCTURES, structures);

		List<Row> labels = new ArrayList<>();
		labels.add(new HeaderRow("Labels"));
		labels.add(new ToggleRow("Show labels", () -> cfg.showLabels, v -> cfg.showLabels = v));
		labels.add(new ToggleRow("Show names", () -> cfg.showNames, v -> cfg.showNames = v));
		labels.add(new ToggleRow("Show distance", () -> cfg.showDistance, v -> cfg.showDistance = v));
		labels.add(new ToggleRow("Show coordinates", () -> cfg.showCoords, v -> cfg.showCoords = v));
		labels.add(new SliderRow("Label range (blocks)", 8, 512, () -> cfg.labelRange, v -> cfg.labelRange = v));
		labels.add(new SliderRow("Max labels", 1, 64, () -> cfg.maxLabels, v -> cfg.maxLabels = v));
		rows.put(Tab.LABELS, labels);
	}

	private void refilterBlocks() {
		blocksView.clear();
		String q = filter.trim().toLowerCase(Locale.ROOT);
		for (BlockInfo b : allBlocks()) {
			if (selectedOnly && !cfg.isBlockSelected(b.id())) {
				continue;
			}
			if (q.isEmpty() || b.search().contains(q)) {
				blocksView.add(b);
			}
		}
		scroll = 0;
	}

	/** Rows currently listed (settings tabs, or search results across tabs). Null on the Blocks tab. */
	private List<Row> currentRows() {
		if (tab == Tab.BLOCKS) {
			return null;
		}
		String q = filter.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return rows.get(tab);
		}
		List<Row> hits = new ArrayList<>();
		for (Tab t : Tab.values()) {
			if (t == Tab.BLOCKS) {
				continue;
			}
			for (Row r : rows.get(t)) {
				if (r.searchable() && r.label.toLowerCase(Locale.ROOT).contains(q)) {
					hits.add(r);
				}
			}
		}
		return hits;
	}

	private int rowCount() {
		List<Row> r = currentRows();
		return r == null ? blocksView.size() : r.size();
	}

	// ------------------------------------------------------------------ layout

	@Override
	protected void init() {
		panelW = Math.min(this.width - 20, 580);
		panelH = Math.min(this.height - 20, 360);
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;

		searchBox = new EditBox(this.font, contentX(), contentTop(), contentW() - 8, 18, Component.literal("Search"));
		searchBox.setHint(Component.literal(tab == Tab.BLOCKS ? "Search every block in the game..." : "Search settings..."));
		searchBox.setValue(filter);
		searchBox.setResponder(text -> {
			filter = text;
			if (tab == Tab.BLOCKS) {
				refilterBlocks();
			}
			scroll = 0;
		});
		this.addRenderableWidget(searchBox);
	}

	private int contentX() {
		return panelX + SIDE_W + 10;
	}

	private int contentW() {
		return panelW - SIDE_W - 20;
	}

	private int contentTop() {
		return panelY + HEADER_H + 8;
	}

	private int listTop() {
		return contentTop() + 24 + (tab == Tab.BLOCKS ? 22 : 0);
	}

	private int listBottom() {
		return panelY + panelH - 8;
	}

	private int rowW() {
		return contentW() - 10;
	}

	private int visibleRows() {
		return Math.max(1, (listBottom() - listTop()) / ROW_H);
	}

	private void clampScroll() {
		scroll = Math.max(0, Math.min(scroll, Math.max(0, rowCount() - visibleRows())));
	}

	private void switchTab(Tab t) {
		tab = t;
		scroll = 0;
		searchBox.setHint(Component.literal(t == Tab.BLOCKS ? "Search every block in the game..." : "Search settings..."));
		if (t == Tab.BLOCKS) {
			refilterBlocks();
		}
	}

	// ------------------------------------------------------------------ drawing helpers

	private String fit(String s, int maxW) {
		if (font.width(s) <= maxW) {
			return s;
		}
		String t = s;
		while (t.length() > 1 && font.width(t + "...") > maxW) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "...";
	}

	private void pill(GuiGraphicsExtractor g, int x, int y, boolean on) {
		g.fill(x, y, x + 26, y + 12, on ? ON : OFF);
		int k = on ? x + 15 : x + 1;
		g.fill(k, y + 1, k + 10, y + 11, TEXT);
	}

	private void swatch(GuiGraphicsExtractor g, int x, int y, int rgb) {
		g.fill(x - 1, y - 1, x + 15, y + 15, BORDER);
		g.fill(x, y, x + 14, y + 14, 0xFF000000 | rgb);
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean active, boolean hover) {
		int bg = active ? ACCENT : (hover ? 0xFF2C3040 : BUTTON);
		g.fill(x, y, x + w, y + 18, bg);
		g.text(font, label, x + (w - font.width(label)) / 2, y + 5, TEXT, false);
	}

	private static boolean in(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static int hsv(float h) {
		float hh = (h - (float) Math.floor(h)) * 6f;
		int i = (int) hh;
		float f = hh - i;
		float q = 1 - f;
		float r;
		float gr;
		float b;
		switch (i % 6) {
			case 0 -> { r = 1; gr = f; b = 0; }
			case 1 -> { r = q; gr = 1; b = 0; }
			case 2 -> { r = 0; gr = 1; b = f; }
			case 3 -> { r = 0; gr = q; b = 1; }
			case 4 -> { r = f; gr = 0; b = 1; }
			default -> { r = 1; gr = 0; b = q; }
		}
		return ((int) (r * 255 + 0.5f) << 16) | ((int) (gr * 255 + 0.5f) << 8) | (int) (b * 255 + 0.5f);
	}

	private void openPicker(String title, int rgb, IntConsumer setter) {
		popup = true;
		popupTitle = title;
		popupColor = rgb;
		popupSetter = setter;
	}

	private void applyPopupColor(int rgb) {
		popupColor = rgb;
		popupSetter.accept(rgb);
	}

	// popup geometry
	private int popX() {
		return panelX + (panelW - 176) / 2;
	}

	private int popY() {
		return panelY + (panelH - 108) / 2;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		clampScroll();
		g.fill(0, 0, this.width, this.height, DIM);

		// panel
		g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, BORDER);
		g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
		g.fill(panelX, panelY, panelX + panelW, panelY + HEADER_H, HEADER);
		g.fill(panelX, panelY + HEADER_H - 1, panelX + panelW, panelY + HEADER_H, ACCENT);
		g.text(font, "BASE FINDER", panelX + 12, panelY + 11, TEXT, true);
		String count = BaseFinderMod.targetCount() + " highlighted";
		g.text(font, count, panelX + panelW - 30 - font.width(count), panelY + 11, MUTED, false);
		boolean overClose = in(mx, my, panelX + panelW - 24, panelY + 6, 18, 18);
		g.fill(panelX + panelW - 24, panelY + 6, panelX + panelW - 6, panelY + 24, overClose ? 0xFFB03A3A : BUTTON);
		g.text(font, "x", panelX + panelW - 18, panelY + 11, TEXT, false);

		// sidebar
		g.fill(panelX, panelY + HEADER_H, panelX + SIDE_W, panelY + panelH, SIDEBAR);
		int ty = panelY + HEADER_H + 8;
		for (Tab t : Tab.values()) {
			boolean active = t == tab;
			boolean hover = in(mx, my, panelX, ty, SIDE_W, 26);
			if (active) {
				g.fill(panelX, ty, panelX + SIDE_W, ty + 26, 0xFF1A1D28);
				g.fill(panelX, ty, panelX + 3, ty + 26, ACCENT);
			} else if (hover) {
				g.fill(panelX, ty, panelX + SIDE_W, ty + 26, 0xFF141720);
			}
			g.text(font, t.title, panelX + 16, ty + 9, active ? TEXT : MUTED, false);
			ty += 28;
		}
		// master switch at the bottom of the sidebar
		int my0 = panelY + panelH - 52;
		g.text(font, "Client data only", panelX + 12, my0 - 14, MUTED, false);
		g.text(font, "ESP", panelX + 12, my0 + 6, TEXT, false);
		pill(g, panelX + SIDE_W - 42, my0 + 4, cfg.masterEnabled);
		g.text(font, "Right Shift: close", panelX + 12, my0 + 24, MUTED, false);

		// search and toolbar
		searchBox.extractRenderState(g, mx, my, delta);
		int cx = contentX();
		if (tab == Tab.BLOCKS) {
			int by = contentTop() + 22;
			button(g, cx, by, 96, selectedOnly ? "Selected only" : "Show all", selectedOnly, in(mx, my, cx, by, 96, 18));
			button(g, cx + 102, by, 56, "+ Ores", false, in(mx, my, cx + 102, by, 56, 18));
			button(g, cx + 164, by, 56, "+ Base", false, in(mx, my, cx + 164, by, 56, 18));
			button(g, cx + 226, by, 48, "Clear", false, in(mx, my, cx + 226, by, 48, 18));
			String info = cfg.blocks.size() + " selected, " + blocksView.size() + " shown";
			g.text(font, info, cx + contentW() - 8 - font.width(info), by + 5, MUTED, false);
		} else if (!filter.isBlank()) {
			g.text(font, "Search results from all tabs", cx, contentTop() + 24 - 10, MUTED, false);
		}

		// list
		int lt = listTop();
		int vr = visibleRows();
		int rw = rowW();
		List<Row> list = currentRows();
		for (int i = 0; i < vr; i++) {
			int idx = scroll + i;
			if (idx >= rowCount()) {
				break;
			}
			int y = lt + i * ROW_H;
			boolean hover = !popup && in(mx, my, cx, y, rw, ROW_H - 2);
			if (list != null) {
				list.get(idx).draw(g, cx, y, rw, hover);
			} else {
				drawBlockRow(g, blocksView.get(idx), cx, y, rw, hover);
			}
		}
		if (rowCount() == 0) {
			g.text(font, tab == Tab.BLOCKS ? "No blocks match." : "No settings match.", cx + 8, lt + 8, MUTED, false);
		}

		// scrollbar
		int total = rowCount();
		if (total > vr) {
			int trackH = vr * ROW_H;
			int thumbH = Math.max(16, trackH * vr / total);
			int thumbY = lt + (trackH - thumbH) * scroll / Math.max(1, total - vr);
			g.fill(cx + contentW() - 4, lt, cx + contentW(), lt + trackH, 0xFF0E1015);
			g.fill(cx + contentW() - 4, thumbY, cx + contentW(), thumbY + thumbH, ACCENT);
		}

		if (popup) {
			drawPopup(g, mx, my);
		}
	}

	private void drawBlockRow(GuiGraphicsExtractor g, BlockInfo b, int x, int y, int w, boolean hover) {
		boolean selected = cfg.isBlockSelected(b.id());
		if (hover) {
			g.fill(x, y, x + w, y + ROW_H - 2, ROW_HOVER);
		}
		// checkbox
		g.fill(x + 6, y + 5, x + 20, y + 19, selected ? ACCENT : OFF);
		if (selected) {
			g.fill(x + 10, y + 9, x + 16, y + 15, TEXT);
		}
		// color swatch
		Integer own = cfg.blockColor(b.id());
		swatch(g, x + 26, y + 4, own != null ? own : cfg.color(EspCategory.BLOCK));
		g.text(font, fit(b.name(), w - 60 - 110), x + 48, y + 8, selected ? TEXT : MUTED, false);
		String id = b.id().startsWith("minecraft:") ? "" : b.id();
		if (!id.isEmpty()) {
			g.text(font, fit(id, 100), x + w - 6 - Math.min(100, font.width(id)), y + 8, MUTED, false);
		}
	}

	private void drawPopup(GuiGraphicsExtractor g, int mx, int my) {
		int x = popX();
		int y = popY();
		g.fill(0, 0, this.width, this.height, 0x66000000);
		g.fill(x - 1, y - 1, x + 177, y + 109, ACCENT);
		g.fill(x, y, x + 176, y + 108, PANEL);
		g.text(font, fit(popupTitle, 120), x + 8, y + 8, TEXT, false);
		g.fill(x + 176 - 8 - 22, y + 5, x + 176 - 8, y + 19, BORDER);
		g.fill(x + 176 - 7 - 22, y + 6, x + 176 - 9, y + 18, 0xFF000000 | popupColor);
		for (int i = 0; i < PRESETS.length; i++) {
			int sx = x + 8 + (i % 8) * 20;
			int sy = y + 26 + (i / 8) * 20;
			boolean cur = PRESETS[i] == popupColor;
			g.fill(sx - 1, sy - 1, sx + 17, sy + 17, cur ? TEXT : BORDER);
			g.fill(sx, sy, sx + 16, sy + 16, 0xFF000000 | PRESETS[i]);
		}
		int hy = y + 70;
		for (int i = 0; i < 160; i += 2) {
			g.fill(x + 8 + i, hy, x + 10 + i, hy + 12, 0xFF000000 | hsv(i / 160f));
		}
		g.text(font, "Drag for any color", x + 8, hy + 16, MUTED, false);
		button(g, x + 176 - 8 - 44, y + 108 - 24, 44, "Done", false, in(mx, my, x + 176 - 8 - 44, y + 84, 44, 18));
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mx = (int) event.x();
		int my = (int) event.y();

		if (popup) {
			handlePopupClick(mx, my);
			return true;
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() != 0) {
			return false;
		}

		if (in(mx, my, panelX + panelW - 24, panelY + 6, 18, 18)) {
			onClose();
			return true;
		}

		int ty = panelY + HEADER_H + 8;
		for (Tab t : Tab.values()) {
			if (in(mx, my, panelX, ty, SIDE_W, 26)) {
				switchTab(t);
				return true;
			}
			ty += 28;
		}
		int my0 = panelY + panelH - 52;
		if (in(mx, my, panelX + 6, my0, SIDE_W - 12, 22)) {
			cfg.masterEnabled = !cfg.masterEnabled;
			return true;
		}

		int cx = contentX();
		if (tab == Tab.BLOCKS) {
			int by = contentTop() + 22;
			if (in(mx, my, cx, by, 96, 18)) {
				selectedOnly = !selectedOnly;
				refilterBlocks();
				return true;
			}
			if (in(mx, my, cx + 102, by, 56, 18)) {
				for (int i = 0; i < ORE_IDS.length; i++) {
					cfg.setBlockSelected(ORE_IDS[i], true);
					if (cfg.blockColor(ORE_IDS[i]) == null) {
						cfg.setBlockColor(ORE_IDS[i], ORE_COLORS[i]);
					}
				}
				BlockSets.invalidate();
				return true;
			}
			if (in(mx, my, cx + 164, by, 56, 18)) {
				for (String id : BASE_IDS) {
					cfg.setBlockSelected(id, true);
				}
				BlockSets.invalidate();
				return true;
			}
			if (in(mx, my, cx + 226, by, 48, 18)) {
				cfg.blocks.clear();
				BlockSets.invalidate();
				if (selectedOnly) {
					refilterBlocks();
				}
				return true;
			}
		}

		int lt = listTop();
		if (my >= lt && my < lt + visibleRows() * ROW_H && mx >= cx && mx < cx + rowW()) {
			int idx = scroll + (my - lt) / ROW_H;
			if (idx < rowCount()) {
				int y = lt + (idx - scroll) * ROW_H;
				List<Row> list = currentRows();
				if (list != null) {
					return list.get(idx).click(cx, y, rowW(), mx, my);
				}
				return clickBlockRow(blocksView.get(idx), cx, mx);
			}
		}
		return false;
	}

	private boolean clickBlockRow(BlockInfo b, int x, int mx) {
		if (mx >= x + 24 && mx < x + 44) {
			Integer own = cfg.blockColor(b.id());
			openPicker(b.name() + " color", own != null ? own : cfg.color(EspCategory.BLOCK), v -> {
				cfg.setBlockColor(b.id(), v);
				BlockSets.invalidateColors();
			});
			if (!cfg.isBlockSelected(b.id())) {
				cfg.setBlockSelected(b.id(), true);
				BlockSets.invalidate();
			}
			return true;
		}
		cfg.setBlockSelected(b.id(), !cfg.isBlockSelected(b.id()));
		BlockSets.invalidate();
		if (selectedOnly) {
			int keep = scroll;
			refilterBlocks();
			scroll = keep;
		}
		return true;
	}

	private void handlePopupClick(int mx, int my) {
		int x = popX();
		int y = popY();
		if (in(mx, my, x + 176 - 8 - 44, y + 84, 44, 18) || !in(mx, my, x, y, 176, 108)) {
			popup = false;
			return;
		}
		for (int i = 0; i < PRESETS.length; i++) {
			int sx = x + 8 + (i % 8) * 20;
			int sy = y + 26 + (i / 8) * 20;
			if (in(mx, my, sx, sy, 16, 16)) {
				applyPopupColor(PRESETS[i]);
				return;
			}
		}
		if (in(mx, my, x + 8, y + 70, 160, 12)) {
			draggingHue = true;
			applyPopupColor(hsv((mx - (x + 8)) / 160f));
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		int mx = (int) event.x();
		if (draggingHue) {
			int x = popX() + 8;
			applyPopupColor(hsv(Math.max(0, Math.min(159, mx - x)) / 160f));
			return true;
		}
		if (activeSlider != null) {
			activeSlider.drag(mx);
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingHue = false;
		activeSlider = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!popup) {
			scroll -= (int) Math.signum(scrollY) * 2;
			clampScroll();
		}
		return true;
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
