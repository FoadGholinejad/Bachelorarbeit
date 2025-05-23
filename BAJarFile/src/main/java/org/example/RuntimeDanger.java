package org.example;

import java.io.File;

public class RuntimeDanger {
    public static void doExec() {
        System.out.println("Attempting to run a (formerly) dangerous command...");

        try {
            // Create a folder on the Desktop (example path):
            File folder = new File("C:\\Users\\salaz\\Desktop\\complicatedFolder");
            boolean created = folder.mkdir();

            if (created) {
                System.out.println("Folder created successfully!");
            } else {
                System.out.println("Failed to create folder or folder already exists.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}