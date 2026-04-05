# Java Metamodel Reader

A tool that uses [SPOON](https://spoon.gforge.inria.fr/) to parse Java source files and extract metamodel information (classes, interfaces, enums, fields, methods, inheritance, etc.).

## Prerequisites

- **JDK 17** or later
- **Maven 3.8+**

## Compilation

You can compile the project using the native maven command: `mvn clean compile`

Or use the provided wrapper scripts based on your OS:
- **Windows:** `.\setup.ps1`
- **Linux / WSL:** `./setup.sh` (If permission denied, run `chmod +x *.sh` first)

## Usage

This tool now utilizes robust CLI parameters to control input modes, JSON file output, and summary visibility. 

You can run the tool using the wrapper scripts:
- **Windows:** `.\run.ps1 [variables]`
- **Linux / WSL:** `./run.sh [variables]`
- **Native Maven:** `mvn exec:java -Dexec.args="[variables]"`

### Arguments

| Parameter | Description |
|-----------|-------------|
| `-i, --input <path>` | **(Required)** The directory path to process. |
| `-m, --mode <mode>`  | `single` (default): treats the input path as one project.<br>`multi-repo`: treats the input path as a container of multiple separate repositories. It will process each immediate subdirectory independently. |
| `-j, --json`         | Enables JSON output. |
| `-o, --output <file>`| If specified, the JSON output is saved to this text file instead of printing to the console. |
| `--no-summary`       | Suppresses the human-readable metamodel summary on the console (defaults to false). |
| `-h, --help`         | Shows the help message. |

### Examples

**1. Basic Single Project (Default summary view)**
```bash
./run.sh -i samples
```

**2. Multi-repo analysis saving strictly to a JSON file**
```bash
./run.sh -i /path/to/multiple/repos -m multi-repo --json --output results.json --no-summary
```

**3. Single project, print summary AND json on screen**
```bash
./run.sh -i samples --json
```

## Project Structure

```
java-metamodel/
├── pom.xml                                  # Maven configuration
├── setup.sh / setup.ps1                     # Compilation scripts
├── run.sh / run.ps1                         # Execution wrappers
├── samples/                                 # Sample single-repo folder
│   ├── Animal.java
│   └── Speakable.java
└── src/main/java/com/metamodel/
    └── JavaModelReader.java                 # Main application
```

## Extracted Features

For each Java type found in the source directory:
- **Type kind:** class, interface, enum, annotation, record
- **Name:** Simple and fully-qualified name
- **Superclass:** Direct superclass
- **Interfaces:** All implemented interfaces
- **Fields:** Name, type, visibility, static/final modifiers
- **Methods:** Name, return type, parameters, visibility, modifiers
