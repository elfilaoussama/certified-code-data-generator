package com.verification;

import eu.modelwriter.core.alloyinecore.interpreter.FormulaInfo;
import eu.modelwriter.core.alloyinecore.interpreter.KodKodUniverse;
import eu.modelwriter.core.alloyinecore.recognizer.AlloyInEcoreLexer;
import eu.modelwriter.core.alloyinecore.recognizer.AlloyInEcoreParser;
import eu.modelwriter.core.alloyinecore.translator.EcoreInstanceTranslator;
import kodkod.ast.BinaryFormula;
import kodkod.ast.Formula;
import kodkod.engine.Proof;
import kodkod.engine.Solution;
import kodkod.engine.Solver;
import kodkod.engine.satlab.SATFactory;
import kodkod.engine.ucore.RCEStrategy;
import kodkod.ast.NaryFormula;
import kodkod.ast.operator.FormulaOperator;
import kodkod.instance.Bounds;
import org.antlr.v4.runtime.*;
import org.eclipse.emf.common.util.URI;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Evaluates metamodel invariants against a concrete instance model.
 *
 * Accepts .aie (AlloyInEcore text) or .xmi (EMF XMI) instance files.
 * In .aie files, the literal ECORE_PATH is replaced with the actual .ecore path.
 *
 * Pipeline: text → ANTLR parse → KodKodUniverse → Kodkod SAT/UNSAT
 */
public class InvariantChecker {

    public static final String ECORE_PLACEHOLDER = "ECORE_PATH";

    /**
     * @param instancePath  path to .aie or .xmi instance file
     * @param ecoreAbsPath  absolute path to generated .ecore
     * @param outputDir     directory for solver output files
     * @return "SAT" or "UNSAT" result string
     */
    public static String check(String instancePath, String ecoreAbsPath, String outputDir) throws Exception {
        return checkWithReport(instancePath, ecoreAbsPath, outputDir, null).result;
    }

