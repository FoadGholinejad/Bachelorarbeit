package org.example;

import javassist.*;
import javassist.bytecode.*;
import javassist.expr.*;
import java.io.*;
import java.util.Enumeration;
import java.util.jar.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of a bytecode transformation system for JAR security enhancement using Javassist.
 * This class demonstrates the application of bytecode engineering techniques to enhance
 * security by intercepting File.mkdir() calls and replacing them with security exceptions.
 * It showcases how Javassist can be used to modify Java bytecode within JAR files
 * while preserving the original structure.
 * The implementation provides a complete pipeline from reading the original JAR,
 * transforming class files that contain mkdir() calls, and generating a new secured JAR file.
 *
 * @author Foad Gholinejad
 */
public class JavassistSecurityExample {

    private static final Logger LOGGER = Logger.getLogger(JavassistSecurityExample.class.getName());
    private static final String TARGET_METHOD = "mkdir";
    private static final String TARGET_CLASS = "java.io.File";
    private static final String SECURITY_WARNING = "[SECURITY WARNING] mkdir operation blocked!";
    private static final String SECURITY_EXCEPTION_MESSAGE = "Directory creation is not allowed by security policy";

    /**
     * Entry point for the bytecode transformation process.
     * This method initializes the transformation pipeline, defining the input and output
     * paths, and coordinating the overall transformation process. It implements error handling
     * to ensure graceful failure in case of exceptions during the transformation process.
     *
     * @param args Command-line arguments (not used in current implementation)
     */
    public static void main(String[] args) {
        // Define input and output JAR files
        File inputJar = new File("C:/Users/salaz/Desktop/myApp.jar");
        File outputJar = new File(inputJar.getParentFile(),
                inputJar.getName().replace(".jar", "_modified.jar"));

        System.out.println("🔍 Analyzing JAR file: " + inputJar.getAbsolutePath());
        System.out.println("📝 Output will be written to: " + outputJar.getAbsolutePath());

        try {
            transformJar(inputJar, outputJar);
            System.out.println("\n✅ Successfully transformed JAR. Output saved to: " + outputJar.getAbsolutePath());
        } catch (Exception e) {
            System.out.println("❌ Error processing JAR: " + e.getMessage());

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
     * Transforms a JAR file by modifying classes that contain mkdir() calls.
     * This method implements the core transformation pipeline:
     * 1. Read the original JAR file
     * 2. Identify classes containing File.mkdir() calls
     * 3. Transform those classes to throw security exceptions instead
     * 4. Write the transformed JAR to output location
     * 5. Provide detailed statistics about the transformation process
     * The transformation carefully maintains the original JAR structure while
     * enhancing security by preventing directory creation operations.
     *
     * @param inputJar The source JAR file
     * @param outputJar The destination JAR file
     * @throws IOException If an I/O error occurs during the transformation process
     */
    private static void transformJar(File inputJar, File outputJar) throws IOException {
        // Verify the input file exists
        if (!inputJar.exists()) {
            throw new FileNotFoundException("Input JAR file not found: " + inputJar.getAbsolutePath());
        }

        // Statistics counters
        int totalClasses = 0;
        int modifiedClasses = 0;
        int totalMkdirCalls = 0;

        // ----------------------
        // Measure instrumentation start time (in milliseconds)
        // ----------------------
        long instrumentationStartTime = System.currentTimeMillis();

        // Initialize Javassist ClassPool
        ClassPool pool = ClassPool.getDefault();

        // Create JarFile and JarOutputStream
        try (JarFile jarFile = new JarFile(inputJar);
             JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(outputJar))) {

            // Process each entry in the JAR
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // Read the entry data
                try (InputStream is = jarFile.getInputStream(entry)) {
                    byte[] entryData = readAllBytes(is);

                    // If this is a class file, check if it needs transformation
                    if (entryName.endsWith(".class")) {
                        totalClasses++;

                        // Check if this class contains mkdir() calls and transform if needed
                        try {
                            MkdirTransformationResult result = transformClassIfNeeded(pool, entryData, entryName);

                            if (result.wasTransformed) {
                                modifiedClasses++;
                                totalMkdirCalls += result.mkdirCallsFound;
                                System.out.println("🔧 Found target class: " + entryName);

                                // Print original bytecode
                                System.out.println("\n===== ORIGINAL CLASS BYTECODE =====");
                                printClassBytecode(result.originalClass);

                                // Print modified bytecode
                                System.out.println("\n===== MODIFIED CLASS BYTECODE =====");
                                // Create a new CtClass from the transformed bytes to print its bytecode
                                CtClass modifiedClass = pool.makeClass(new ByteArrayInputStream(result.transformedBytes));
                                printClassBytecode(modifiedClass);
                                modifiedClass.detach();

                                System.out.println("\n==================================================");

                                // Write the transformed class to the output JAR
                                writeJarEntry(jarOut, entry, result.transformedBytes);
                                continue;
                            }
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Error transforming class: " + entryName, e);
                            // If transformation fails, write the original bytes
                        }
                    }

                    // For non-class files or classes without mkdir(), copy unchanged
                    writeJarEntry(jarOut, entry, entryData);
                }
            }
        }

        // ----------------------
        // Measure instrumentation end time (in milliseconds)
        // ----------------------
        long instrumentationEndTime = System.currentTimeMillis();
        long instrumentationTimeMs = instrumentationEndTime - instrumentationStartTime;

        // Print summary
        System.out.println("\n📊 Transformation Summary:");
        System.out.println("   - Total classes processed: " + totalClasses);
        System.out.println("   - Classes modified: " + modifiedClasses);
        System.out.println("   - mkdir() calls replaced: " + totalMkdirCalls);
        System.out.println("   - Time taken: " + instrumentationTimeMs + "ms");
        System.out.println("📦 Original JAR size: " + inputJar.length() + " bytes");
        System.out.println("📦 Modified JAR size: " + outputJar.length() + " bytes");
    }

