package org.example;

import org.objectweb.asm.*;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.*;
import java.nio.file.Files;

/**
 * Implementation of a bytecode transformation system for method addition using ASM.
 * This class demonstrates the application of bytecode engineering techniques to extend
 * class functionality by adding new methods to existing classes. It showcases how ASM's
 * visitor-based API can be used to modify Java bytecode while preserving the original
 * class structure.
 * The implementation provides a complete pipeline from reading the original class,
 * transforming its structure, and generating a new class file with the additional method.
 *
 * @author Foad Gholinejad
 */
public class ASMAddMethodExample {

    /**
     * Entry point for the bytecode transformation process.
     * This method initializes the transformation pipeline, defining the input and output
     * paths, and coordinating the overall transformation process. It implements error handling
     * to ensure graceful failure in case of exceptions during the transformation process.
     *
     * @param args Command-line arguments (not used in current implementation)
     */
    public static void main(String[] args) {
        // Define input and output paths for the class files
        String inputPath = "C:/Users/salaz/Desktop/ASMExample/build/classes/java/main/org/example/TestClass.class";
        String outputPath = "C:/Users/salaz/Desktop/TestClass_Modified.class";

        System.out.println("🔍 Original class path: " + inputPath);
        System.out.println("📝 Output class path  : " + outputPath);

        try {
            transformClass(inputPath, outputPath);
            System.out.println("\n✅ Successfully transformed class. Output saved to: " + outputPath);
        } catch (Exception e) {
            System.out.println("❌ Error processing class: " + e.getMessage());

            // Improved error logging
            System.err.println("Error details:");
            for (StackTraceElement element : e.getStackTrace()) {
                System.err.println("    at " + element);
            }

            // If there's a cause, log that too
            Throwable cause = e.getCause();
            if (cause != null) {
                System.err.println("Caused by: " + cause.getMessage());
                for (StackTraceElement element : cause.getStackTrace()) {
                    System.err.println("    at " + element);
                }
            }

            System.exit(1);
        }
    }

    /**
     * Performs the bytecode transformation on a class file.
     * This method implements the core transformation pipeline:
     * 1. Read the original class file
     * 2. Creates a modified version that includes the new method
     * 3. Preserves the original class structure while adding the new method
     * 4. Write the transformed class to output location
     * 5. Displays the bytecode before and after transformation for analysis;
     * The transformation carefully maintains the original class structure while adding
     * the new method, ensuring compatibility with existing code.
     *
     * @param inputPath The path to the source class file
     * @param outputPath The path to write the transformed class file
     * @throws Exception If errors occur during the transformation process
     */
    private static void transformClass(String inputPath, String outputPath) throws Exception {
        // Verify the input file exists
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new FileNotFoundException("Input class file not found: " + inputPath);
        }

