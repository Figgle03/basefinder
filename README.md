# Base Finder ESP (Fabric, Minecraft Java 26.1.2)

A client-only Fabric mod that highlights things your client already knows about: storage blocks,
selected base blocks, nearby entities and "man-made looking" chunks. It draws boxes through walls,
with distance and coordinate labels and a dark click GUI.

## Build status: read this first

**This source has not been compiled or run.** The environment it was written in blocked access to
`maven.fabricmc.net`, `services.gradle.org` and Mojang's servers, and only had JDK 21 (26.1.2 needs JDK 25).
The Fabric, Loom and Fabric API versions and the world-render, HUD, screen and key-mapping code were
checked against the Fabric 26.1.2 docs, but the first real build may still show small compile errors
(renamed methods, changed signatures). The areas most likely to need a tweak are:

| File | What to check |
| --- | --- |
| `render/EspRenderer.java` | Closely follows the Fabric docs sample; pipeline/buffer API names |
| `render/LabelOverlay.java` | `GuiGraphicsExtractor.text/fill`, `Window.getGuiScaledWidth/Height`, `Options.hideGui` |
| `gui/ClickGuiScreen.java` | `Screen.removeWidget`, `children()`, `EditBox.setHint`, widget `extractRenderState` |
| `detect/ChunkScanner.java` | `LevelChunk.getSectionsCount`, `getSectionYFromSectionIndex`, `ChunkPos.asLong/getX/getZ` |
| `detect/BlockSets.java` | `Registry.getOptional(Identifier)`, block entity class names |
| `build.gradle` | Uses `implementation` (not `modImplementation`) because 26.1 is unobfuscated |

## Requirements

* JDK 25
* Gradle 9.2+ (Loom 1.15.5 needs it)
* Minecraft Java 26.1.2, Fabric Loader 0.19.1+, Fabric API `0.145.4+26.1.2`

## Build

```
gradle wrapper --gradle-version 9.2.0   # once, creates gradlew
./gradlew build                         # Windows: gradlew.bat build
```

The jar is `build/libs/basefinder-1.0.0.jar` (use the one without `-sources`).
To test in a dev client: `./gradlew runClient`.

## Install

1. Install Fabric Loader for 26.1.2 and put Fabric API in `.minecraft/mods`.
2. Put `basefinder-1.0.0.jar` in `.minecraft/mods`.
3. Start the game. Press **Right Shift** to open the menu. Rebind it under
   Options > Controls > Key Binds > *Base Finder ESP*.

Settings are saved in `.minecraft/config/basefinder.json`.

## Features

* **Storage ESP**: chests, trapped chests, barrels, shulker boxes, ender chests, hoppers, dispensers and droppers (from block entities the server sent).
* **Underground ESP**: storage and structure detections below you that are not sky-exposed, drawn in their own color.
* **Block ESP**: any block ids you list (default: obsidian, ender chest, furnaces, crafting table, anvil, beacon, spawner...). Edit the list in the GUI.
* **Entity ESP**: players, optionally mobs, with distance labels. Off by default.
* **Structure ESP**: a density heuristic. A chunk with many planks/brick/glass/door-type blocks gets one marker, plus "possible entrance" markers on doors in it. It does not do real room or wall detection.
* **Labels**: name, distance and coordinates, each toggleable. Each category has its own hex color.
* **Range**: 16-128 blocks, but you can only ever see chunks inside the server's view distance.

## Performance design

* Detection is cached per chunk and filled by a queue with a ~1.5 ms/tick time budget.
* Sections whose block palette lacks any target block are skipped without iterating their blocks.
* Nearby chunks are re-scanned every ~5 seconds (no mixins); cached hits are checked against the live block before display so broken blocks vanish immediately.
* Targets are rebuilt every 5 ticks into an immutable list, sorted by distance and capped (default 1500).
* All boxes are drawn in a single draw call; labels are limited to the 64 nearest targets.

## Limitations (important)

* **The mod only sees data your client already received.** It cannot see blocks, chests or players in chunks the server has not sent, and it does not read server files, packets beyond normal gameplay, or other players' data.
* **Underground ESP only works for blocks and entities whose information is available to the client.** Servers that hide ores, containers or entities from the client (anti-xray, anti-ESP plugins) will show nothing for hidden things. The server is deliberately withholding that data, and this mod does not try to get around that.
* Storage detection uses block entities. If a server withholds chest block entities until you are close, they will only appear when sent.
* Entity ESP only covers entities the server tracks for you.
* Label placement uses the base FOV option, so it can drift during FOV effects.

## Server rules: check before you use it

ESP is a hack on most multiplayer servers. When this project was written, no official Donut SMP rules page was found.
Search results only showed third-party pages, including an anti-chest-ESP plugin made specifically for
Donut SMP-style servers, which suggests ESP is both unwelcome and actively countered. Servers commonly ban
ESP and X-ray with permanent bans. Read the current rules and ask staff before joining with this mod; use it
only in singleplayer, on your own server, or where ESP is explicitly allowed.

## Easiest way to get a ready-to-use jar (no local setup)

1. Create a new GitHub repository and upload this project (including the `.github` folder).
2. Open the **Actions** tab, run **Build mod jar** (it also runs on every push).
3. Download the `basefinder-jar` artifact, unzip it, and drop `basefinder-1.0.0.jar` into `.minecraft/mods`.

Fabric API is bundled in the jar, so you only need Fabric Loader for 26.1.2. If the build fails, the Actions log
shows the compiler errors; paste them to get them fixed.