    /**
     * Represents the result of a class transformation attempt.
     */
    private static class MkdirTransformationResult {
        public final boolean wasTransformed;
        public final int mkdirCallsFound;
        public final byte[] transformedBytes;
        public final CtClass originalClass; // Store the original class for bytecode printing

        public MkdirTransformationResult(boolean wasTransformed, int mkdirCallsFound,
                                         byte[] transformedBytes, CtClass originalClass) {
            this.wasTransformed = wasTransformed;
            this.mkdirCallsFound = mkdirCallsFound;
            this.transformedBytes = transformedBytes;
            this.originalClass = originalClass;
        }
    }

    /**
     * Transforms a class if it contains mkdir() calls.
     * This method uses Javassist to analyze and modify the class bytecode.
     *
     * @param pool The Javassist ClassPool
     * @param classBytes The original class bytecode
     * @param className The name of the class (for logging)
     * @return A result object containing transformation information
     * @throws Exception If an error occurs during transformation
     */
    private static MkdirTransformationResult transformClassIfNeeded(ClassPool pool, byte[] classBytes, String className)
            throws Exception {

        // Load the class into Javassist
        CtClass ctClass = pool.makeClass(new ByteArrayInputStream(classBytes));

        // First pass: detect mkdir() calls
        MkdirDetector detector = new MkdirDetector();
        int mkdirCalls = detector.detectMkdirCalls(ctClass);

        // If no mkdir() calls found, return without transformation
        if (mkdirCalls == 0) {
            ctClass.detach();
            return new MkdirTransformationResult(false, 0, null, null);
        }

        // Store a copy of the original class for bytecode printing
        CtClass originalClass = pool.makeClass(new ByteArrayInputStream(classBytes));

        // Second pass: transform the class
        transformMkdirCalls(ctClass);

        // Get the transformed bytecode
        byte[] transformedBytes = ctClass.toBytecode();

        // Clean up the transformed class
        ctClass.detach();

        return new MkdirTransformationResult(true, mkdirCalls, transformedBytes, originalClass);
    }

    /**
     * Helper class to detect mkdir() calls in a class.
     */
    private static class MkdirDetector {
        private int mkdirCallCount = 0;

        /**
         * Scans a class for mkdir() calls.
         *
         * @param ctClass The class to scan
         * @return The number of mkdir() calls found
         * @throws CannotCompileException If an error occurs during scanning
         */
        public int detectMkdirCalls(CtClass ctClass) throws CannotCompileException {
            mkdirCallCount = 0;

            // Scan all methods in the class
            for (CtMethod method : ctClass.getDeclaredMethods()) {
                method.instrument(new ExprEditor() {
                    @Override
                    public void edit(MethodCall m) {
                        if (m.getClassName().equals(TARGET_CLASS) && m.getMethodName().equals(TARGET_METHOD)) {
                            mkdirCallCount++;
                        }
                    }
                });
            }

            return mkdirCallCount;
        }
    }

