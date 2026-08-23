package org.bxteam.quark.platform;

/**
 * A scheduled task that can be cancelled.
 */
public interface TaskHandle {
    /**
     * Cancels the task. Does nothing if it already finished or was cancelled.
     */
    void cancel();

    /**
     * @return true if {@link #cancel()} was called
     */
    boolean isCancelled();
}
