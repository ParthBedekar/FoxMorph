package org.example.db;

import java.io.*;
import java.nio.charset.Charset;

/**
 * Reads memo content from a Visual FoxPro FPT memo file.
 *
 * FPT file layout:
 *   Bytes 0-3  : next free block number (big-endian int) — NOT used for reading
 *   Bytes 2-3  : reserved
 *   Bytes 4-5  : reserved
 *   Bytes 6-7  : block size (big-endian short) — CRITICAL, not always 512
 *   Bytes 8-511: reserved / padding to first block
 *
 * Each memo block:
 *   Bytes 0-3  : block type (big-endian int): 1=text, 0=picture/binary
 *   Bytes 4-7  : content length in bytes (big-endian int)
 *   Bytes 8+   : actual memo content
 *   (padded to next block boundary)
 *
 * The DBF record stores the block number (0-based) directly.
 * Offset = blockNumber * blockSize
 */
public final class FptReader {

    private static final Charset CP1252 = Charset.forName("Cp1252");

    private FptReader() {}

    public static String read(File dbfFile, int blockNumber) {
        if (dbfFile == null || blockNumber <= 0) return null;

        File fptFile = resolveFptFile(dbfFile);
        if (fptFile == null) {
            System.out.println("  FptReader: no FPT file found for " + dbfFile.getName());
            return null;
        }

        try (RandomAccessFile raf = new RandomAccessFile(fptFile, "r")) {

            // Read block size from FPT header bytes 6-7 (big-endian short)
            raf.seek(6);
            int blockSize = ((raf.read() & 0xFF) << 8) | (raf.read() & 0xFF);
            if (blockSize <= 0) blockSize = 512; // fallback

            // Seek to the memo block
            long offset = (long) blockNumber * blockSize;
            if (offset >= raf.length()) {
                System.out.printf("  FptReader: block %d offset %d beyond file length %d%n",
                        blockNumber, offset, raf.length());
                return null;
            }
            raf.seek(offset);

            // Read 8-byte block header
            int blockType     = readIntBE(raf);  // 1 = text
            int contentLength = readIntBE(raf);  // bytes of content that follow

            System.out.printf("  FptReader: block=%d blockSize=%d offset=%d type=%d len=%d%n",
                    blockNumber, blockSize, offset, blockType, contentLength);

            if (contentLength <= 0 || contentLength > 1_000_000) return null;

            byte[] content = new byte[contentLength];
            int read = raf.read(content);
            if (read <= 0) return null;

            String text = new String(content, 0, read, CP1252).trim();
            return text.isEmpty() ? null : text;

        } catch (IOException e) {
            System.out.println("  FptReader error: " + e.getMessage());
            return null;
        }
    }

    private static File resolveFptFile(File dbfFile) {
        String base = dbfFile.getName();
        if (base.contains(".")) base = base.substring(0, base.lastIndexOf('.'));
        File dir = dbfFile.getParentFile();

        // Try exact case first, then uppercase, then lowercase
        for (String ext : new String[]{".fpt", ".FPT", ".Fpt"}) {
            File f = new File(dir, base + ext);
            if (f.exists()) return f;
        }
        // Also try uppercase base name (Windows VFP often uses uppercase filenames)
        for (String ext : new String[]{".fpt", ".FPT"}) {
            File f = new File(dir, base.toUpperCase() + ext);
            if (f.exists()) return f;
        }
        return null;
    }

    private static int readIntBE(RandomAccessFile raf) throws IOException {
        return ((raf.read() & 0xFF) << 24)
                | ((raf.read() & 0xFF) << 16)
                | ((raf.read() & 0xFF) << 8)
                |  (raf.read() & 0xFF);
    }
}