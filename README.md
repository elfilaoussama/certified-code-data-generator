# Certified Dataset Generator

This workspace is a collection of small, scriptable tools for **extracting**, **generating**, and **verifying** object-oriented (OO) structure data.

If you’re new here and just want “run the pipeline and get a `report.json`”, start with **pipeline-orchestrator**.

## Workspace layout (start here)

- [pipeline-orchestrator/](pipeline-orchestrator/) — *entrypoint CLI/GUI* that orchestrates extraction → verification.
  - README: [pipeline-orchestrator/README.md](pipeline-orchestrator/README.md)
- [java-metamodel/](java-metamodel/) — SPOON-based Java source extractor (writes `spoon.json`).
  - README: [java-metamodel/README.md](java-metamodel/README.md)
- [alloy-in-ecore-java-verification/](alloy-in-ecore-java-verification/) — AlloyInEcore + Kodkod verifier.
  - Verifier CLI: [alloy-in-ecore-java-verification/VerificationEnvironment/](alloy-in-ecore-java-verification/VerificationEnvironment/)
  - Verifier README: [alloy-in-ecore-java-verification/VerificationEnvironment/README.md](alloy-in-ecore-java-verification/VerificationEnvironment/README.md)
- [skeleton_generator/](skeleton_generator/) — Alloy Analyzer based *instance generator* (synthetic skeleton datasets).
  - README: [skeleton_generator/README.md](skeleton_generator/README.md)

## How the verification pipeline works

At a high level, the verification pipeline is:

1. **Extract (SPOON)**: parse Java source code and emit a JSON representation (`spoon.json`).
2. **Map (JSON → AIE)**: convert that JSON into an AlloyInEcore instance model (`.aie`) that the verifier can solve.
3. **Verify (Kodkod)**: solve constraints from the `.recore` metamodel against the instance and write a machine-readable `report.json`.

The orchestrator provides three ways to run this:

- `extract` — run step (1) only
- `verify` — run steps (2–3) only (input can be `.json`, `.aie`, or `.xmi`)
- `run` — run steps (1–3) end-to-end

### Outputs you should expect

The pipeline writes a SPOON JSON plus a verifier output folder that typically contains a generated `.ecore`, a `.kodkod` debug dump, and a JSON report.

When the report says:

- `SAT`: no violations were found (common shape: `{ "result": "SAT", "violations": [] }`)
- `UNSAT`: invariants/constraints were violated and `violations[]` lists the broken rule(s)

> The most practical artifact to consume programmatically is the verifier’s `report.json`.

## Quick start (recommended entrypoint)

### Linux / WSL

From the workspace root:

```bash
cd pipeline-orchestrator
./setup.sh

./run.sh run \
  --source ../java-metamodel/samples \
  --mode single \
  --out ../pipeline-output \
  --details
```

### Windows (PowerShell)

```powershell
cd .\pipeline-orchestrator
.\setup.ps1

.\run.ps1 run `
  --source "..\\java-metamodel\\samples" `
  --mode single `
  --out "..\\pipeline-output" `
  --details
```

This will create `pipeline-output/` if it doesn’t exist.

For more options (multi-repo mode, custom `.recore`, GUI, WSL execution on Windows), see:

- [pipeline-orchestrator/README.md](pipeline-orchestrator/README.md)

## Synthetic dataset generation (optional)

If instead of analyzing real Java repos you want to **generate synthetic OO skeleton instances** via Alloy, use:

- [skeleton_generator/](skeleton_generator/)

That module has its own bulk generation scripts (`generate_dataset.sh` / `generate_dataset.ps1`) and writes instances under `skeleton_generator/output/`.

## Requirements

- JDK 17+ and Maven (each module is a small Maven project)
- For GUI usage: a desktop environment (Swing)

## License

MIT — see [LICENSE](LICENSE).
