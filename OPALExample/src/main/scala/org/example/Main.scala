package org.example

object Main {
  def main(args: Array[String]): Unit = {
    println("MENU:")
    println("1) Add Field Example")
    println("2) Add Method Example")
    println("3) Modify Method Example")
    println("4) OPAL Security Example")
    print("Select scenario (1/2/3/4): ")
    val choice = scala.io.StdIn.readInt()

    choice match {
      case 1 =>
        OPALAddFieldExample.main(Array.empty)
      case 2 =>
        OPALAddMethodExample.main(Array.empty)
      case 3 =>
        OPALModifyMethodExample.main(Array.empty)
      case 4 =>
        OPALSecurityExample.main(Array.empty)
      case _ =>
        println("Invalid option.")
    }
  }
}
