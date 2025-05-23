package org.example;

public class Main {
    public static void main(String[] args) {

        // 1) Create and run TaskManager with workers.
            TaskManager manager = new TaskManager(5);
            manager.startAll();
            manager.waitUntilDone();

            // 2) Attempt a "dangerous" call.
            //    Perfect place for instrumentation to intercept.
            RuntimeDanger.doExec();

    }
}