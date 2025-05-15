# Java Bytecode Instrumentation Projects

This repository contains four independent Java projects developed as part of my bachelor's thesis at TU Dortmund. The goal is to compare the capabilities, usability, and performance of three major Java bytecode instrumentation frameworks: **ASM**, **Javassist**, and **OPAL**.

## 📁 Project Structure

Each subdirectory represents a standalone IntelliJ project:

- `ASMExample/` – Instrumentation tasks using the ASM framework
- `JavaAssistExample/` – Instrumentation using Javassist
- `OPALExample/` – Bytecode transformation in Scala using OPAL’s CODE DSL
- `BAJarFile/` – Contains `myApp.jar`, a compiled multi-threaded application used to test instrumentation at runtime (e.g., security enforcement)

## 🧪 Instrumentation Tasks

Implemented for all frameworks:
- Field Addition
- Method Addition
- Method Modification
- Security Enforcement (replacing `mkdir()` with `SecurityException`)

## 📄 Thesis Reference

This repository is part of my Bachelor's thesis:
> **Comparing Java Bytecode Instrumentation Approaches**  
> Technische Universität Dortmund – Faculty of Computer Science  
> Supervisor: JProf. Dr.-Ing. Ben Hermann

## 📎 Repository Purpose

To provide practical, measurable, and structured examples for comparing Java bytecode instrumentation tools across several use cases and APIs.

## 📄 License

For academic and educational use only.
