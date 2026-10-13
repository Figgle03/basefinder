package dev.basefinder.config;

/** The ESP categories. Each one has its own toggle and color. */
public enum EspCategory {
	STORAGE("Storage ESP", 0xFFB020),
	UNDERGROUND("Underground ESP", 0x38D9A9),
	BLOCK("Block ESP", 0xB366FF),
	ENTITY("Entity ESP", 0xFF4D4D),
	STRUCTURE("Structure ESP", 0x4DA6FF);

	public final String displayName;
	public final int defaultColor;

	EspCategory(String displayName, int defaultColor) {
		this.displayName = displayName;
		this.defaultColor = defaultColor;
	}
}
