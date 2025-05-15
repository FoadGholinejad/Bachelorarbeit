package org.example;

import org.objectweb.asm.*;
import org.objectweb.asm.util.TraceClassVisitor;
import java.io.*;
import java.util.Enumeration;
import java.util.jar.*;

/**
 * Implementation of a bytecode transformation system for JAR security enhancement using ASM.
 * This class demonstrates the application of bytecode engineering techniques to enhance
 * security by intercepting File.mkdir() calls and replacing them with security exceptions.
 * It showcases how ASM's visitor-based API can be used to modify Java bytecode within JAR files
 * while preserving the original structure.
 * The implementation provides a complete pipeline from reading the original JAR,
 * transforming class files that contain mkdir() calls, and generating a new secured JAR file.
 *
 * @author Foad Gholinejad
 */
public class ASMSecurityExample {

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

                        // Check if this class contains mkdir() calls
                        ClassReader cr = new ClassReader(entryData);
                        MkdirDetector detector = new MkdirDetector();
                        cr.accept(detector, 0);

                        if (detector.containsMkdir) {
                            modifiedClasses++;
                            totalMkdirCalls += detector.mkdirCount;
                            System.out.println("🔧 Found target class: " + entryName);

                            // Print bytecode headers and content
                            System.out.println("\n===== ORIGINAL CLASS BYTECODE =====");
                            printClassBytecode(entryData);

                            // Transform the class
                            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
                            ClassVisitor cv = new SecurityTransformVisitor(cw);
                            cr.accept(cv, 0);
                            byte[] transformedData = cw.toByteArray();

                            System.out.println("\n===== MODIFIED CLASS BYTECODE =====");
                            printClassBytecode(transformedData);

                            System.out.println("\n==================================================");

                            // Write the transformed class to the output JAR
                            writeJarEntry(jarOut, entry, transformedData);
                            continue;
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
     * Prints the bytecode of a class in a human-readable format using ASM's TraceClassVisitor.
     *
     * @param classBytes The bytecode of the class to print
     */
    private static void printClassBytecode(byte[] classBytes) {
        if (classBytes == null) return;

        try {
            ClassReader cr = new ClassReader(classBytes);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            TraceClassVisitor tcv = new TraceClassVisitor(pw);
            cr.accept(tcv, 0);
            System.out.println(sw);
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

    /**
     * Class visitor that detects mkdir() calls in a class.
     * This visitor scans class methods for calls to java.io.File.mkdir()
     * and tracks the number of such calls found.
     */
    private static class MkdirDetector extends ClassVisitor {
        /** Flag indicating whether the class contains mkdir() calls */
        boolean containsMkdir = false;

        /** Counter for the number of mkdir() calls found */
        int mkdirCount = 0;

        /**
         * Constructs a new MkdirDetector.
         */
        public MkdirDetector() {
            super(Opcodes.ASM9);
        }

        /**
         * Called for each method in the class. Returns a method visitor
         * that will detect mkdir() calls.
         *
         * @param access The method's access flags
         * @param name The method name
         * @param descriptor The method descriptor
         * @param signature The method's signature
         * @param exceptions The method's exception table
         * @return A MethodVisitor that will detect mkdir() calls
         */
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {
                /**
                 * Called for each method invocation in the method.
                 * Detects calls to java.io.File.mkdir().
                 */
                @Override
                public void visitMethodInsn(int opcode, String owner, String name,
                                            String descriptor, boolean isInterface) {
                    if (owner.equals("java/io/File") && name.equals("mkdir")) {
                        containsMkdir = true;
                        mkdirCount++;
                    }
                }
            };
        }
    }

    /**
     * Class visitor that transforms mkdir() calls to security exceptions.
     * This visitor passes through all original class elements unchanged but
     * transforms methods to replace mkdir() calls with security exceptions.
     */
    private static class SecurityTransformVisitor extends ClassVisitor {
        /**
         * Constructs a new SecurityTransformVisitor.
         *
         * @param cv The ClassVisitor to delegate to
         */
        public SecurityTransformVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        /**
         * Called for each method in the class. Returns a method visitor
         * that will transform mkdir() calls.
         *
         * @param access The method's access flags
         * @param name The method name
         * @param descriptor The method descriptor
         * @param signature The method's signature
         * @param exceptions The method's exception table
         * @return A MethodVisitor that will transform mkdir() calls
         */
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new SecurityMethodVisitor(mv);
        }
    }

    /**
     * Method visitor that transforms mkdir() calls to security exceptions.
     * This visitor intercepts calls to java.io.File.mkdir() and replaces them
     * with code that throws a SecurityException.
     */
    private static class SecurityMethodVisitor extends MethodVisitor {
        /**
         * Constructs a new SecurityMethodVisitor.
         *
         * @param mv The MethodVisitor to delegate to
         */
        public SecurityMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        /**
         * Called for each method invocation in the method.
         * Transforms calls to java.io.File.mkdir() to throw security exceptions.
         *
         * @param opcode The opcode of the type instruction to be visited
         * @param owner The internal name of the method's owner class
         * @param name The method's name
         * @param descriptor The method's descriptor
         * @param isInterface Whether the method's owner class is an interface
         */
        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                    String descriptor, boolean isInterface) {
            // Check for File.mkdir() calls
            if (owner.equals("java/io/File") && name.equals("mkdir")) {
                // Insert security check instead of the mkdir call
                insertSecurityCheck();

                // Skip the original mkdir call
                // Note: We're not calling super.visitMethodInsn here
                return;
            }

            // Pass through all another method calls unchanged
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        /**
         * Insert bytecode for security warning and exception.
         * This method generates bytecode that prints a warning message and
         * throws a SecurityException.
         */
        private void insertSecurityCheck() {
            // Print warning message
            visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out",
                    "Ljava/io/PrintStream;");
            visitLdcInsn("[SECURITY WARNING] mkdir operation blocked!");
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream",
                    "println", "(Ljava/lang/String;)V", false);

            // Create and throw SecurityException
            visitTypeInsn(Opcodes.NEW, "java/lang/SecurityException");
            visitInsn(Opcodes.DUP);
            visitLdcInsn("Directory creation is not allowed by security policy");
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/SecurityException",
                    "<init>", "(Ljava/lang/String;)V", false);
            visitInsn(Opcodes.ATHROW);
        }
    }
}