    /**
     * Transforms mkdir() calls in a class to throw security exceptions.
     *
     * @param ctClass The class to transform
     * @throws CannotCompileException If an error occurs during transformation
     */
    private static void transformMkdirCalls(CtClass ctClass) throws CannotCompileException {
        // Process all methods in the class
        for (CtMethod method : ctClass.getDeclaredMethods()) {
            method.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall m) throws CannotCompileException {
                    if (m.getClassName().equals(TARGET_CLASS) && m.getMethodName().equals(TARGET_METHOD)) {
                        // Replace mkdir() call with security exception
                        // We need to handle the return value properly
                        m.replace(
                                "{ " +
                                        "System.out.println(\"" + SECURITY_WARNING + "\"); " +
                                        "$_ = false; " +  // Set return value to false before throwing exception
                                        "throw new SecurityException(\"" + SECURITY_EXCEPTION_MESSAGE + "\"); " +
                                        "}"
                        );
                    }
                }
            });
        }
    }

    /**
     * Prints the bytecode of a class in a human-readable format.
     *
     * @param ctClass The class whose bytecode should be printed
     */
    private static void printClassBytecode(CtClass ctClass) {
        if (ctClass == null) return;

        try {
            // Print class information
            System.out.println("// class " + ctClass.getName());
            System.out.println("// access flags: 0x" + Integer.toHexString(ctClass.getModifiers()));

            // Print methods
            for (CtMethod method : ctClass.getDeclaredMethods()) {
                System.out.println("\n// Method: " + method.getLongName());
                System.out.println("// access flags: 0x" + Integer.toHexString(method.getModifiers()));

                // Print method bytecode
                CodeAttribute codeAttr = method.getMethodInfo().getCodeAttribute();
                if (codeAttr != null) {
                    CodeIterator iterator = codeAttr.iterator();
                    while (iterator.hasNext()) {
                        int pos = iterator.next();
                        int op = iterator.byteAt(pos);
                        String opcode = Mnemonic.OPCODE[op];
                        System.out.printf("  %4d: %s\n", pos, opcode);

                        // Print operands for some common opcodes
                        switch (op) {
                            case Opcode.INVOKEVIRTUAL:
                            case Opcode.INVOKESPECIAL:
                            case Opcode.INVOKESTATIC:
                            case Opcode.INVOKEINTERFACE:
                                int index = iterator.u16bitAt(pos + 1);
                                try {
                                    ConstPool constPool = codeAttr.getConstPool();
                                    String className = constPool.getMethodrefClassName(index);
                                    String methodName = constPool.getMethodrefName(index);
                                    String methodType = constPool.getMethodrefType(index);
                                    System.out.printf("        %s.%s:%s\n", className, methodName, methodType);
                                } catch (Exception e) {
                                    // Ignore errors in printing method references
                                }
                                break;
                        }
                    }
                    System.out.println("    MAXSTACK = " + codeAttr.getMaxStack());
                    System.out.println("    MAXLOCALS = " + codeAttr.getMaxLocals());
                } else {
                    System.out.println("    // No code attribute (abstract or native method)");
                }
            }
        } catch (Exception e) {
            System.out.println("Error printing bytecode: " + e.getMessage());
        }
    }

    /**
     * Writes an entry to the JAR output stream.
     * This method creates a new JAR entry with the same name as the original
     * and writes the provided data to the JAR output stream.
     *
     * @param jarOut The JAR output stream
     * @param originalEntry The original JAR entry
     * @param data The data to write
     * @throws IOException If an I/O error occurs
     */
    private static void writeJarEntry(JarOutputStream jarOut, JarEntry originalEntry,
                                      byte[] data) throws IOException {
        // Create a new entry (don't reuse the original to avoid compressed size issues)
        JarEntry newEntry = new JarEntry(originalEntry.getName());
        jarOut.putNextEntry(newEntry);
        jarOut.write(data);
        jarOut.closeEntry();
    }

    /**
     * Reads all bytes from an input stream.
     * This utility method efficiently reads the entire contents of an input stream
     * into a byte array, handling streams of any size.
     *
     * @param is The input stream to read
     * @return A byte array containing all bytes from the input stream
     * @throws IOException If an I/O error occurs
     */
    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        return buffer.toByteArray();
    }
}