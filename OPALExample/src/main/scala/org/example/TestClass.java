package org.example;

public class TestClass {
    private String message = "Hello, World!";

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static void main(String[] args) {
        // Create an instance of TestClass
        TestClass testClass = new TestClass();

        // Print the initial value of the message
        System.out.println("Initial message: " + testClass.getMessage());
    }
}
