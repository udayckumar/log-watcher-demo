package com.example.logwatcher.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe notifier that implements the Observer pattern.
 * It maintains a list of registered observers and broadcasts new lines.
 * <p>
 * CopyOnWriteArrayList is used because broadcast frequency is higher than register/unregister typically,
 * and it gives stable iteration without locking.
 */
public class LogNotifier {
    private final List<LogObserver> observers = new CopyOnWriteArrayList<>();

    /**
     * Register an observer to receive new lines.
     */
    public void addObserver(LogObserver o) {
        if (o != null) observers.add(o);
    }

    /**
     * Unregister an observer.
     */
    public void removeObserver(LogObserver o) {
        if (o != null) observers.remove(o);
    }

    /**
     * Broadcast a new line to all observers.
     */
    public void notifyObservers(String line) {
        for (LogObserver o : observers) {
            try {
                o.onNewLogLine(line);
            } catch (Throwable t) {
                // Catch to keep other observers unaffected by a single failing observer.
                t.printStackTrace();
            }
        }
    }
}
