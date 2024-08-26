package com.cavetale.nbtdump;

import com.github.steveice10.opennbt.conversion.ConverterRegistry;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Map;

public final class WorldBorderGuesser {
    @SuppressWarnings("unchecked")
    public static void guessWorldBorder(File worldFolder) throws Exception {
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
        System.err.println("Using region folder: " + regionFolder);
        int west = Integer.MAX_VALUE;
        int east = Integer.MIN_VALUE;
        int north = Integer.MAX_VALUE;
        int south = Integer.MIN_VALUE;
        Vec2i westmost = new Vec2i(west, 0);
        Vec2i eastmost = new Vec2i(east, 0);
        Vec2i northmost = new Vec2i(0, north);
        Vec2i southmost = new Vec2i(0, south);
        for (File regionFile : regionFolder.listFiles()) {
            final String name = regionFile.getName();
            if (!name.startsWith("r.") || !name.endsWith(".mca")) continue;
            final String[] tokens = name.split("\\.", 4);
            final int regionX = Integer.parseInt(tokens[1]);
            final int regionZ = Integer.parseInt(tokens[2]);
            try (RandomAccessFile raf = new RandomAccessFile(regionFile, "r")) {
                if (raf.length() == 0L) {
                    System.err.println(regionFile + ": File is empty");
                    continue;
                }
                for (int z = 0; z < 32; z += 1) {
                    for (int x = 0; x < 32; x += 1) {
                        final int location = Main.getChunkLocation(raf, x, z);
                        if (location == 0) continue;
                        boolean chunkIsEmpty = true;
                        final Map<String, Object> tag = (Map<String, Object>) ConverterRegistry.convertToValue(Main.getAnvilTag(raf, x, z));
                        final List<Object> sections = (List<Object>) tag.get("sections");
                        for (int i = 0; i < sections.size(); i += 1) {
                            final Map<String, Object> section = (Map<String, Object>) sections.get(i);
                            final Map<String, Object> blockStates = (Map<String, Object>) section.get("block_states");
                            final List<Object> palette = (List<Object>) blockStates.get("palette");
                            if (palette.size() > 1) {
                                chunkIsEmpty = false;
                                break;
                            }
                            final Map<String, Object> paletteEntry = (Map<String, Object>) palette.get(0);
                            if (!"minecraft:air".equals(paletteEntry.get("Name"))) {
                                chunkIsEmpty = false;
                                break;
                            }
                        }
                        if (chunkIsEmpty) {
                            continue;
                        }
                        final int chunkX = (regionX << 5) + x;
                        final int chunkZ = (regionZ << 5) + z;
                        if (chunkX < west) {
                            west = chunkX;
                            westmost = new Vec2i(chunkX, chunkZ);
                        }
                        if (chunkX > east) {
                            east = chunkX;
                            eastmost = new Vec2i(chunkX, chunkZ);
                        }
                        if (chunkZ < north) {
                            north = chunkZ;
                            northmost = new Vec2i(chunkX, chunkZ);
                        }
                        if (chunkZ > south) {
                            south = chunkZ;
                            southmost = new Vec2i(chunkX, chunkZ);
                        }
                    }
                }
            }
        }
        west = west << 4;
        east = (east << 4) + 15;
        north = north << 4;
        south = (south << 4) + 15;
        int centerX = (west + east) / 2;
        int centerZ = (north + south) / 2;
        int sizeX = east - west + 1;
        int sizeZ = south - north + 1;
        System.out.println("Guessing World Border");
        System.out.println(" x: " + west + " " + east);
        System.out.println(" z: " + north + " " + south);
        System.out.println(" center: " + centerX + " " + centerZ);
        System.out.println(" size: " + sizeX + " " + sizeZ);
        System.out.println("Extremes");
        System.out.println(" west: " + westmost + " " + westmost.toBlock());
        System.out.println(" east: " + eastmost + " " + eastmost.toBlock());
        System.out.println(" north: " + northmost + " " + northmost.toBlock());
        System.out.println(" south: " + southmost + " " + southmost.toBlock());
    }

    private record Vec2i(int x, int z) {
        @Override public String toString() {
            return "(" + x + "," + z + ")";
        }

        public Vec2i toBlock() {
            return new Vec2i(x() << 4, z() << 4);
        }
    }

    private WorldBorderGuesser() { }
}
