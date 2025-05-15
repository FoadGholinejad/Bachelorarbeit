package org.example

import java.io.{File, FileOutputStream}
import java.nio.file.{Files, Paths, StandardOpenOption}
import org.opalj.ba._
import org.opalj.br._
import org.opalj.br.instructions._
import org.opalj.bc.Assembler

import scala.language.postfixOps
import scala.sys.process._

/**
 * Implementation of a bytecode transformation system for method modification using OPAL.
 *
 * This class demonstrates the application of bytecode engineering techniques to alter
 * the behavior of existing methods in Java classes. It showcases how OPAL's embedded
 * Domain-Specific Language (eDSL) can be used to declaratively redefine method
 * implementations with modified functionality.
 *
 * The implementation focuses on a specific transformation case: changing a method that
 * multiplies a random number by 2 to instead multiply it by 3, demonstrating how
 * bytecode engineering can be used to modify application behavior without source code access.
 *
 * @author Foad Gholinejad
 */
object OPALModifyMethodExample {

  /**
   * Entry point for the bytecode transformation process.
   *
   * This method initializes the transformation pipeline, defining the input and output
   * paths, and coordinating the overall transformation process. It implements error handling
   * to ensure graceful failure in case of exceptions during the transformation process.
   *
   * @param args Command-line arguments (not used in current implementation)
   */
  def main(args: Array[String]): Unit = {
    // Define input and output paths for the class files
    val originalClassPath = Paths.get("C:/Users/salaz/Desktop/OPALExample/target/scala-2.13/classes/org/example/ModifyMethodTestClass.class")
    val outputClassPath = Paths.get("C:/Users/salaz/Desktop/ModifyMethodTestClass_Modified.class")

    println(s"🔍 Original class path: $originalClassPath")
    println(s"📝 Output class path  : $outputClassPath")

    try {
      transformClass(originalClassPath.toFile, outputClassPath.toFile)
      println(s"\n✅ Successfully transformed class. Output saved to: ${outputClassPath.toAbsolutePath}")
    } catch {
      case e: Exception =>
        println(s"❌ Error processing class: ${e.getMessage}")
        e.printStackTrace()
        System.exit(1)
    }
  }

  /**
   * Performs the bytecode transformation on a class file.
   *
   * This method implements the core transformation pipeline:
   * 1. Read the original class file
   * 2. Creates a new class definition with modified method implementation
   * 3. Preserves the original class structure while changing specific method behavior
   * 4. Write the transformed class to the output location
   *
   * The transformation demonstrates how to alter method behavior while maintaining
   * the overall class structure and interface.
   *
   * @param originalClassFile The source class file to be transformed
   * @param outputClassFile The destination file to write the transformed bytecode
   * @throws Exception If errors occur during the transformation process
   */
  private def transformClass(originalClassFile: File, outputClassFile: File): Unit = {
    if (!originalClassFile.exists()) {
      throw new java.io.FileNotFoundException(s"Original class file not found: ${originalClassFile.getAbsolutePath}")
    }

    // Create temporary directory for class files
    val tempDir = new File(System.getProperty("java.io.tmpdir"), "opal_bytecode_" + System.currentTimeMillis())
    tempDir.mkdirs()

    // Save original class file for disassembly
    val originalClassCopy = new File(tempDir, "Original_ModifyMethodTestClass.class")
    Files.copy(originalClassFile.toPath, originalClassCopy.toPath)

    // Print original bytecode in human-readable format
    println("\n===== ORIGINAL CLASS BYTECODE =====")
    disassembleClass(originalClassCopy)

    val startTime = System.currentTimeMillis()
    // ----------------------
    // Rebuild ModifyMethodTestClass with modified processRandomNumber() method
    // ----------------------
    val modifiedClass = CLASS(
      accessModifiers = PUBLIC SUPER,            // Class access modifiers
      thisType = "org/example/ModifyMethodTestClass", // Fully qualified class name
      fields = FIELDS(),                         // No fields in this example
      methods = METHODS(
        // Default Constructor
        METHOD(PUBLIC, "<init>", "()V", CODE(
          ALOAD_0,                                            // Load 'this'
          INVOKESPECIAL("java/lang/Object", isInterface = false ,"<init>", "()V"), // Call super constructor
          RETURN
        )),

        // Modified processRandomNumber(): generates a random number and returns number * 3
        // The original method returned number * 2, we're changing the multiplication factor
        METHOD(PUBLIC, "processRandomNumber", "()I", CODE(
          // Random random = new Random();
          NEW("java/util/Random"),
          DUP,
          INVOKESPECIAL("java/util/Random", isInterface = false ,"<init>", "()V"),
          ASTORE(1),

          // int number = random.nextInt(1000);
          ALOAD(1),
          SIPUSH(1000),
          INVOKEVIRTUAL("java/util/Random", "nextInt", "(I)I"),
          ISTORE(2),

          // System.out.println("Generated number: " + number);
          GETSTATIC("java/lang/System", "out", "Ljava/io/PrintStream;"),
          NEW("java/lang/StringBuilder"),
          DUP,
          INVOKESPECIAL("java/lang/StringBuilder", isInterface = false ,"<init>", "()V"),
          LDC(ConstantString("Generated number: ")),
          INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"),
          ILOAD(2),
          INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;"),
          INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;"),
          INVOKEVIRTUAL("java/io/PrintStream", "println", "(Ljava/lang/String;)V"),

          // return number * 3; (changed from number * 2 in the original)
          ILOAD(2),
          ICONST_3,  // Changed from ICONST_2 to ICONST_3
          IMUL,
          IRETURN
        ))
      )
    )

    // Convert to bytecode
    val (daClassFile, _) = modifiedClass.toDA()
    val newBytes = Assembler(daClassFile)

    // Save modified class file for disassembly
    val modifiedClassCopy = new File(tempDir, "Modified_ModifyMethodTestClass.class")
    new FileOutputStream(modifiedClassCopy).write(newBytes)

    // Ensure the output directory exists
    Files.createDirectories(outputClassFile.getParentFile.toPath)

    // Write the generated class file to the specified location
    Files.write(outputClassFile.toPath, newBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime

    // Print modified bytecode in human-readable format
    println("\n===== MODIFIED CLASS BYTECODE =====")
    disassembleClass(modifiedClassCopy)
    println("\n" + "=".repeat(50))

    // Clean up temporary files
    try {
      for (file <- tempDir.listFiles()) {
        file.delete()
      }
      tempDir.delete()
    } catch {
      case _: Exception => // Ignore cleanup errors
    }

    // Print summary
    println("\n📊 Transformation Summary:")
    println(s"   - Modified method: 'processRandomNumber()' to multiply by 3 instead of 2")
    println(s"   - Time taken: ${duration}ms")
    println(s"📦 Original class size: ${originalClassFile.length()} bytes")
    println(s"📦 Modified class size: ${outputClassFile.length()} bytes")
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
              line.contains("processRandomNumber") ||
              line.contains("Random") ||
              line.contains("imul") ||
              line.contains("iconst_2") ||
              line.contains("iconst_3"))
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