package org.example;

public class Worker implements Runnable {

    private final int workerId;

    public Worker(int workerId) {
        this.workerId = workerId;
    }

    @Override
    public void run() {
        doWork();
    }

    /**
     * The method that does some "heavy" work
     * (perfect to measure start/end time via instrumentation).
     */
    public void doWork() {
        System.out.println("Worker " + workerId + " started work.");

        // Example time-consuming or CPU-bound operation: compute factorial
        // for demonstration, pick a small number so it won't truly block:
        long factorial = 1;
        for (int i = 1; i <= 10; i++) {
            factorial *= i;
            try {
                Thread.sleep(50);  // simulate some additional workload
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Worker " + workerId + " computed factorial(10) = " + factorial);
        System.out.println("Worker " + workerId + " finished work.");
    }
}