        // Create temporary directory for class files
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "asm_bytecode_" + System.currentTimeMillis());
        if (!tempDir.mkdirs() && !tempDir.exists()) {
            throw new IOException("Failed to create temporary directory: " + tempDir.getAbsolutePath());
        }

        // Save original class file for disassembly
        File originalClassCopy = new File(tempDir, "Original_TestClass.class");
        Files.copy(inputFile.toPath(), originalClassCopy.toPath());

        // Print original bytecode in human-readable format
        System.out.println("\n===== ORIGINAL CLASS BYTECODE =====");
        disassembleClass(originalClassCopy);

        // ----------------------
        // Measure instrumentation start time (in milliseconds)
        // ----------------------
        long instrumentationStartTime = System.currentTimeMillis();

        // Step 1: Load the original bytecode
        FileInputStream fis = new FileInputStream(inputFile);
        ClassReader classReader = new ClassReader(fis);

        // Step 2: Prepare a ClassWriter that will receive the modified bytecode
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        // Step 3: Apply the transformation - add a new method
        classReader.accept(new AddMethodVisitor(classWriter), 0);

        // Step 4: Get the modified bytecode
        byte[] modifiedBytecode = classWriter.toByteArray();

        // Step 5: Write the modified bytecode to the output file
        // Ensure the output directory exists
        File outputFile = new File(outputPath);
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Failed to create output directory: " + parentDir.getAbsolutePath());
        }

        FileOutputStream fos = new FileOutputStream(outputFile);
        fos.write(modifiedBytecode);
        fos.close();
        fis.close();


        // Save modified class file for disassembly
        File modifiedClassCopy = new File(tempDir, "Modified_TestClass.class");
        Files.copy(outputFile.toPath(), modifiedClassCopy.toPath());

        // ----------------------
        // Measure instrumentation end time (in milliseconds)
        // ----------------------
        long instrumentationEndTime = System.currentTimeMillis();
        long instrumentationTimeMs = instrumentationEndTime - instrumentationStartTime;

        // Print modified bytecode in human-readable format
        System.out.println("\n===== MODIFIED CLASS BYTECODE =====");
        disassembleClass(modifiedClassCopy);
        System.out.println("\n" + "=".repeat(50));


        // Clean up temporary files
        try {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.delete()) {
                        System.err.println("Warning: Failed to delete temporary file: " + file.getAbsolutePath());
                    }
                }
            }
            if (!tempDir.delete()) {
                System.err.println("Warning: Failed to delete temporary directory: " + tempDir.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Warning: Error during cleanup of temporary files: " + e.getMessage());
        }

        // ----------------------
        // Report file sizes and instrumentation duration
        // ----------------------
        File originalFile = new File(inputPath);
        File modifiedFile = new File(outputPath);

        // Print summary
        System.out.println("\n📊 Transformation Summary:");
        System.out.println("   - Added method: 'printNewMessage()' that prints a message");
        System.out.println("   - Time taken: " + instrumentationTimeMs + "ms");
        System.out.println("📦 Original class size: " + originalFile.length() + " bytes");
        System.out.println("📦 Modified class size: " + modifiedFile.length() + " bytes");
    }

    /**
     * Disassembles a Java class file to human-readable bytecode representation.
     * This method uses ASM's TraceClassVisitor to generate a human-readable
     * representation of the bytecode, facilitating analysis and verification of
     * the transformation process.
     *
     * @param classFile The Java class file to disassemble
     * @throws IOException If an I/O error occurs during file reading
     */
    private static void disassembleClass(File classFile) throws IOException {
        FileInputStream fis = new FileInputStream(classFile);
        ClassReader cr = new ClassReader(fis);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        // Use TraceClassVisitor to print the class structure
        TraceClassVisitor tcv = new TraceClassVisitor(null, pw);
        cr.accept(tcv, 0);
        fis.close();

        // Show full bytecode output
        System.out.println(sw);
    }

    /**
     * ClassVisitor implementation that adds a new method to a class.
     * This visitor passes through all original class elements unchanged and
     * adds a new public method named "printNewMessage" that prints a message to the console.
     */
    private static class AddMethodVisitor extends ClassVisitor {

        /**
         * Constructs a new AddMethodVisitor.
         *
         * @param cv The ClassVisitor to delegate to
         */
        public AddMethodVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        /**
         * Called at the end of the class. This is where we add our new method.
         */
        @Override
        public void visitEnd() {
            // Add the new method
            MethodVisitor mv = cv.visitMethod(
                    Opcodes.ACC_PUBLIC,           // Access modifier (public)
                    "printNewMessage",            // Method name
                    "()V",                        // Method descriptor (no params, void return)
                    null,                         // Signature (null for non-generic)
                    null                          // Exceptions (none)
            );

            if (mv != null) {
                mv.visitCode();

                // Get the System.out
                mv.visitFieldInsn(
                        Opcodes.GETSTATIC,
                        "java/lang/System",
                        "out",
                        "Ljava/io/PrintStream;"
                );

                // Push the string to print onto the stack
                mv.visitLdcInsn("This is a new method added by ASM!");

                // Call println method
                mv.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        "java/io/PrintStream",
                        "println",
                        "(Ljava/lang/String;)V",
                        false
                );

                // Return from method
                mv.visitInsn(Opcodes.RETURN);

                // Set max stack and locals (ASM will compute these values)
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }

            // Call the parent method
            super.visitEnd();
        }
    }
}