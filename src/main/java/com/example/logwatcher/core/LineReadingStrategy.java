package com.example.logwatcher.core;

/**
 * Strategy interface for reading the last N lines from a file efficiently.
 * Allows swapping different implementations (e.g. backward-seek, memory cache, external index).
 */
public interface LineReadingStrategy {
    /**
     * Read up to n last lines from filePath (oldest-first).
     *
     * @param filePath path to file
     * @param n        number of lines to retrieve
     * @return array of lines (length <= n), oldest-first (so display naturally)
     * @throws Exception on any IO/decoding error
     */
    String[] readLastNLines(String filePath, int n) throws Exception;
}
