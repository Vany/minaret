package com.minaret;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;

public class ChunkLoaderData {

    private static final String FILE_NAME = "minaret_chunk_loaders.txt";
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
        Path tmp = filePath.resolveSibling(FILE_NAME + ".tmp");
        try (BufferedWriter w = Files.newBufferedWriter(tmp)) {
            for (BlockPos pos : loaders) {
                w.write(pos.getX() + " " + pos.getY() + " " + pos.getZ());
                w.newLine();
            }
        } catch (Exception e) {
            MinaretMod.LOGGER.error("Failed to save chunk loader data", e);
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
            MinaretMod.LOGGER.error("Failed to finalize chunk loader data", e);
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
            MinaretMod.LOGGER.error("Failed to load chunk loader data", e);
        }
    }

    private static ChunkLoaderData instance;

    public static ChunkLoaderData get(ServerLevel level) {
        if (instance == null) {
            Path worldDir = level.getServer().getWorldPath(LevelResource.ROOT);
            instance = new ChunkLoaderData(worldDir.resolve(FILE_NAME));
            instance.load();
        }
        return instance;
    }

    public static void reset() {
        instance = null;
    }
}
