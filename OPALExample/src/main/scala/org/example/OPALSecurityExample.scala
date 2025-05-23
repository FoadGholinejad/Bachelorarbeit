package org.example

import org.opalj.ba._
import org.opalj.br._
import org.opalj.br.instructions._
import org.opalj.bc.Assembler

import java.io._
import java.util.jar.{JarEntry, JarFile, JarOutputStream}
import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.sys.process._

/**
 * Implementation of a static bytecode transformation system for security policy enforcement.
 *
 * This class demonstrates the application of bytecode engineering techniques to enforce
 * security policies by transforming potentially dangerous operations in Java bytecode.
 * Specifically, it targets file system operations (mkdir) and replaces them with
 * security exceptions and appropriate warnings.
 *
 * The implementation leverages the OPAL framework's embedded Domain-Specific Language (eDSL)
 * for bytecode manipulation, providing a high-level declarative approach to bytecode
 * transformation rather than low-level instruction manipulation.
 *
 * @author Foad Gholinejad
 */
object OPALSecurityExample {

  /**
   * Entry point for the bytecode transformation process.
   *
   * This method initializes the transformation pipeline, handling command-line arguments
   * and coordinating the overall transformation process. It implements error handling
   * to ensure graceful failure in case of exceptions during the transformation process.
   *
   * @param args Command-line arguments (not used in current implementation)
   */
  def main(args: Array[String]): Unit = {
    // Define input and output JAR files with hardcoded paths
    val inputJar = new File("C:/Users/salaz/Desktop/MyApp.jar")
    val outputJar = new File(inputJar.getParent, inputJar.getName.stripSuffix(".jar") + "_modified.jar")

    println(s"🔍 Analyzing JAR file: ${inputJar.getAbsolutePath}")
    println(s"📝 Output will be written to: ${outputJar.getAbsolutePath}")

    try {
      transformJar(inputJar, outputJar)
      println(s"\n✅ Successfully transformed JAR. Output saved to: ${outputJar.getAbsolutePath}")
    } catch {
      case e: Exception =>
        println(s"❌ Error processing JAR: ${e.getMessage}")
        e.printStackTrace()
        System.exit(1)
    }
  }

  /**
   * Performs the bytecode transformation on a JAR file.
   *
   * This method implements the core transformation pipeline:
   * 1. Read input JAR file
   * 2. Processes each entry, identifying class files for potential transformation
   * 3. Applies targeted transformations to security-sensitive classes
   * 4. Write the transformed classes back to a new JAR file
   *
   * The transformation preserves the original JAR structure, modifying only
   * the targeted classes while leaving other resources intact.
   *
   * @param inputJar The source JAR file to be transformed
   * @param outputJar The destination JAR file to write transformed bytecode
   * @throws FileNotFoundException If the input JAR file does not exist
   * @throws IOException If errors occur during JAR processing
   */
  private def transformJar(inputJar: File, outputJar: File): Unit = {
    if (!inputJar.exists()) {
      throw new FileNotFoundException(s"Input JAR not found: ${inputJar.getAbsolutePath}")
    }

    val startTime = System.currentTimeMillis()

    // Load the JAR file
    val jarFile = new JarFile(inputJar)
    val jarOut = new JarOutputStream(new FileOutputStream(outputJar))

    // Create temporary directory for class files
    val tempDir = new File(System.getProperty("java.io.tmpdir"), "opal_bytecode_" + System.currentTimeMillis())
    tempDir.mkdirs()

    // Keep track of statistics
    var totalClasses = 0
    var modifiedClasses = 0
    var totalMkdirCalls = 0

    // Process each entry in the JAR
    for (entry <- jarFile.entries().asScala) {
      val entryName = entry.getName
      val entryStream = jarFile.getInputStream(entry)
      val entryBytes = entryStream.readAllBytes()

      if (entryName.endsWith(".class")) {
        totalClasses += 1

        // Check if this is the class we want to modify
        if (entryName.contains("RuntimeDanger")) {
          modifiedClasses += 1
          println(s"🔧 Found target class: $entryName")
          totalMkdirCalls += 1

          // Save original class file for disassembly
          val originalClassFile = new File(tempDir, "Original_" + entryName.replace('/', '_'))
          new FileOutputStream(originalClassFile).write(entryBytes)

          // Print original bytecode in human-readable format
          println("\n===== ORIGINAL CLASS BYTECODE =====")
          disassembleClass(originalClassFile)

          // Apply the security transformation using OPAL's eDSL
          val modifiedClass = CLASS(
            accessModifiers = PUBLIC SUPER,
            thisType = "org/example/RuntimeDanger",
            methods = METHODS(
              METHOD(
                PUBLIC STATIC,
                "doExec",
                "()V",
                CODE(
                  // Preserve the original message output
                  GETSTATIC("java/lang/System", "out", "Ljava/io/PrintStream;"),
                  LDC(ConstantString("Attempting to run a (formerly) dangerous command...")),
                  INVOKEVIRTUAL("java/io/PrintStream", "println", "(Ljava/lang/String;)V"),

                  // Replace mkdir with security warning
                  GETSTATIC("java/lang/System", "out", "Ljava/io/PrintStream;"),
                  LDC(ConstantString("[SECURITY WARNING] mkdir operation blocked!")),
                  INVOKEVIRTUAL("java/io/PrintStream", "println", "(Ljava/lang/String;)V"),

                  // Throw security exception
                  NEW("java/lang/SecurityException"),
                  DUP,
                  LDC(ConstantString("Directory creation is not allowed by security policy")),
                  INVOKESPECIAL("java/lang/SecurityException", isInterface = false, "<init>", "(Ljava/lang/String;)V"),
                  ATHROW
                )
              )
            )
          )

          // Convert to bytecode
          val (daClassFile, _) = modifiedClass.toDA()
          val newBytes = Assembler(daClassFile)

          // Save modified class file for disassembly
          val modifiedClassFile = new File(tempDir, "Modified_" + entryName.replace('/', '_'))
          new FileOutputStream(modifiedClassFile).write(newBytes)

          // Print modified bytecode in human-readable format
          println("\n===== MODIFIED CLASS BYTECODE =====")
          disassembleClass(modifiedClassFile)
          println("\n" + "=".repeat(50))

          // Write to output JAR
          jarOut.putNextEntry(new JarEntry(entryName))
          jarOut.write(newBytes)
          jarOut.closeEntry()
        } else {
          // Not the target class, copy unchanged
          jarOut.putNextEntry(new JarEntry(entryName))
          jarOut.write(entryBytes)
          jarOut.closeEntry()
        }
      } else {
        // Not a class file, copy unchanged
        jarOut.putNextEntry(new JarEntry(entryName))
        jarOut.write(entryBytes)
        jarOut.closeEntry()
      }
    }

    // Close resources
    jarOut.close()
    jarFile.close()

    // Clean up temporary files
    try {
      for (file <- tempDir.listFiles()) {
        file.delete()
      }
      tempDir.delete()
    } catch {
      case _: Exception => // Ignore cleanup errors
    }

    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime

    // Print summary
    println("\n📊 Transformation Summary:")
    println(s"   - Total classes processed: $totalClasses")
    println(s"   - Classes modified: $modifiedClasses")
    println(s"   - mkdir() calls replaced: $totalMkdirCalls")
    println(s"   - Time taken: ${duration}ms")
    println(s"📦 Original JAR size: ${inputJar.length()} bytes")
    println(s"📦 Modified JAR size: ${outputJar.length()} bytes")
  }

