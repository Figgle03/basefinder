package dev.basefinder.detect;

import dev.basefinder.config.BaseFinderConfig;
import dev.basefinder.config.EspCategory;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Scans only chunks the client has already received. Results are cached per chunk, so the world is not
 * rescanned every frame. Work is queued and done under a small per-tick time budget; chunks near the
 * player are refreshed on a slow round-robin, which picks up block changes without any mixins.
 *
 * Everything here runs on the client thread, so no locking is needed.
 */
public final class ChunkScanner {
	private static final long BUDGET_NANOS = 1_500_000L; // about 1.5 ms of scanning per tick
	private static final int RESCAN_TICKS = 100;

	public static final class Entry {
		public final int cx;
		public final int cz;
		public List<Hit> hits = List.of();
		long lastScanTick;

		Entry(int cx, int cz) {
			this.cx = cx;
			this.cz = cz;
		}
	}

	private final Long2ObjectOpenHashMap<Entry> cache = new Long2ObjectOpenHashMap<>();
	private final ArrayDeque<Long> queue = new ArrayDeque<>();
	private final LongOpenHashSet queued = new LongOpenHashSet();
	private long tick;

	public Iterable<Entry> entries() {
		return cache.values();
	}

	public void onChunkLoad(LevelChunk chunk) {
		enqueue(chunk.getPos().toLong());
	}

	public void onChunkUnload(LevelChunk chunk) {
		long key = chunk.getPos().toLong();
		cache.remove(key);
		queued.remove(key);
	}

	public void clear() {
		cache.clear();
		queue.clear();
		queued.clear();
	}

	private void enqueue(long key) {
		if (queued.add(key)) {
			queue.add(key);
		}
	}

	/** Called every client tick. Drains the queue within a time budget and schedules refreshes. */
	public void tick(ClientLevel level, double px, double pz) {
		tick++;
		BaseFinderConfig cfg = BaseFinderConfig.get();

		if (tick % 20 == 0 && queue.isEmpty()) {
			double reach = cfg.range + 16;
			for (Entry e : cache.values()) {
				double dx = e.cx * 16 + 8 - px;
				double dz = e.cz * 16 + 8 - pz;
				if (Math.abs(dx) <= reach && Math.abs(dz) <= reach && tick - e.lastScanTick >= RESCAN_TICKS) {
					enqueue(ChunkPos.asLong(e.cx, e.cz));
				}
			}
		}

		long start = System.nanoTime();
		while (!queue.isEmpty() && System.nanoTime() - start < BUDGET_NANOS) {
			long key = queue.poll();
			queued.remove(key);
			int cx = ChunkPos.getX(key);
			int cz = ChunkPos.getZ(key);
			LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
			if (chunk == null) {
				cache.remove(key);
				continue;
			}
			Entry entry = cache.computeIfAbsent(key, k -> new Entry(cx, cz));
			entry.hits = scan(chunk, cfg);
			entry.lastScanTick = tick;
		}
	}

	private List<Hit> scan(LevelChunk chunk, BaseFinderConfig cfg) {
		List<Hit> out = new ArrayList<>();

		// 1) Storage: block entities the server already sent with the chunk.
		for (BlockEntity be : chunk.getBlockEntities().values()) {
			if (BlockSets.isStorage(be)) {
				Block block = be.getBlockState().getBlock();
				out.add(new Hit(be.getBlockPos(), EspCategory.STORAGE, BlockSets.niceName(block), block));
			}
		}

		// 2) Selected blocks and structure heuristics, skipping whole sections via the palette check.
		Set<Block> wanted = BlockSets.wanted();
		Set<Block> artificial = BlockSets.artificial();
		boolean wantBlocks = !wanted.isEmpty();
		boolean wantStructure = true;

		int artCount = 0;
		long sumX = 0, sumY = 0, sumZ = 0;
		List<Hit> doors = new ArrayList<>();
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int i = 0; i < chunk.getSectionsCount(); i++) {
			LevelChunkSection section = chunk.getSection(i);
			if (section.hasOnlyAir()) {
				continue;
			}
			boolean hasWanted = wantBlocks && section.maybeHas(s -> wanted.contains(s.getBlock()));
			boolean hasArtificial = wantStructure && section.maybeHas(s -> artificial.contains(s.getBlock()));
			if (!hasWanted && !hasArtificial) {
				continue;
			}
			int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						Block block = section.getBlockState(x, y, z).getBlock();
						if (hasWanted && wanted.contains(block)) {
							out.add(new Hit(new BlockPos(baseX + x, baseY + y, baseZ + z),
								EspCategory.BLOCK, BlockSets.niceName(block), block));
						}
						if (hasArtificial && artificial.contains(block)) {
							artCount++;
							sumX += baseX + x;
							sumY += baseY + y;
							sumZ += baseZ + z;
							if (BlockSets.isDoor(block) && doors.size() < 16) {
								doors.add(new Hit(new BlockPos(baseX + x, baseY + y, baseZ + z),
									EspCategory.STRUCTURE, "possible entrance", block));
							}
						}
					}
				}
			}
		}

		// A chunk with lots of man-made blocks is flagged as one structure marker plus any doors found in it.
		// This is a density heuristic, not real room or wall detection.
		if (artCount >= cfg.structureThreshold) {
			pos.set(sumX / artCount, sumY / artCount, sumZ / artCount);
			out.add(new Hit(pos.immutable(), EspCategory.STRUCTURE,
				"structure (" + artCount + " built blocks)", null));
			out.addAll(doors);
		}
		return out;
	}
}
