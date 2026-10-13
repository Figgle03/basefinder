package dev.basefinder.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persistent settings, stored as JSON in the Fabric config directory. */
public final class BaseFinderConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("basefinder.json");
	private static BaseFinderConfig instance;

	public static final String[] STORAGE_KEYS = {
		"chest", "trapped_chest", "barrel", "shulker_box", "ender_chest", "hopper", "dispenser", "dropper"
	};

	public boolean masterEnabled = true;
	public Map<String, Boolean> enabled = new LinkedHashMap<>();
	public Map<String, Integer> colors = new LinkedHashMap<>();

	/** Scan/display range in blocks. Limited in practice by the server's view distance. */
	public int range = 64;
	public int maxTargets = 1500;
	/** Fill opacity in percent (5-90). */
	public int fillOpacity = 35;

	public boolean showLabels = true;
	public boolean showNames = true;
	public boolean showDistance = true;
	public boolean showCoords = true;
	/** Labels are only drawn up to this distance and for at most maxLabels targets. */
	public int labelRange = 48;
	public int maxLabels = 20;

	public boolean entityIncludeMobs = false;
	public Map<String, Boolean> storageTypes = new LinkedHashMap<>();
	public boolean structureShowMarkers = true;
	public boolean structureShowDoors = false;
	/** Minimum artificial blocks in one chunk before it is flagged as a structure. */
	public int structureThreshold = 60;

	/** Any block ids the user picked in the Blocks tab. */
	public List<String> blocks = new ArrayList<>(Arrays.asList(
		"minecraft:obsidian", "minecraft:crying_obsidian",
		"minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
		"minecraft:crafting_table", "minecraft:enchanting_table", "minecraft:anvil",
		"minecraft:beacon", "minecraft:respawn_anchor", "minecraft:spawner"
	));
	/** Optional per-block colors (RGB). Blocks without an entry use the Block ESP color. */
	public Map<String, Integer> blockColors = new LinkedHashMap<>();

	public static BaseFinderConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static BaseFinderConfig load() {
		BaseFinderConfig cfg = null;
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				cfg = GSON.fromJson(reader, BaseFinderConfig.class);
			} catch (IOException | RuntimeException e) {
				System.err.println("[BaseFinder] Could not read config, using defaults: " + e);
			}
		}
		if (cfg == null) {
			cfg = new BaseFinderConfig();
		}
		cfg.normalize();
		return cfg;
	}

	private void normalize() {
		if (enabled == null) enabled = new LinkedHashMap<>();
		if (colors == null) colors = new LinkedHashMap<>();
		if (blocks == null) blocks = new ArrayList<>();
		if (blockColors == null) blockColors = new LinkedHashMap<>();
		if (storageTypes == null) storageTypes = new LinkedHashMap<>();
		for (EspCategory c : EspCategory.values()) {
			enabled.putIfAbsent(c.name(), c != EspCategory.ENTITY);
			colors.putIfAbsent(c.name(), c.defaultColor);
		}
		for (String k : STORAGE_KEYS) {
			storageTypes.putIfAbsent(k, true);
		}
		range = Math.max(16, Math.min(512, range));
		maxTargets = Math.max(100, Math.min(10000, maxTargets));
		fillOpacity = Math.max(5, Math.min(90, fillOpacity));
		labelRange = Math.max(8, Math.min(512, labelRange));
		maxLabels = Math.max(1, Math.min(64, maxLabels));
		structureThreshold = Math.max(10, Math.min(400, structureThreshold));
	}

	public void save() {
		normalize();
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			System.err.println("[BaseFinder] Could not save config: " + e);
		}
	}

	public boolean isOn(EspCategory c) {
		return masterEnabled && enabled.getOrDefault(c.name(), false);
	}

	public void setOn(EspCategory c, boolean value) {
		enabled.put(c.name(), value);
	}

	public int color(EspCategory c) {
		return colors.getOrDefault(c.name(), c.defaultColor) & 0xFFFFFF;
	}

	public void setColor(EspCategory c, int rgb) {
		colors.put(c.name(), rgb & 0xFFFFFF);
	}

	public boolean storageAllowed(String key) {
		return storageTypes.getOrDefault(key, true);
	}

	public boolean isBlockSelected(String id) {
		return blocks.contains(id);
	}

	public void setBlockSelected(String id, boolean on) {
		if (on) {
			if (!blocks.contains(id)) {
				blocks.add(id);
			}
		} else {
			blocks.remove(id);
		}
	}

	/** Per-block color, or null if the block uses the Block ESP color. */
	public Integer blockColor(String id) {
		return blockColors.get(id);
	}

	public void setBlockColor(String id, int rgb) {
		blockColors.put(id, rgb & 0xFFFFFF);
	}
}
