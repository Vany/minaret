package com.minaret;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChunkLoaderData {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String LEGACY_FILE_NAME = "minaret_chunk_loaders.txt";
    private final Set<BlockPos> loaders = new HashSet<>();
    private final Path filePath;

    private ChunkLoaderData(Path filePath) {
        this.filePath = filePath;
    }

    public void add(BlockPos pos) {
        if (loaders.add(pos.immutable())) {
            save();
        }
    }

    public void remove(BlockPos pos) {
        if (loaders.remove(pos)) {
            save();
        }
    }

    public void forceAll(ServerLevel level) {
        for (BlockPos pos : loaders) {
            ChunkPos cp = new ChunkPos(pos);
            level.setChunkForced(cp.x, cp.z, true);
        }
    }

    public void unforceAll(ServerLevel level) {
        for (BlockPos pos : loaders) {
            ChunkPos cp = new ChunkPos(pos);
            level.setChunkForced(cp.x, cp.z, false);
        }
    }

    private void save() {
        Path tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        try (BufferedWriter w = Files.newBufferedWriter(tmp)) {
            for (BlockPos pos : loaders) {
                w.write(pos.getX() + " " + pos.getY() + " " + pos.getZ());
                w.newLine();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save chunk loader data", e);
            return;
        }
        try {
            Files.move(
                tmp,
                filePath,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
        } catch (Exception e) {
            LOGGER.error("Failed to finalize chunk loader data", e);
        }
    }

    private void load() {
        loaders.clear();
        if (!Files.exists(filePath)) return;
        try (BufferedReader r = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(" ");
                loaders.add(
                    new BlockPos(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])
                    )
                );
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load chunk loader data", e);
        }
    }

    // ── Per-dimension instance management ────────────────────────────────

    private static final Map<ResourceKey<Level>, ChunkLoaderData> instances =
        new ConcurrentHashMap<>();

    public static ChunkLoaderData get(ServerLevel level) {
        return instances.computeIfAbsent(level.dimension(), key -> {
            String dimId = Compat.resourceKeyId(key);
            String fileName = "minaret_chunk_loaders_" + dimId + ".txt";
            Path worldDir = level.getServer().getWorldPath(LevelResource.ROOT);
            Path path = worldDir.resolve(fileName);

            // One-time migration from legacy single-file format
            if (!Files.exists(path) && key.equals(Level.OVERWORLD)) {
                Path legacy = worldDir.resolve(LEGACY_FILE_NAME);
                if (Files.exists(legacy)) {
                    try {
                        Files.copy(legacy, path);
                        LOGGER.info("Migrated legacy chunk loader data to {}", fileName);
                    } catch (Exception e) {
                        LOGGER.error("Failed to migrate legacy chunk loader data", e);
                    }
                }
            }

            ChunkLoaderData data = new ChunkLoaderData(path);
            data.load();
            return data;
        });
    }

    public static void reset() {
        instances.clear();
    }
}
