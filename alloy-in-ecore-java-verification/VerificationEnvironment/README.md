# AlloyInEcore Verification Pipeline

Verifies instance models against AlloyInEcore `.recore` invariants using Kodkod (SAT/UNSAT).

## What it does

Each run:

1. Parses the metamodel `.recore` into an EMF `EPackage` and writes a generated `.ecore` into the output directory.
2. Loads the instance (`.aie` or `.xmi`). (`.xmi` is translated into `.aie` internally.)
3. Builds Kodkod `Formula` + `Bounds`, solves with MiniSatProver, and writes a debug `.kodkod` file.

When UNSAT, the tool extracts an **UNSAT core** (a minimal set of formulas that caused UNSAT) and reports the violated rule(s).

## Credits / License

This verifier is built on top of:

- AlloyInEcore (MIT License) — Copyright (c) 2016, Ferhat Erata <ferhat@computer.org>
- Kodkod (MIT License) — Copyright (c) 2005–present, Emina Torlak

Kodkod may rely on third-party SAT solvers; their licenses may differ from MIT.

## Requirements (Windows)

Windows is supported **natively** (no WSL required).

Use the provided PowerShell wrappers (`setup.ps1`, `run.ps1`).

Note: if proof-capable native solver binaries are not available, `--details` / `--report` will still list the broken rule(s) using a proof-less approximation (drop-one-rule checks) rather than an exact Kodkod UNSAT core.

## Setup

First run (or after changing AlloyInEcore core code):

### Linux (Bash)

```bash
cd VerificationEnvironment
./setup.sh
```

### Windows (PowerShell)

```powershell
cd .\VerificationEnvironment
.\setup.ps1
```

## Usage

Run help:

```bash
cd VerificationEnvironment
./run.sh --help
```

On Windows PowerShell:

```powershell
cd .\VerificationEnvironment
.\run.ps1 --help
```

### Flags

| Flag | Description | Default |
| ---- | ----------- | ------- |
| `-i, --instance <path>` | Instance file (`.aie`, `.xmi`, or `.json`) | (required) |
| `-r, --recore <path>` | Metamodel `.recore` path | `src/main/resources/ClassHierarchies.recore` |
| `-o, --output <dir>` | Output directory | `output/` |
| `--details` | Print “Broken rules” (UNSAT core) to console | off |
| `--report <path>` | Write machine-readable JSON report | off |

### Examples

Verify an `.aie` instance and write a JSON report:

```bash
./run.sh -i my_instance.aie --details --report output/report.json
```

Verify an `.xmi` instance:

```bash
./run.sh -i my_instance.xmi --details --report output/report.json
```

From Windows PowerShell:

```powershell
cd .\VerificationEnvironment
.\run.ps1 -i my_instance.aie --details --report output\report.json
```

### About `.json` instances

If you pass a `.json`, the pipeline will attempt to map it to `output/MappedInstance.aie` using `com.verification.mapper.JsonToAieMapper`.
This mapper is currently best-effort and may not emit syntax that the AlloyInEcore instance grammar accepts. For reliable runs, prefer `.aie` or `.xmi`.

### JSON → AIE Mapper (`JsonToAieMapper`)

The `JsonToAieMapper` translates SPOON-extracted Java project metadata (JSON) into an AlloyInEcore `.aie` instance model that the verification pipeline can check against the metamodel invariants.

#### Architecture

```text
┌──────────────┐        ┌─────────────────┐        ┌──────────────┐
│  SPOON JSON  │──────▶│  JsonToAieMapper │──────▶│  .aie file   │
│  (input)     │        │                 │        │  (output)    │
└──────────────┘        └─────────────────┘        └──────────────┘
```

1. **Parse** — Reads the JSON file with Gson; expects a top-level `projects` array.
2. **Iterate types** — Takes the first project and iterates over its `types[]` array (up to **50** types).
3. **Map each type** — For every class/interface, emits a `Class` object with nested `Method` / `Attribute` objects (all wrapped under a synthetic `Root` container so the instance parses as a single root object).
4. **Write** — Creates parent directories if needed and writes the `.aie` output.

#### Mapping rules