  /**
   * Disassembles a Java class file to human-readable bytecode representation.
   *
   * This method uses the standard Java disassembler tool (javap) to generate
   * a human-readable representation of the bytecode, facilitating analysis and
   * verification of the transformation process. The output is filtered to focus
   * on the most relevant aspects of the bytecode.
   *
   * The method implements robust error handling and fallback mechanisms in case
   * the javap tool is not available in the execution environment.
   *
   * @param classFile The Java class file to disassemble
   */
  private def disassembleClass(classFile: File): Unit = {
    try {
      // Check if javap is available
      val javapPath = findJavap()

      if (javapPath.nonEmpty) {
        // Use javap to disassemble the class
        val cmd = s"$javapPath -v -p ${classFile.getAbsolutePath}"
        val output = cmd.!!

        // Filter and print the most relevant parts
        val lines = output.split("\n")
        val filteredOutput = lines.filter(line =>
          line.trim.nonEmpty &&
            (line.contains("public") ||
              line.contains("private") ||
              line.contains("protected") ||
              line.contains("static") ||
              line.contains("Code:") ||
              line.contains("line") ||
              line.contains("invoke") ||
              line.contains("new") ||
              line.contains("get") ||
              line.contains("put") ||
              line.contains("mkdir") ||
              line.contains("Exception") ||
              line.contains("throw"))
        )

        println(filteredOutput.mkString("\n"))
      } else {
        // Fallback to basic information if javap is not available
        println(s"Class: ${classFile.getName}")
        println(s"Size: ${classFile.length()} bytes")
        println("(javap not found in PATH - install JDK for detailed bytecode)")
      }
    } catch {
      case e: Exception =>
        println(s"Error disassembling class: ${e.getMessage}")
        println(s"Class: ${classFile.getName}")
        println(s"Size: ${classFile.length()} bytes")
    }
  }

  /**
   * Locates the javap executable in the system.
   *
   * This method implements a robust search strategy to locate the Java disassembler
   * tool (javap) across different operating systems and Java installations. It tries
   * multiple potential locations, including
   * 1. The standard Java home directory
   * 2. The system PATH
   * 3. Common default installation locations
   *
   * The cross-platform approach ensures that the disassembly functionality works
   * consistently across different execution environments.
   *
   * @return The path to the javap executable, or an empty string if not found
   */
  private def findJavap(): String = {
    try {
      // Try to find javap in JAVA_HOME
      val javaHome = System.getProperty("java.home")
      val javapInJavaHome = new File(javaHome, "bin/javap")
      val javapInJavaHomeExe = new File(javaHome, "bin/javap.exe")

      if (javapInJavaHome.exists()) {
        return javapInJavaHome.getAbsolutePath
      } else if (javapInJavaHomeExe.exists()) {
        return javapInJavaHomeExe.getAbsolutePath
      }

      // Try to find javap in PATH
      val isWindows = System.getProperty("os.name").toLowerCase.contains("win")
      val cmd = if (isWindows) "where javap" else "which javap"
      val output = cmd.!!.trim

      if (output.nonEmpty && new File(output).exists()) {
        return output
      }

      // If we get here, try a default location
      if (isWindows) {
        val defaultPath = "C:/Program Files/Java/jdk-11.0.1/bin/javap.exe"
        if (new File(defaultPath).exists()) {
          return defaultPath
        }
      }

      ""
    } catch {
      case _: Exception => ""
    }
  }
}