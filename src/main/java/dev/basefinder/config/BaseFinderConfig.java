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

	public boolean masterEnabled = true;
	public Map<String, Boolean> enabled = new LinkedHashMap<>();
	public Map<String, Integer> colors = new LinkedHashMap<>();

	/** Scan/display range in blocks. Limited in practice by the server's view distance. */
	public int range = 64;
	public int maxTargets = 1500;
	/** Fill opacity in percent (5-90). */
	public int fillOpacity = 35;
	public boolean showLabels = true;
	public boolean showDistance = true;
	public boolean showCoords = true;
	public boolean entityIncludeMobs = false;
	/** Which storage types to show (keys in STORAGE_KEYS). */
	public Map<String, Boolean> storageTypes = new LinkedHashMap<>();
	/** Extra block ids typed in by the user, on top of the toggles. */
	public List<String> customBlocks = new ArrayList<>();
	public boolean structureShowMarkers = true;
	public boolean structureShowDoors = false;
	public boolean showNames = true;
	/** Labels are only drawn up to this distance and for at most maxLabels targets. */
	public int labelRange = 48;
	public int maxLabels = 20;
	/** Freecam fly speed, 1-20 (0.1 blocks per tick per step). */
	public int freecamSpeed = 5;
	/** Ore block ids shown by the Xray category. */
	public List<String> xrayBlocks = new ArrayList<>(Arrays.asList(
		"minecraft:ancient_debris", "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"
	));
	/** Minimum artificial blocks in one chunk before it is flagged as a structure. */
	public int structureThreshold = 60;
	/** Namespaced block ids for Block ESP. */
	public List<String> blocks = new ArrayList<>(Arrays.asList(
		"minecraft:obsidian", "minecraft:crying_obsidian",
		"minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
		"minecraft:crafting_table", "minecraft:enchanting_table", "minecraft:anvil",
		"minecraft:beacon", "minecraft:respawn_anchor", "minecraft:spawner"
	));

	public static final String[] STORAGE_KEYS = {
		"chest", "trapped_chest", "barrel", "shulker_box", "ender_chest", "hopper", "dispenser", "dropper"
	};

	public static final String[] XRAY_CATALOG = {
		"minecraft:ancient_debris", "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
		"minecraft:emerald_ore", "minecraft:deepslate_emerald_ore", "minecraft:gold_ore",
		"minecraft:deepslate_gold_ore", "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
		"minecraft:copper_ore", "minecraft:deepslate_copper_ore", "minecraft:coal_ore",
		"minecraft:deepslate_coal_ore", "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
		"minecraft:lapis_ore", "minecraft:deepslate_lapis_ore", "minecraft:nether_gold_ore",
		"minecraft:nether_quartz_ore"
	};

	/** Blocks that get their own toggle in the GUI. */
	public static final String[] BLOCK_CATALOG = {
		"minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:furnace", "minecraft:blast_furnace",
		"minecraft:smoker", "minecraft:crafting_table", "minecraft:enchanting_table", "minecraft:anvil",
		"minecraft:beacon", "minecraft:respawn_anchor", "minecraft:spawner", "minecraft:tnt",
		"minecraft:netherite_block", "minecraft:diamond_block", "minecraft:end_portal_frame"
	};

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
		for (EspCategory c : EspCategory.values()) {
			enabled.putIfAbsent(c.name(), c != EspCategory.ENTITY && c != EspCategory.XRAY);
			colors.putIfAbsent(c.name(), c.defaultColor);
		}
		if (storageTypes == null) storageTypes = new LinkedHashMap<>();
		if (customBlocks == null) customBlocks = new ArrayList<>();
		if (xrayBlocks == null) xrayBlocks = new ArrayList<>();
		freecamSpeed = Math.max(1, Math.min(20, freecamSpeed));
		for (String k : STORAGE_KEYS) {
			storageTypes.putIfAbsent(k, true);
		}
		labelRange = Math.max(8, Math.min(512, labelRange));
		maxLabels = Math.max(1, Math.min(64, maxLabels));
		range = Math.max(16, Math.min(512, range));
		maxTargets = Math.max(100, Math.min(5000, maxTargets));
		fillOpacity = Math.max(5, Math.min(90, fillOpacity));
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

	public boolean storageAllowed(String key) {
		return storageTypes.getOrDefault(key, true);
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
}