| JSON field | AIE field | Mapping logic |
| --- | --- | --- |
| `qualifiedName` | `Class.cid` | Dots replaced with underscores (e.g. `com.foo.Bar` → `com_foo_Bar`) |
| `kind` | `kind` | `"interface"` → `Interface`, anything else → `ConcreteClass` |
| `isAbstract` | `isAbstract` | `true` → `Yes`, `false`/missing → `No` |
| `methods[].name` | `mname` | Verbatim |
| `methods[].returnType` | `rtype` | Verbatim (defaults to `"void"`) |
| `methods[].parameters[].type` | `msig` | Builds signature string: `name(Type1, Type2)` |
| `methods[].visibility` | `mvis` | `public` → `Pub`, `private` → `Priv`, `protected` → `Prot`, `package` → `Pkg` |
| `methods[].isStatic` | `mscope` | `true` → `Static`, `false`/missing → `Instance` |
| `methods[].isAbstract` | `isAbstract` | `true` → `Yes`, `false`/missing → `No` |
| `fields[].name` | `aname` | Verbatim |
| `fields[].type` | `atype` | Verbatim (defaults to `"Object"`) |
| `fields[].visibility` | `avis` | Same visibility mapping as methods |
| `fields[].isStatic` | `ascope` | `true` → `Static`, `false`/missing → `Instance` |

#### Standalone usage

The mapper can be invoked independently of the verification pipeline:

```bash
mvn exec:java \
  -Dexec.mainClass="com.verification.mapper.JsonToAieMapper" \
  -Dexec.args="path/to/spoon_output.json output/MappedInstance.aie"
```

On Windows PowerShell:

```powershell
mvn exec:java `
  "-Dexec.mainClass=com.verification.mapper.JsonToAieMapper" `
  "-Dexec.args=path\to\spoon_output.json output\MappedInstance.aie"
```

#### Example

**Input** (`spoon_output.json`):

```json
{
  "projects": [{
    "types": [{
      "qualifiedName": "com.example.Animal",
      "kind": "class",
      "isAbstract": true,
      "methods": [{
        "name": "speak",
        "returnType": "String",
        "visibility": "public",
        "isStatic": false,
        "isAbstract": true,
        "parameters": []
      }],
      "fields": [{
        "name": "name",
        "type": "String",
        "visibility": "private",
        "isStatic": false
      }]
    }]
  }]
}
```

**Output** (`MappedInstance.aie`):

```text
instance results;
model class_hierarchies : 'ECORE_PATH';

Root {
  contents: {
    Class {
      cid: "com_example_Animal",
      kind: ConcreteClass,
      isAbstract: Yes,
      methods: {
        Method {
          mid: "com_example_Animal_m0",
          mname: "speak",
          msig: "speak()",
          mvis: Pub,
          mscope: Instance,
          rtype: "String",
          isAbstract: Yes
        }
      },
      attributes: {
        Attribute {
          aid: "com_example_Animal_a0",
          aname: "name",
          atype: "String",
          avis: Priv,
          ascope: Instance
        }
      }
    }
  }
}
```

> **Note:** The mapper caps processing at 50 types per project. The `'ECORE_PATH'` placeholder in the header is replaced by the pipeline at runtime with the actual generated `.ecore` path.

## Outputs

Generated artifacts go under the chosen output directory (`output/` by default):

- `<MetamodelName>.ecore` (generated from `.recore`)
- `<InstanceFile>.kodkod` (solver debug dump)

Maven also creates `target/` directories while building/running.

## Report format (`--report`)

The JSON report includes `invariantName` so you can aggregate “most violated rules” later:

```json
{
  "result": "UNSAT",
  "violations": [
    {
      "line": 53,
      "invariantName": "id_unique(Class.cid)",
      "description": "Qualifier {id} (unique) on Class.cid",
      "formula": "(all ... )"
    }
  ]
}
```

Notes:

- `line` is a 1-based line number from the `.recore` token that produced the formula (it may point at generated/qualifier constraints like `{id}` rules).
- `invariantName` is either a user invariant name (e.g. `UniqueClassIds`) or a generated constraint name (e.g. `id_unique(Class.cid)`).
