package com.cavetale.nbtdump;

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.conversion.ConverterRegistry;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Map;

public final class WorldBorderCutter {
    @SuppressWarnings("unchecked")
    public static void cutWorldBorder(File worldFolder, int padding, boolean simulate) throws Exception {
        final File levelDatFile = new File(worldFolder, "level.dat");
        if (!levelDatFile.exists()) {
            System.err.println("Level dat not found: " + levelDatFile);
            return;
        }
        final Map<String, Object> level = (Map<String, Object>) ConverterRegistry.convertToValue(NBTIO.readFile(levelDatFile, true, false));
        final Map<String, Object> levelData = (Map<String, Object>) level.get("Data");
        final double centerX = (Double) levelData.get("BorderCenterX");
        final double centerZ = (Double) levelData.get("BorderCenterZ");
        final double size = (Double) levelData.get("BorderSize");
        System.out.println("Center " + centerX + " " + centerZ);
        System.out.println("Size " + size);
        System.out.println("Simulate " + simulate);
        if (size >= 5.9E7) {
            System.err.println("World Border too large or not set");
            return;
        }
        final double sizeh = size * 0.5;
        final int west = (int) Math.floor(centerX - sizeh);
        final int east = (int) Math.ceil(centerX + sizeh);
        final int north = (int) Math.floor(centerZ - sizeh);
        final int south = (int) Math.ceil(centerZ + sizeh);
        final int westChunk = (west >> 4) - padding;
        final int eastChunk = (east >> 4) + padding;
        final int northChunk = (north >> 4) - padding;
        final int southChunk = (south >> 4) + padding;
        final int westRegion = westChunk >> 5;
        final int eastRegion = eastChunk >> 5;
        final int northRegion = northChunk >> 5;
        final int southRegion = southChunk >> 5;
        File regionFolder = null;
        for (String path : List.of("region", "DIM1/region", "DIM-1/region")) {
            final File folder = new File(worldFolder, path);
            if (folder.exists()) {
                regionFolder = folder;
                break;
            }
        }
        if (regionFolder == null) {
            System.err.println("Region folder not found: " + worldFolder);
            return;
        }
        System.out.println("Using region folder: " + regionFolder);
        int deletedRegionFiles = 0;
        int erasedChunks = 0;
        for (File regionFile : regionFolder.listFiles()) {
            final String name = regionFile.getName();
            if (!name.startsWith("r.") || !name.endsWith(".mca")) continue;
            final String[] tokens = name.split("\\.", 4);
            final int regionX = Integer.parseInt(tokens[1]);
            final int regionZ = Integer.parseInt(tokens[2]);
            if (regionX > westRegion && regionX < eastRegion && regionZ > northRegion && regionZ < southRegion) {
                continue;
            }
            if (regionX < westRegion || regionX > eastRegion || regionZ < northRegion || regionZ > southRegion) {
                System.out.println(regionFile.getName() + ": Deleting Region");
                if (!simulate) {
                    regionFile.delete();
                }
                deletedRegionFiles += 1;
                continue;
            }
            try (RandomAccessFile raf = new RandomAccessFile(regionFile, "rw")) {
                if (raf.length() == 0L) {
                    System.err.println(regionFile + ": File is empty");
                    continue;
                }
                for (int z = 0; z < 32; z += 1) {
                    for (int x = 0; x < 32; x += 1) {
                        final int chunkX = (regionX << 5) + x;
                        final int chunkZ = (regionZ << 5) + z;
                        if (chunkX >= westChunk && chunkX <= eastChunk && chunkZ >= northChunk && chunkZ <= southChunk) {
                            continue;
                        }
                        final int location = Main.getChunkLocation(raf, x, z);
                        if (location == 0) continue;
                        System.out.println(regionFile.getName() + ": Erasing Chunk " + chunkX + " " + chunkZ);
                        if (!simulate) {
                            final long offset = 4L * (long) (x + z * 32);
                            raf.seek(offset);
                            raf.writeInt(0);
                            raf.writeInt(0);
                        }
                        erasedChunks += 1;
                    }
                }
            }
        }
        System.out.println("Done. Deleted " + deletedRegionFiles + " region files and erased " + erasedChunks + " chunks");
    }

    private WorldBorderCutter() { }
}
