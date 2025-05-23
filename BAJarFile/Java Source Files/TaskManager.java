package org.example;
import java.util.ArrayList;
import java.util.List;

public class TaskManager {

    private final List<Thread> workers = new ArrayList<>();

    /**
     * Prepare a certain number of workers, e.g. 3 or 5.
     */
    public TaskManager(int numWorkers) {
        for (int i = 1; i <= numWorkers; i++) {
            workers.add(new Thread(new Worker(i)));
        }
    }

    /**
     * Start all worker threads (good target for instrumentation:
     * logging start time, etc. or even hooking each thread run).
     */
    public void startAll() {
        System.out.println("Starting all workers...");
        for (Thread t : workers) {
            t.start();
        }
    }

    /**
     * Wait for all workers to finish
     * (also a candidate for instrumentation).
     */
    public void waitUntilDone() {
        for (Thread t : workers) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("All workers have finished.");
    }
}