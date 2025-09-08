package com.example.logwatcher.core;

/**
 * Observer contract for receiving new log lines.
 * Implementations will do something with each new line (e.g. send to WebSocket, write to Kafka, etc.).
 */
public interface LogObserver {
    /**
     * Called whenever FileTailService observes a complete new line appended to the log.
     *
     * @param line the text of the new log line (without newline)
     */
    void onNewLogLine(String line);
}
