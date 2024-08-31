package com.cavetale.nbtdump.region;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;

/**
 * A region file with an underlying random access file.
 */
public final class RandomAccessRegionFile implements RegionFileHeader {
    private final int regionX;
    private final int regionZ;
    private final RandomAccessFile raf;

    public RandomAccessRegionFile(final File file, final String mode) throws FileNotFoundException {
        final String filename = file.getName();
        final String[] tokens = filename.split("\\.", 4);
        if (tokens.length != 4) {
            throw new IllegalArgumentException("filename=" + filename);
        }
        if (!"r".equals(tokens[0]) || !"mca".equals(tokens[3])) {
            throw new IllegalArgumentException("filename=" + filename);
        }
        try {
            this.regionX = Integer.parseInt(tokens[1]);
            this.regionZ = Integer.parseInt(tokens[2]);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("filename=" + filename, nfe);
        }
        this.raf = new RandomAccessFile(file, mode);
    }

    public int getChunkLocation(final int x, final int z) {
        final long offset = RegionFileHeader.getChunkLocationOffset(x, z);
        try {
            raf.seek(offset);
            return raf.readInt();
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    @Override
    public boolean hasChunk(final int x, final int z) {
        return getChunkLocation(x, z) != 0;
    }

    @Override
    public int getChunkOffset(final int x, final int z) {
        return RegionFileHeader.locationToOffset(getChunkLocation(x, z));
    }

    @Override
    public int getChunkSectorCount(final int x, final int z) {
        return RegionFileHeader.locationToSectorCount(getChunkLocation(x, z));
    }

    @Override
    public int getChunkTimestamp(final int x, final int z) {
        final long offset = RegionFileHeader.getChunkTimestampOffset(x, z);
        try {
            raf.seek(offset);
            return raf.readInt();
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    public void debug() throws IOException {
        System.out.println("Region " + regionX + " " + regionZ);
        final long length = raf.length();
        System.out.println("Length " + length);
        final int sectorCount = (int) (length / RegionFileHeader.KIB);
        final int modulo = (int) (length % RegionFileHeader.KIB);
        System.out.println("Sectors " + sectorCount + " Mod " + modulo);
        final StringBuilder sectors = new StringBuilder(sectorCount);
        final char gap = '_';
        for (int i = 0; i < sectorCount; i += 1) {
            sectors.append(gap);
        }
        sectors.setCharAt(0, 'i');
        sectors.setCharAt(1, 'i');
        for (int z = 0; z < 32; z += 1) {
            final StringBuilder sb = new StringBuilder();
            for (int x = 0; x < 32; x += 1) {
                final int location = getChunkLocation(x, z);
                sb.append(location != 0 ? "XX" : "__");
                if (location == 0) continue;
                final int chunkOffset = RegionFileHeader.locationToOffset(location);
                final int chunkLength = RegionFileHeader.locationToSectorCount(location);
                for (int i = 0; i < chunkLength; i += 1) {
                    sectors.setCharAt(chunkOffset + i, 'O');
                }
            }
            System.out.println(sb.toString());
        }
        int gaps = 0;
        for (int i = 0; i < sectorCount; i += 1) {
            if (sectors.charAt(i) == gap) gaps += 1;
        }
        System.out.println("Gaps " + gaps);
        final int lineLength = 64;
        for (int i = 0; i < sectorCount; i += lineLength) {
            System.out.println(sectors.substring(i, Math.min(sectorCount, i + lineLength)));
        }
    }
}
