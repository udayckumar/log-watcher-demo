package com.example.logwatcher.core;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Tails a file using RandomAccessFile. Reads appended bytes and notifies registered observers.
 * Designed to be simple and robust on Java 8.
 * <p>
 * Responsibilities:
 * - Maintain file pointer and only read appended bytes.
 * - Handle truncation / rotation (file length < pointer).
 * - Buffer partial lines (when a newline hasn't yet been written).
 * <p>
 * Limitations:
 * - Uses polling instead of native file watch; poll interval is configurable.
 */
public class FileTailService implements Runnable {

    private final String filePath;
    private final LogNotifier notifier;
    private final LineReadingStrategy lastNReader;
    private final int pollIntervalMs;
    private final Charset charset = StandardCharsets.UTF_8; // change if needed
    // partial buffer for bytes that don't end with newline yet
    private final ByteArrayOutputStream partial = new ByteArrayOutputStream();
    private volatile boolean running = true;
    private Thread worker;
    private long pointer = 0L; // next byte position to read

    public FileTailService(String filePath, LogNotifier notifier, LineReadingStrategy lastNReader, int pollIntervalMs) {
        this.filePath = filePath;
        this.notifier = notifier;
        this.lastNReader = lastNReader;
        this.pollIntervalMs = pollIntervalMs;
    }

    @PostConstruct
    public void start() {
        worker = new Thread(this, "file-tail-thread");
        worker.setDaemon(true);
        worker.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join(1000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * Provide a safe way for others (e.g. WebSocket handler on new client connect) to request last N lines.
     */
    public String[] readLastNLines(int n) throws Exception {
        return lastNReader.readLastNLines(filePath, n);
    }

    @Override
    public void run() {
        RandomAccessFile raf = null;
        try {
            File f = new File(filePath);
            // ensure file exists (if not, wait and retry)
            while (running && !f.exists()) {
                try {
                    Thread.sleep(pollIntervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            raf = new RandomAccessFile(f, "r");
            // Start pointer at end: we only want updates. If you want to send entire file on bootstrap, change here.
            pointer = raf.length();

            while (running) {
                long fileLen = f.length();
                if (fileLen < pointer) {
                    // file truncated or rotated: reopen and reset pointer
                    try {
                        raf.close();
                    } catch (IOException ignored) {
                    }
                    raf = new RandomAccessFile(f, "r");
                    pointer = 0; // start from beginning of new file
                } else if (fileLen > pointer) {
                    // new content available
                    raf.seek(pointer);
                    long bytesToRead = fileLen - pointer;
                    // read in chunks
                    byte[] buffer = new byte[8192];
                    while (bytesToRead > 0) {
                        int toRead = (int) Math.min(buffer.length, bytesToRead);
                        int read = raf.read(buffer, 0, toRead);
                        if (read <= 0) break;
                        processBytes(buffer, read);
                        bytesToRead -= read;
                    }
                    pointer = raf.getFilePointer();
                }
                // sleep
                try {
                    Thread.sleep(pollIntervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Process a block of bytes read from file, split into newline-terminated lines,
     * buffer partial lines across reads, and notify observers for each completed line.
     */
    private void processBytes(byte[] bytes, int len) throws IOException {
        // walk bytes, find newline (LF). We accept \n as delimiter.
        for (int i = 0; i < len; i++) {
            byte b = bytes[i];
            if (b == '\n') {
                // flush partial + bytes up to i as a line
                String line;
                if (partial.size() == 0) {
                    // just decode the bytes up to i
                    line = new String(bytes, 0, i + 1, charset); // includes newline
                    // remove trailing newline:
                    line = trimTrailingNewline(line);
                } else {
                    // include previously buffered bytes then bytes[0..i]
                    partial.write(bytes, 0, i + 1); // includes newline
                    line = partial.toString(charset);
                    line = trimTrailingNewline(line);
                    partial.reset();
                }
                // If we had extra bytes after i in this chunk, we must handle them.
                // But since we're iterating per byte, we handle sequentially; the next bytes will be processed by continuing loop.
                notifier.notifyObservers(line);
                // Shift remaining bytes in this call: to keep code simple, create a small copy of remaining bytes and continue processing them
                int remaining = len - (i + 1);
                if (remaining > 0) {
                    byte[] rem = new byte[remaining];
                    System.arraycopy(bytes, i + 1, rem, 0, remaining);
                    // recursively process remaining bytes
                    processBytes(rem, remaining);
                }
                return; // important: we've delegated remaining processing above so break out
            }
        }

        // no newline found in this block: buffer it
        partial.write(bytes, 0, len);
        // ensure partial doesn't grow unbounded: if it becomes > some large threshold, flush to avoid OOM (policy decision)
        if (partial.size() > 1024 * 1024) { // 1MB
            // very long line without newline: force flush as a line to avoid infinite growth
            String forced = partial.toString(charset);
            notifier.notifyObservers(forced);
            partial.reset();
        }
    }

    // remove trailing newline or carriage return
    private String trimTrailingNewline(String s) {
        if (s == null) return s;
        int end = s.length();
        while (end > 0) {
            char c = s.charAt(end - 1);
            if (c == '\n' || c == '\r') end--;
            else break;
        }
        return s.substring(0, end);
    }
}
