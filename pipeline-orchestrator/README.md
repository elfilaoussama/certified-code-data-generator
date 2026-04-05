# Pipeline Orchestrator (Linux)

This module provides a small, **parameterized** CLI that orchestrates the existing pipeline:

1. `java-metamodel` (SPOON) → extracts project structure into JSON
2. `alloy-in-ecore-java-verification/VerificationEnvironment` → verifies invariants (SAT/UNSAT) and optionally writes a JSON report

It intentionally reuses the existing module wrappers:

- On Linux/Unix: `run.sh`
- On Windows: `run.ps1`

## Prereqs

- Java + Maven
- For GUI: a desktop environment

## Quick start

```bash
cd pipeline-orchestrator
./setup.sh
./run.sh run \
  --source /mnt/e/PROJECTS/Certified\ Dataset\ Generator/java-metamodel/samples \
  --mode single \
  --out /mnt/e/PROJECTS/Certified\ Dataset\ Generator/pipeline-output \
  --details
```

### Windows (PowerShell, native)

```powershell
cd .\pipeline-orchestrator
.\setup.ps1
.\run.ps1 run --source "E:\\PROJECTS\\Certified Dataset Generator\\java-metamodel\\samples" --mode single --out "E:\\PROJECTS\\Certified Dataset Generator\\pipeline-output" --details
```

## GUI

Launch the GUI:

```bash
cd pipeline-orchestrator
./run.sh gui
```

On Windows (PowerShell):

```powershell
cd .\pipeline-orchestrator
.\setup.ps1
.\run.ps1 gui
```

### Windows GUI + Linux execution (recommended)

If you want the **GUI to run natively on Windows**, but the underlying pipeline steps (`run.sh` in submodules) to execute inside a **Linux shell**, use:

```powershell
cd .\pipeline-orchestrator
.\setup.ps1
.\run.ps1 gui --platform LINUX
```

This option is also available for `extract`, `verify`, and `run`.

Outputs:

- `out/spoon.json`
- `out/verifier/` (generated `.ecore`, `.kodkod`, and any other artifacts)
- `out/verifier/report.json` (if `--report` or `--details` is enabled)

## Commands

- `extract` — run SPOON extractor only
- `verify` — run verifier only (`.json`, `.aie`, `.xmi`)
- `run` — extract + verify

Run `./run.sh --help` for full options.
