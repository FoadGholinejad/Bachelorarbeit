package org.example;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Which scenario would you like to run?");
        System.out.println("1) Add Field");
        System.out.println("2) Add Method");
        System.out.println("3) Modify Method");
        System.out.println("4) Security Example");
        System.out.print("Enter 1, 2, 3, or 4: ");

        int choice = scanner.nextInt();

        switch (choice) {
            case 1:
                System.out.println("\nRunning JavassistAddFieldExample...\n");
                JavassistAddFieldExample.main(new String[0]);
                break;
            case 2:
                System.out.println("\nRunning JavassistAddMethodExample...\n");
                JavassistAddMethodExample.main(new String[0]);
                break;
            case 3:
                System.out.println("\nRunning JavassistModifyMethodExample...\n");
                JavassistModifyMethodExample.main(new String[0]);
                break;
            case 4:
                System.out.println("\nRunning JavassistSecurityExample...\n");
                JavassistSecurityExample.main(new String[0]);
                break;
            default:
                System.out.println("Invalid choice. Exiting.");
        }

        scanner.close();
    }
}