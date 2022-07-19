package com.cavetale.nbtdump;

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.conversion.ConverterRegistry;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class Main {
    private Main() { }

    static final class Flags {
        boolean help;
        boolean chunkSpecified;
        int chunkX;
        int chunkZ;
        boolean pretty;
        List<String> paths;
        boolean gzipSpecified;
        boolean gzip;
        boolean endianSpecified;
        boolean littleEndian;
        List<String> gets;
        List<Condition> conditions;
        boolean skipEmpty;
        boolean printChunkCoords;
        String outputPath;
    }

    enum Comparison {
        EQUAL,
        NOT_EQUAL;
    }

    static final class Condition {
        Comparison comparison;
        final String path;
        final Object value;

        Condition(final Comparison comparison, final String path, final Object value) {
            this.comparison = comparison;
            this.path = path;
            this.value = value;
        }
    }

    public static void main(String[] args) throws Exception {
        final Flags flags;
        try {
            flags = parseFlags(Arrays.asList(args).iterator());
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            printHelp(System.err);
            System.exit(1);
            return;
        }
        if (flags.help) {
            printHelp(System.out);
            System.exit(0);
            return;
        }
        printTag(flags);
        System.exit(0);
    }

    static void printTag(Flags flags) throws Exception {
        if (flags.paths != null) {
            for (String path : flags.paths) {
                File file = new File(path);
                if (!file.exists()) {
                    System.err.println("File not found: " + file);
                    continue;
                }
                PrintStream out = flags.outputPath != null
                    ? new PrintStream(new File(flags.outputPath + "/" + file.getName()))
                    : System.out;
                if (path.endsWith(".dat")) {
                    boolean gzip = flags.gzipSpecified ? flags.gzip : true;
                    boolean littleEndian = flags.endianSpecified ? flags.littleEndian : false;
                    Tag tag = NBTIO.readFile(file, gzip, littleEndian);
                    printTag(out, tag, flags);
                } else if (path.endsWith(".mca")) {
                    RandomAccessFile raf = new RandomAccessFile(file, "r");
                    if (raf.length() == 0L) {
                        System.err.println(path + ": File is empty");
                        continue;
                    }
                    if (flags.chunkSpecified) {
                        Tag tag = getAnvilTag(raf, flags.chunkX, flags.chunkZ);
                        printTag(out, tag, flags);
                    } else {
                        for (int z = 0; z < 32; z += 1) {
                            for (int x = 0; x < 32; x += 1) {
                                int location;
                                try {
                                    location = getChunkLocation(raf, x, z);
                                } catch (IOException ioe) {
                                    System.err.println(path + ": Chunk location failed: " + x + " " + z);
                                    continue;
                                }
                                if (location == 0) continue;
                                Tag tag = getAnvilTag(raf, x, z);
                                printTag(out, tag, flags, (flags.printChunkCoords ? x + "," + z + "," : ""));
                            }
                        }
                    }
                } else {
                    Tag tag = NBTIO.readFile(file, flags.gzip, flags.littleEndian);
                    printTag(out, tag, flags);
                }
                if (out != System.out) {
                    out.close();
                }
            }
        } else {
            InputStream inp = System.in;
            if (flags.gzip) inp = new GZIPInputStream(inp);
            boolean littleEndian = flags.endianSpecified ? flags.littleEndian : false;
            Tag tag = NBTIO.readTag(inp, littleEndian);
            printTag(System.out, tag, flags);
        }
        System.exit(0);
    }

    static void printTag(PrintStream out, Tag tag, Flags flags) {
        printTag(out, tag, flags, "");
    }

    static void printTag(PrintStream out, Tag tag, Flags flags, String prefix) {
        if (tag == null) return;
        Object o = ConverterRegistry.convertToValue(tag);
        if (flags.conditions != null) {
            for (Condition condition : flags.conditions) {
                Object value = path(o, condition.path);
                switch (condition.comparison) {
                case EQUAL:
                    if (!Objects.equals(condition.value, value)) return;
                    break;
                case NOT_EQUAL:
                    if (Objects.equals(condition.value, value)) return;
                    break;
                default: throw new IllegalStateException("comparison=" + condition.comparison);
                }
            }
        }
        if (flags.gets != null) {
            if (flags.gets.size() > 1) {
                Map<String, Object> omap = new HashMap<>();
                for (String pat : flags.gets) {
                    Object p = path(o, pat);
                    if (flags.skipEmpty) {
                        if (p == null) continue;
                        if (p instanceof Map map && map.isEmpty()) continue;
                        if (p instanceof List list && list.isEmpty()) continue;
                    }
                    omap.put(pat, p);
                }
                o = omap;
            } else {
                o = path(o, flags.gets.get(0));
            }
        }
        if (flags.skipEmpty) {
            if (o == null) return;
            if (o instanceof Map map && map.isEmpty()) return;
            if (o instanceof List list && list.isEmpty()) return;
        }
        Gson gson = flags.pretty
            ? new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
            : new GsonBuilder().disableHtmlEscaping().create();
        out.println(prefix + gson.toJson(o));
    }

    static Object path(Object current, String path) {
        Iterator<String> iter = Arrays.asList(path.split("\\.")).iterator();
        return path(current, iter);
    }

    static Object path(Object current, Iterator<String> components) {
        if (!components.hasNext()) return current;
        if (current instanceof Map) {
            Map map = (Map) current;
            String it = components.next();
            return path(map.get(it), components);
        } else if (current instanceof List) {
            List list = (List) current;
            String it = components.next();
            int index = Integer.parseInt(it);
            return path(list.get(index), components);
        } else {
            return null;
        }
    }

    static int getChunkLocation(RandomAccessFile raf, int chunkX, int chunkZ) throws IOException {
        long offset = 4L * (long) (chunkX + chunkZ * 32);
        raf.seek(offset);
        int location = raf.readInt();
        return location;
    }

    static int getOffset(int location) {
        return (location & 0xffffff00) >> 8;
    }

    static int getSectorCount(int location) {
        return location & 0xff;
    }

    static Tag getAnvilTag(RandomAccessFile raf, int x, int z) throws Exception {
        int location = getChunkLocation(raf, x, z);
        return getAnvilTag(raf, location);
    }

    static Tag getAnvilTag(RandomAccessFile raf, int location) throws Exception {
        int offset = getOffset(location);
        raf.seek((long) offset * 4096L);
        int length = raf.readInt();
        int compressionType = (int) raf.readByte();
        byte[] compressed = new byte[length];
        int read = 0;
        while (read < length) {
            try {
                int res = raf.read(compressed, read, length - read);
                if (res < 0) return null;
                read += res;
            } catch (Exception e) {
                System.err.println("read=" + read + " length=" + length);
                e.printStackTrace();
                return null;
            }
        }
        InputStream inp = new ByteArrayInputStream(compressed);
        if (compressionType == 1) {
            inp = new GZIPInputStream(inp);
        } else if (compressionType == 2) {
            inp = new InflaterInputStream(inp);
        }
        return NBTIO.readTag(inp);
    }

    static Flags parseFlags(Iterator<String> iter) {
        final Flags flags = new Flags();
        while (iter.hasNext()) {
            String it = iter.next();
            if (it.startsWith("--")) {
                if (it.length() < 4) {
                    throw new IllegalArgumentException("Invalid flag: " + it);
                }
                parseFlag(flags, iter, it.substring(2));
            } else if (it.startsWith("-")) {
                int max = it.length() - 1;
                for (int i = 1; i <= max; i += 1) {
                    parseFlag(flags, i == max ? iter : null, "" + it.charAt(i));
                }
            } else {
                if (flags.paths == null) flags.paths = new ArrayList<>();
                flags.paths.add(it);
                continue;
            }
        }
        return flags;
    }

    static void parseFlag(Flags flags, Iterator<String> iter, String it) {
        switch (it) {
        case "h": case "help":
            flags.help = true;
            break;
        case "c": case "chunk":
            if (flags.chunkSpecified) {
                throw new IllegalArgumentException("Chunk selected more than once");
            }
            flags.chunkSpecified = true;
            flags.chunkX = Integer.parseInt(iter.next());
            flags.chunkZ = Integer.parseInt(iter.next());
            break;
        case "b": case "beauty":
            if (flags.pretty) {
                throw new IllegalArgumentException("Pretty printing set more than once");
            }
            flags.pretty = true;
            break;
        case "z": case "gzip":
            if (flags.gzipSpecified) {
                throw new IllegalArgumentException("Gzip set more than once");
            }
            flags.gzipSpecified = true;
            flags.gzip = true;
            break;
        case "Z": case "nogzip":
            if (flags.gzipSpecified) {
                throw new IllegalArgumentException("Gzip set more than once");
            }
            flags.gzipSpecified = true;
            flags.gzip = false;
            break;
        case "l": case "lendian":
            if (flags.endianSpecified) {
                throw new IllegalArgumentException("Endianness set more than once");
            }
            flags.endianSpecified = true;
            flags.littleEndian = true;
            break;
        case "g": case "get":
            if (flags.gets == null) flags.gets = new ArrayList<>();
            flags.gets.add(iter.next());
            break;
        case "e": case "eq": {
            if (flags.conditions == null) flags.conditions = new ArrayList<>();
            String path = iter.next();
            Gson gson = new Gson();
            Object value = gson.fromJson(iter.next(), Object.class);
            flags.conditions.add(new Condition(Comparison.EQUAL, path, value));
            break;
        }
        case "n": case "neq": {
            if (flags.conditions == null) flags.conditions = new ArrayList<>();
            String path = iter.next();
            Gson gson = new Gson();
            Object value = gson.fromJson(iter.next(), Object.class);
            flags.conditions.add(new Condition(Comparison.NOT_EQUAL, path, value));
            break;
        }
        case "s": case "skipempty":
            flags.skipEmpty = true;
            break;
        case "p": case "printchunkcoords":
            flags.printChunkCoords = true;
            break;
        case "o": case "output":
            if (flags.outputPath != null) {
                throw new IllegalArgumentException("Output path specified more than once");
            }
            flags.outputPath = iter.next();
            break;
        default:
            throw new IllegalArgumentException("Invalid flag: " + it);
        }
    }

    static void printHelp(PrintStream out) {
        out.println("Usage: java -jar NBTDump [OPTION] [FILE]");
        out.println("File Formats");
        out.println("  *.dat\t\tAssume gzip compressed NBT file");
        out.println("  *.mca\t\tAssume anvil file format");
        out.println("Options");
        out.println("  -h, --help\t\t\tPrint help and exit");
        out.println("  -c, --chunk <X> <Z>\t\tSpecify chunk index for anvil files");
        out.println("  -b, --beauty\t\t\tEnable pretty printing");
        out.println("  -z, --gzip\t\t\tEnable gzip decompression");
        out.println("  -Z, --nogzip\t\t\tDisable gzip decompression");
        out.println("  -l, --lendian\t\t\tUse little endian");
        out.println("  -g, --get\t\t\tGet a value (repeatable)");
        out.println("  -e, --eq <PATH> <VALUE>\tOnly print if value at PATH equals VALUE");
        out.println("  -n, --neq <PATH> <VALUE>\tOnly print if value at PATH differs from VALUE");
        out.println("  -s, --skipempty\t\tSkip empty or null tags");
        out.println("  -p, --printchunkcoords\t\tPrint chunk coordinates");
        out.println("  -o, --output\t\tPrint each file to an output folder");
    }
}
