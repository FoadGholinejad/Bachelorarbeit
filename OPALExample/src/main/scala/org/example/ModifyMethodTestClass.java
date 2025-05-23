package org.example;

import java.util.Random;

public class ModifyMethodTestClass {
    public int processRandomNumber() {
        Random random = new Random();
        int number = random.nextInt(1000); // Generate a random number between 0 and 999
        System.out.println("Generated number: " + number);
        return number * 2; // Original multiplication by 2
    }

    public static void main(String[] args) {
        ModifyMethodTestClass testClass = new ModifyMethodTestClass();
        int result = testClass.processRandomNumber();
        System.out.println("Result of processRandomNumber: " + result);
    }
}