    /**
     * Runs verification and (optionally) fills a report with UNSAT core details.
     */
    public static VerificationReport checkWithReport(String instancePath, String ecoreAbsPath, String outputDir, VerificationReport report) throws Exception {
        VerificationReport effectiveReport = (report != null) ? report : new VerificationReport();

        effectiveReport.result = null;
        if (effectiveReport.violations != null) {
            effectiveReport.violations.clear();
        }

        File instanceFile = new File(instancePath).getAbsoluteFile();
        if (!instanceFile.exists())
            throw new IllegalArgumentException("Instance file not found: " + instancePath);

        // ── Read / translate instance text ──
        String aieText;
        if (instancePath.endsWith(".aie")) {
            aieText = new String(Files.readAllBytes(Paths.get(instanceFile.getAbsolutePath())));
            String ecoreForwardSlash = ecoreAbsPath.replace("\\", "/");
            aieText = aieText.replace(ECORE_PLACEHOLDER, ecoreForwardSlash);
        } else {
            EcoreInstanceTranslator translator = new EcoreInstanceTranslator();
            aieText = translator.translate(instanceFile.getAbsolutePath());
        }

        if (aieText == null || aieText.trim().isEmpty())
            throw new RuntimeException("Instance text empty — check format/metamodel references.");

        // ── Parse ──
        CharStream input = CharStreams.fromString(aieText);
        AlloyInEcoreLexer lexer = new AlloyInEcoreLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        URI fileURI = URI.createFileURI(instanceFile.getAbsolutePath());
        AlloyInEcoreParser parser = new AlloyInEcoreParser(tokens, fileURI);

        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg, RecognitionException e) {
                System.err.println("  PARSE ERROR " + line + ":" + charPositionInLine + " " + msg);
            }
        });

        // Suppress verbose parser output but keep parse errors visible.
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream nullStream = new java.io.PrintStream(new java.io.OutputStream() { public void write(int b) {} });
        System.setOut(nullStream);
        try {
            parser.instance(null);
        } finally {
            System.setOut(originalOut);
        }

        if (parser.instance == null)
            throw new RuntimeException("Parser returned null instance — check syntax errors above.");

        // ── Build KodKod universe ──
        KodKodUniverse universe = new KodKodUniverse(parser.instance);
        Formula formula = universe.getFormula();
        Bounds bounds = universe.getBounds();

        // ── Solve ──
        Solver solver = new Solver();

        // Prefer proof-capable solver when available (enables UNSAT core extraction).
        // Fall back to pure-Java SAT4J for portability (e.g., Windows without WSL/native prover libs).
        SATFactory satFactory = SATFactory.MiniSatProver;
        if (!SATFactory.available(satFactory)) {
            satFactory = SATFactory.DefaultSAT4J;
        }

        solver.options().setSolver(satFactory);
        solver.options().setCoreGranularity(3);
        solver.options().setLogTranslation(2);
        solver.options().setSymmetryBreaking(20);
        solver.options().setBitwidth(universe.getBitwidth());

        Solution solution;
        try {
            solution = solver.solve(formula, bounds);
        } catch (RuntimeException | UnsatisfiedLinkError t) {
            // If the selected solver cannot be loaded at runtime, retry with SAT4J.
            if (satFactory != SATFactory.DefaultSAT4J) {
                solver = new Solver();
                solver.options().setSolver(SATFactory.DefaultSAT4J);
                solver.options().setCoreGranularity(0);
                solver.options().setLogTranslation(2);
                solver.options().setSymmetryBreaking(20);
                solver.options().setBitwidth(universe.getBitwidth());
                solution = solver.solve(formula, bounds);
            } else {
                throw t;
            }
        }

        // ── Save solver output ──
        File outDir = new File(outputDir);
        outDir.mkdirs();
        String solverFile = instanceFile.getName() + ".kodkod";
        universe.save(outDir.getAbsolutePath() + File.separator, solverFile, bounds, formula, solution);
        System.out.println("      → " + outDir.getAbsolutePath() + File.separator + solverFile);

        // ── Report ──
        if (solution.sat()) {
            System.out.println("      ✓ SAT — all invariants hold");
            universe.updateWithInstance(solution.instance());
            effectiveReport.result = "SAT";
            return effectiveReport;
        } else {
            System.out.println("      ✗ UNSAT — invariant violation detected");
            effectiveReport.result = "UNSAT";
            Proof proof = solution.proof();
            if (proof != null) {
                proof.minimize(new RCEStrategy(proof.log()));
                effectiveReport.violations.addAll(extractViolationsFromCore(universe, proof.highLevelCore().keySet()));
            } else {
                // Portable fallback: compute a 1-minimal unsat core by trying to drop candidate rule formulas.
                // This requires no proof logging and works with SAT4J.
                effectiveReport.violations.addAll(extractViolationsByDroppingRules(universe, bounds, formula, solver.options().solver()));
            }
            return effectiveReport;
        }
    }

    private static List<VerificationReport.Violation> extractViolationsFromCore(KodKodUniverse universe, Set<Formula> core) {
        List<VerificationReport.Violation> violations = new ArrayList<>();

        for (Formula f : core) {
            Set<FormulaInfo> fis = universe.getFormulaInfos(f);
            if (fis == null || fis.isEmpty()) {
                violations.add(new VerificationReport.Violation(null, null, null, String.valueOf(f)));
            } else {
                for (FormulaInfo fi : fis) {
                    Integer line = fi.getLine() > 0 ? fi.getLine() : null;
                    String desc = fi.getDescription();
                    String name = extractInvariantName(desc);
                    violations.add(new VerificationReport.Violation(line, name, desc, String.valueOf(f)));
                }
            }
        }

        return violations;
    }

    private static List<VerificationReport.Violation> extractViolationsByDroppingRules(
            KodKodUniverse universe,
            Bounds bounds,
            Formula fullFormula,
            SATFactory satFactory
    ) {
        List<Formula> conjuncts = flattenAnd(fullFormula);

        // Phase 1: Prefer user-facing "rules" (invariants + qualifier constraints) to keep fallback fast.
        List<Formula> ruleCandidates = new ArrayList<>();
        for (Formula c : conjuncts) {
            if (looksLikeRule(universe.getFormulaInfos(c))) {
                ruleCandidates.add(c);
            }
        }

        List<Formula> candidates = !ruleCandidates.isEmpty() ? ruleCandidates : conjuncts;

        final int maxCandidates = 250;
        if (candidates.size() > maxCandidates) {
            candidates = candidates.subList(0, maxCandidates);
        }

        List<VerificationReport.Violation> violations = new ArrayList<>();
        for (Formula candidate : candidates) {
            Formula reduced = andWithout(conjuncts, candidate);
            boolean sat;
            try {
                sat = solveSat(reduced, bounds, satFactory, universe.getBitwidth());
            } catch (RuntimeException | UnsatisfiedLinkError t) {
                // If even the selected solver can't run, fall back to SAT4J.
                sat = solveSat(reduced, bounds, SATFactory.DefaultSAT4J, universe.getBitwidth());
            }

            if (sat) {
                Set<FormulaInfo> fis = universe.getFormulaInfos(candidate);
                if (fis == null || fis.isEmpty()) {
                    violations.add(new VerificationReport.Violation(null, null, null, String.valueOf(candidate)));
                } else {
                    for (FormulaInfo fi : fis) {
                        Integer line = fi.getLine() > 0 ? fi.getLine() : null;
                        String desc = fi.getDescription();
                        String name = extractInvariantName(desc);
                        violations.add(new VerificationReport.Violation(line, name, desc, String.valueOf(candidate)));
                    }
                }
            }
        }

        return violations;
    }

    private static boolean solveSat(Formula formula, Bounds bounds, SATFactory satFactory, int bitwidth) {
        Solver solver = new Solver();
        solver.options().setSolver(satFactory);
        solver.options().setCoreGranularity(0);
        solver.options().setLogTranslation(0);
        solver.options().setSymmetryBreaking(20);
        solver.options().setBitwidth(bitwidth);
        Solution sol = solver.solve(formula, bounds);
        return sol.sat();
    }

    private static boolean looksLikeRule(Set<FormulaInfo> infos) {
        if (infos == null || infos.isEmpty()) {
            return false;
        }
        for (FormulaInfo fi : infos) {
            String desc = fi.getDescription();
            if (desc == null) {
                continue;
            }
            String trimmed = desc.trim();
            if (trimmed.startsWith("Invariant ")) {
                return true;
            }
            if (trimmed.startsWith("Qualifier {id} (unique) on ")) {
                return true;
            }
        }
        return false;
    }

    private static String extractInvariantName(String desc) {
        if (desc == null) {
            return null;
        }
        String trimmed = desc.trim();
        if (trimmed.startsWith("Invariant ")) {
            String rest = trimmed.substring("Invariant ".length());
            int colon = rest.indexOf(':');
            return (colon >= 0 ? rest.substring(0, colon) : rest).trim();
        }
        if (trimmed.startsWith("Qualifier {id} (unique) on ")) {
            String target = trimmed.substring("Qualifier {id} (unique) on ".length()).trim();
            return !target.isEmpty() ? ("id_unique(" + target + ")") : "id_unique";
        }
        return null;
    }

    private static List<Formula> flattenAnd(Formula f) {
        List<Formula> out = new ArrayList<>();
        flattenAndInto(f, out);
        return out;
    }

    private static void flattenAndInto(Formula f, List<Formula> out) {
        if (f == null) {
            return;
        }
        if (f instanceof NaryFormula) {
            NaryFormula nf = (NaryFormula) f;
            if (nf.op() == FormulaOperator.AND) {
                for (Formula c : nf) {
                    flattenAndInto(c, out);
                }
                return;
            }
        }
        if (f instanceof BinaryFormula) {
            BinaryFormula bf = (BinaryFormula) f;
            if (bf.op() == FormulaOperator.AND) {
                flattenAndInto(bf.left(), out);
                flattenAndInto(bf.right(), out);
                return;
            }
        }
        out.add(f);
    }

    private static Formula andWithout(List<Formula> conjuncts, Formula excluded) {
        if (conjuncts == null || conjuncts.isEmpty()) {
            return Formula.TRUE;
        }
        List<Formula> kept = new ArrayList<>(Math.max(0, conjuncts.size() - 1));
        boolean removedOnce = false;
        for (Formula c : conjuncts) {
            if (!removedOnce && c == excluded) {
                removedOnce = true;
                continue;
            }
            kept.add(c);
        }
        if (kept.isEmpty()) {
            return Formula.TRUE;
        }
        return Formula.and(kept);
    }
}
