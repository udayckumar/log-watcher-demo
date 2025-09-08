package com.example.logwatcher.core;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read last N lines by seeking backwards from EOF in blocks.
 * This implementation is geared to be IO-efficient for large files.
 * <p>
 * Note:
 * - It operates on raw bytes and converts bytes to chars using default ISO-8859-1 semantics if using RandomAccessFile.read().
 * - For proper UTF-8 handling across block boundaries a more robust decoder is needed.
 * For many logs using ASCII/UTF-8 without multibyte boundaries, this is adequate.
 */
public class BackwardSeekStrategy implements LineReadingStrategy {

    private static final int DEFAULT_BUFFER_SIZE = 4096; // 4KB chunks

    @Override
    public String[] readLastNLines(String filePath, int n) throws Exception {
        if (n <= 0) {
            return new String[0];
        }

        File file = new File(filePath);
        if (!file.exists() || file.length() == 0) {
            return new String[0];
        }

        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            long fileLength = raf.length();
            long pos = fileLength - 1;

            List<String> lines = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];

            while (pos >= 0 && lines.size() < n) {
                int toRead = (int) Math.min(buffer.length, pos + 1);
                long start = pos - toRead + 1;
                raf.seek(start);
                raf.readFully(buffer, 0, toRead);

                // scan buffer backward
                for (int i = toRead - 1; i >= 0; i--) {
                    byte b = buffer[i];
                    if (b == '\n') {
                        // finalise current collected chars (they are reversed)
                        lines.add(cur.reverse().toString());
                        cur.setLength(0);
                        if (lines.size() >= n) break;
                    } else {
                        cur.append((char) b);
                    }
                }
                pos = start - 1;
            }

            // leftover (start of file without preceding newline)
            if (cur.length() > 0 && lines.size() < n) {
                lines.add(cur.reverse().toString());
            }

            // lines are newest-first; we must return oldest-first
            Collections.reverse(lines);
            return lines.toArray(new String[0]);
        } finally {
            if (raf != null) raf.close();
        }
    }
}
