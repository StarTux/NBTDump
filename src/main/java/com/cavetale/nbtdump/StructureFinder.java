package com.cavetale.nbtdump;

import com.github.steveice10.opennbt.conversion.ConverterRegistry;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class StructureFinder {
    @SuppressWarnings("unchecked")
    static void findStructures(File worldFolder) throws Exception {
        File databaseFile = new File(worldFolder, "structures.db");
        Class.forName("org.sqlite.JDBC");
        int regionFileCount = 0;
        int structureCount = 0;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS `structures` ("
                                  + " `id` INTEGER PRIMARY KEY,"
                                  + " `type` VARCHAR(255) NOT NULL,"
                                  + " `chunk_x` INTEGER NOT NULL,"
                                  + " `chunk_z` INTEGER NOT NULL,"
                                  + " `ax` INTEGER NOT NULL,"
                                  + " `ay` INTEGER NOT NULL,"
                                  + " `az` INTEGER NOT NULL,"
                                  + " `bx` INTEGER NOT NULL,"
                                  + " `by` INTEGER NOT NULL,"
                                  + " `bz` INTEGER NOT NULL,"
                                  + " `json` TEXT NOT NULL"
                                  + ")");
                statement.execute("CREATE TABLE IF NOT EXISTS `struct_refs` ("
                                  + " `id` INTEGER PRIMARY KEY,"
                                  + " `structure_id` INTEGER NOT NULL,"
                                  + " `region_x` INTEGER NOT NULL,"
                                  + " `region_z` INTEGER NOT NULL,"
                                  + " UNIQUE(`region_x`, `region_z`, `structure_id`)"
                                  + ")");
            }
            Gson gson = new GsonBuilder().disableHtmlEscaping().create();
            String sqlStructure = "INSERT INTO `structures`"
                + " (`type`, `chunk_x`, `chunk_z`, `ax`, `ay`, `az`, `bx`, `by`, `bz`, `json`)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmtStructure = connection.prepareStatement(sqlStructure, Statement.RETURN_GENERATED_KEYS);
                 Statement stmtReference = connection.createStatement()) {
                for (String path : List.of("region", "DIM1/region", "DIM-1/region")) {
                    File folder = new File(worldFolder, path);
                    if (!folder.exists()) continue;
                    for (File file : folder.listFiles()) {
                        if (!file.getName().startsWith("r.") || !file.getName().endsWith(".mca")) continue;
                        System.err.println("Region File " + file);
                        RandomAccessFile raf = new RandomAccessFile(file, "r");
                        if (raf.length() == 0L) {
                            System.err.println(file + ": File is empty");
                            continue;
                        }
                        regionFileCount += 1;
                        for (int z = 0; z < 32; z += 1) {
                            for (int x = 0; x < 32; x += 1) {
                                Tag tag;
                                try {
                                    tag = Main.getAnvilTag(raf, x, z);
                                } catch (IOException ioe) {
                                    continue;
                                }
                                if (tag == null) continue;
                                Map<String, Object> chunkTag = (Map<String, Object>) ConverterRegistry.convertToValue(tag);
                                Map<String, Object> structuresMap = (Map<String, Object>) chunkTag.get("structures");
                                if (structuresMap == null) continue;
                                Map<String, Object> starts = (Map<String, Object>) structuresMap.get("starts");
                                if (starts != null) {
                                    for (Map.Entry<String, Object> entry : starts.entrySet()) {
                                        Map<String, Object> structureMap = (Map<String, Object>) entry.getValue();
                                        String json = gson.toJson(structureMap);
                                        String key = (String) structureMap.get("id");
                                        if (key == null || key.equals("INVALID")) continue;
                                        if (!key.equals(entry.getKey())) {
                                            throw new IllegalStateException(file + ": " + key + " != " + entry.getKey());
                                        }
                                        int ax = Integer.MAX_VALUE;
                                        int ay = Integer.MAX_VALUE;
                                        int az = Integer.MAX_VALUE;
                                        int bx = Integer.MIN_VALUE;
                                        int by = Integer.MIN_VALUE;
                                        int bz = Integer.MIN_VALUE;
                                        for (Map<String, Object> childMap : (List<Map<String, Object>>) structureMap.get("Children")) {
                                            int[] boundingBox = (int[]) childMap.get("BB");
                                            if (boundingBox == null) continue;
                                            ax = Math.min(ax, boundingBox[0]);
                                            ay = Math.min(ay, boundingBox[1]);
                                            az = Math.min(az, boundingBox[2]);
                                            bx = Math.max(bx, boundingBox[3]);
                                            by = Math.max(by, boundingBox[4]);
                                            bz = Math.max(bz, boundingBox[5]);
                                        }
                                        int chunkX = ((Number) structureMap.get("ChunkX")).intValue();
                                        int chunkZ = ((Number) structureMap.get("ChunkZ")).intValue();
                                        stmtStructure.setString(1, key);
                                        stmtStructure.setInt(2, chunkX);
                                        stmtStructure.setInt(3, chunkZ);
                                        stmtStructure.setInt(4, ax);
                                        stmtStructure.setInt(5, ay);
                                        stmtStructure.setInt(6, az);
                                        stmtStructure.setInt(7, bx);
                                        stmtStructure.setInt(8, by);
                                        stmtStructure.setInt(9, bz);
                                        stmtStructure.setString(10, json);
                                        stmtStructure.executeUpdate();
                                        final int structureId;
                                        try (ResultSet generatedKeys = stmtStructure.getGeneratedKeys()) {
                                            if (!generatedKeys.next()) throw new IllegalStateException("No id: " + json);
                                            structureId = generatedKeys.getInt(1);
                                        }
                                        // Reference
                                        final int cax = ax >> 9;
                                        final int caz = az >> 9;
                                        final int cbx = bx >> 9;
                                        final int cbz = bz >> 9;
                                        List<String> values = new ArrayList<>();
                                        for (int cz = caz; cz <= cbz; cz += 1) {
                                            for (int cx = cax; cx <= cbx; cx += 1) {
                                                values.add("(" + structureId + "," + cx + "," + cz + ")");
                                            }
                                        }
                                        String sqlReference = "INSERT INTO `struct_refs`"
                                            + " (`structure_id`, `region_x`, `region_z`)"
                                            + " VALUES " + String.join(", ", values);
                                        stmtReference.execute(sqlReference);
                                        structureCount += 1;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (regionFileCount == 0) {
            System.err.println("No region files found!");
            System.exit(1);
        }
        if (structureCount == 0) {
            System.err.println("No structures found!");
            System.exit(1);
        }
    }

    private StructureFinder() { }
}