package com.cavetale.nbtdump.region;

public interface RegionFileHeader {
    long KIB = 4096L;

    boolean hasChunk(int x, int z);

    int getChunkOffset(int x, int z);

    int getChunkSectorCount(int x, int z);

    int getChunkTimestamp(int x, int z);

    static void assertChunkCoordRange(final int x, final int z) {
        if (x < 0 || x > 31) throw new IllegalArgumentException("x=" + x);
        if (z < 0 || z > 31) throw new IllegalArgumentException("z=" + z);
    }

    static long getChunkLocationOffset(final int x, final int z) {
        assertChunkCoordRange(x, z);
        return 4L * (long) (x + z * 32);
    }

    static long getChunkTimestampOffset(final int x, final int z) {
        assertChunkCoordRange(x, z);
        return KIB + 4L * (long) (x + z * 32);
    }

    static int locationToOffset(final int location) {
        return (location & 0xffffff00) >> 8;
    }

    static int locationToSectorCount(final int location) {
        return location & 0xff;
    }

    static long timestampToMillis(final int timestamp) {
        return 1000L * (long) timestamp;
    }
}
