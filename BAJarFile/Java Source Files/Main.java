package org.example;

public class Main {
    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        // 1) Create and run TaskManager with workers.
        TaskManager manager = new TaskManager(5);
        manager.startAll();
        manager.waitUntilDone();

        long midTime = System.currentTimeMillis();

        // 2) Attempt a "dangerous" call.
        try {
            RuntimeDanger.doExec();
        } catch (SecurityException e) {
            // Print the exception but don't rethrow it
            System.out.println("Exception in thread \"main\" " + e);
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis();

        // Print execution times
        System.out.println("\n=== Execution Times ===");
        System.out.printf("Workers execution time: %d ms%n", 
            (midTime - startTime));
        System.out.printf("Security check execution time: %d ms%n", 
            (endTime - midTime));
    }
}