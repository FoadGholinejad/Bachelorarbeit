package org.example;

import javassist.*;
import javassist.bytecode.*;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.Mnemonic;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Implementation of a bytecode transformation system for method addition using Javassist.
 * This class demonstrates how to use Javassist to modify Java bytecode at runtime by adding
 * a new method to an existing class. It provides functionality to measure performance metrics,
 * compare file sizes, and display bytecode before and after transformation.
 *
 * @author Foad Gholinejad
 */
public class JavassistAddMethodExample {

    private static final Logger LOGGER = Logger.getLogger(JavassistAddMethodExample.class.getName());
    private static final String METHOD_NAME = "printNewMessage";

    /**
     * Entry point for the bytecode transformation process.
     * Coordinates the loading, transformation, and analysis of the class file.
     *
     * @param args Command-line arguments (not used in current implementation)
     */
    public static void main(String[] args) {

        // Paths for the input and output class files
        String inputPath = "C:/Users/salaz/Desktop/JavaAssistExample/build/classes/java/main/org/example/TestClass.class";
        String outputPath = "C:/Users/salaz/Desktop/TestClass_Modified.class";

        System.out.println("🔍 Original class path: " + inputPath);
        System.out.println("📝 Output class path  : " + outputPath);

        try {
            // Create temporary directory for class files if needed
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "javassist_bytecode_" + System.currentTimeMillis());
            if (!tempDir.exists() && !tempDir.mkdirs()) {
                LOGGER.warning("Failed to create temporary directory: " + tempDir.getAbsolutePath());
            }

            // Load the original class into CtClass
            ClassPool pool = ClassPool.getDefault();
            CtClass ctClass = loadClassFromFile(pool, inputPath);

            // Print original bytecode in human-readable format
            System.out.println("\n===== ORIGINAL CLASS BYTECODE =====");
            printClassBytecode(ctClass);

            // ──────────────────────────────────────────────────────────────────────────
            // Measure ONLY the instrumentation time (adding a method + writing)
            // ──────────────────────────────────────────────────────────────────────────
            long instrumentationStartTime = System.currentTimeMillis();

            // Unconditionally add a new method named "printNewMessage"
            addNewMethod(ctClass);

            // Write the modified class
            writeModifiedClass(ctClass, outputPath);

            // Unfreeze (just in case we need further modifications later)
            ctClass.defrost();

            long instrumentationEndTime = System.currentTimeMillis();
            long instrumentationTimeMs = instrumentationEndTime - instrumentationStartTime;

            // Reload the MODIFIED class (to print its bytecode after changes)
            CtClass modifiedClassForBytecode = loadClassFromFile(pool, outputPath);

            // Print modified bytecode in human-readable format
            System.out.println("\n===== MODIFIED CLASS BYTECODE =====");
            printClassBytecode(modifiedClassForBytecode);

            System.out.println("\n" + "=".repeat(50));

            // ──────────────────────────────────────────────────────────────────────────
            // Print transformation summary
            // ──────────────────────────────────────────────────────────────────────────
            File originalFile = new File(inputPath);
            File modifiedFile = new File(outputPath);

            System.out.println("\n📊 Transformation Summary:");
            System.out.println("   - Added method: '" + METHOD_NAME + "' returning void");
            System.out.println("   - Time taken: " + instrumentationTimeMs + "ms");
            System.out.println("📦 Original class size: " + originalFile.length() + " bytes");
            System.out.println("📦 Modified class size: " + modifiedFile.length() + " bytes");

            System.out.println("\n✅ Successfully transformed class. Output saved to: " + outputPath);

            // Clean up temporary files
            cleanupTempFiles(tempDir);

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
        }
    }

    /**
     * Loads a class from a file into a CtClass object.
     *
     * @param pool The ClassPool to use
     * @param filePath The path to the class file
     * @return The loaded CtClass object
     * @throws IOException If an I/O error occurs
     */
    private static CtClass loadClassFromFile(ClassPool pool, String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            return pool.makeClass(fis);
        }
    }

    /**
     * Unconditionally adds a new method "printNewMessage" to the CtClass.
     * No check is performed to see if it already exists.
     *
     * @param ctClass The class to which the method will be added
     * @throws CannotCompileException If the method cannot be compiled
     */
    private static void addNewMethod(CtClass ctClass) throws CannotCompileException {
        // Create and add a new method without checking its prior existence
        CtMethod newMethod = CtNewMethod.make(
                "public void " + METHOD_NAME + "() { " +
                        "    System.out.println(\"This is a new method added by JavaAssist!\");" +
                        "}",
                ctClass
        );
        ctClass.addMethod(newMethod);
    }

    /**
     * Prints the bytecode of the class in a format similar to javap.
     * This provides a human-readable representation of the class structure.
     *
     * @param ctClass The class whose bytecode should be printed
     */
    private static void printClassBytecode(CtClass ctClass) {
        ClassFile classFile = ctClass.getClassFile();

        // Print class header
        int accessFlags = classFile.getAccessFlags();
        StringBuilder accessStr = new StringBuilder();
        if ((accessFlags & AccessFlag.PUBLIC) != 0) accessStr.append("public ");
        if ((accessFlags & AccessFlag.FINAL) != 0) accessStr.append("final ");
        if ((accessFlags & AccessFlag.ABSTRACT) != 0) accessStr.append("abstract ");

        System.out.println("// class version " + classFile.getMajorVersion() + "." + classFile.getMinorVersion() + " (" + classFile.getMajorVersion() + ")");
        System.out.println("// access flags 0x" + Integer.toHexString(accessFlags));
        System.out.println(accessStr + "class " + ctClass.getSimpleName() + " {");
        System.out.println();

        // Print fields
        for (CtField field : ctClass.getDeclaredFields()) {
            try {
                int fieldAccess = field.getModifiers();
                StringBuilder fieldAccessStr = getStringBuilder(fieldAccess);

                System.out.println("  // access flags 0x" + Integer.toHexString(fieldAccess));
                System.out.println("  " + fieldAccessStr + field.getType().getName() + " " + field.getName());
                System.out.println();
            } catch (NotFoundException e) {
                System.out.println("  // Error getting field type: " + e.getMessage());
            }
        }

        // Print methods
        for (CtMethod method : ctClass.getDeclaredMethods()) {
            try {
                int methodAccess = method.getModifiers();
                StringBuilder methodAccessStr = getStringBuilder(methodAccess);
                if ((methodAccess & Modifier.SYNCHRONIZED) != 0) methodAccessStr.append("synchronized ");

                // Get method signature in javap format
                String descriptor = method.getSignature();
                String returnType = getReturnType(descriptor);
                String params = getParameterTypes(descriptor);

                System.out.println("  // access flags 0x" + Integer.toHexString(methodAccess));
                System.out.println("  " + methodAccessStr + returnType + " " + method.getName() + "(" + params + ")");

                // Print method bytecode
                MethodInfo methodInfo = method.getMethodInfo();
                CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
                if (codeAttribute != null) {
                    CodeIterator ci = codeAttribute.iterator();
                    while (ci.hasNext()) {
                        try {
                            int index = ci.next();
                            int opcode = ci.byteAt(index);
                            String mnemonic = Mnemonic.OPCODE[opcode];
                            System.out.println("    " + mnemonic);
                        } catch (BadBytecode e) {
                            System.out.println("    // Error reading bytecode: " + e.getMessage());
                        }
                    }

                    System.out.println("    MAXSTACK = " + codeAttribute.getMaxStack());
                    System.out.println("    MAXLOCALS = " + codeAttribute.getMaxLocals());
                }
                System.out.println();
            } catch (Exception e) {
                System.out.println("  // Error processing method: " + e.getMessage());
            }
        }

        System.out.println("}");
    }

    private static StringBuilder getStringBuilder(int fieldAccess) {
        StringBuilder fieldAccessStr = new StringBuilder();
        if ((fieldAccess & Modifier.PUBLIC) != 0) fieldAccessStr.append("public ");
        if ((fieldAccess & Modifier.PRIVATE) != 0) fieldAccessStr.append("private ");
        if ((fieldAccess & Modifier.PROTECTED) != 0) fieldAccessStr.append("protected ");
        if ((fieldAccess & Modifier.STATIC) != 0) fieldAccessStr.append("static ");
        if ((fieldAccess & Modifier.FINAL) != 0) fieldAccessStr.append("final ");
        return fieldAccessStr;
    }

    /**
     * Extracts the return type from a method descriptor.
     *
     * @param descriptor The method descriptor
     * @return The return type as a string
     */
    private static String getReturnType(String descriptor) {
        int endParams = descriptor.lastIndexOf(')');
        if (endParams >= 0) {
            String returnDesc = descriptor.substring(endParams + 1);
            return descriptorToTypeName(returnDesc);
        }
        return "void";
    }

    /**
     * Extracts parameter types from a method descriptor.
     *
     * @param descriptor The method descriptor
     * @return A comma-separated list of parameter types
     */
    private static String getParameterTypes(String descriptor) {
        int startParams = descriptor.indexOf('(');
        int endParams = descriptor.lastIndexOf(')');
        if (startParams >= 0 && endParams > startParams) {
            String paramsDesc = descriptor.substring(startParams + 1, endParams);
            StringBuilder result = new StringBuilder();
            int i = 0;
            while (i < paramsDesc.length()) {
                char c = paramsDesc.charAt(i);
                int end = i;
                if (c == 'L') {
                    end = paramsDesc.indexOf(';', i) + 1;
                } else if (c == '[') {
                    while (end < paramsDesc.length() && paramsDesc.charAt(end) == '[') {
                        end++;
                    }
                    if (end < paramsDesc.length() && paramsDesc.charAt(end) == 'L') {
                        end = paramsDesc.indexOf(';', end) + 1;
                    } else {
                        end++;
                    }
                } else {
                    end++;
                }

                if (result.length() > 0) {
                    result.append(", ");
                }
                result.append(descriptorToTypeName(paramsDesc.substring(i, end)));
                i = end;
            }
            return result.toString();
        }
        return "";
    }

    /**
     * Converts a JVM type descriptor to a human-readable type name.
     *
     * @param descriptor The type descriptor
     * @return The human-readable type name
     */
    private static String descriptorToTypeName(String descriptor) {
        switch (descriptor) {
            case "V":
                return "void";
            case "Z":
                return "boolean";
            case "B":
                return "byte";
            case "C":
                return "char";
            case "S":
                return "short";
            case "I":
                return "int";
            case "J":
                return "long";
            case "F":
                return "float";
            case "D":
                return "double";
        }

        if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
            return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
        }

        if (descriptor.startsWith("[")) {
            return descriptorToTypeName(descriptor.substring(1)) + "[]";
        }

        return descriptor; // Unknown type
    }

    /**
     * Writes the modified class to the specified output path.
     *
     * @param ctClass The modified class to write
     * @param outputPath The path where the modified class should be written
     * @throws IOException If an I/O error occurs
     * @throws CannotCompileException If the class cannot be compiled
     */
    private static void writeModifiedClass(CtClass ctClass, String outputPath)
            throws IOException, CannotCompileException {
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            fos.write(ctClass.toBytecode());
        }
    }

    /**
     * Cleans up temporary files created during the transformation process.
     *
     * @param tempDir The temporary directory to clean
     */
    private static void cleanupTempFiles(File tempDir) {
        if (tempDir != null && tempDir.exists()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.delete()) {
                        LOGGER.fine("Failed to delete temporary file: " + file.getAbsolutePath());
                    }
                }
            }
            if (!tempDir.delete()) {
                LOGGER.fine("Failed to delete temporary directory: " + tempDir.getAbsolutePath());
            }
        }
    }
}