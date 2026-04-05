package com.metamodel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * JavaModelReader uses the SPOON library to parse Java source files
 * and extract metamodel information.
 */
@Command(name = "java-metamodel", mixinStandardHelpOptions = true, version = "1.0",
        description = "Uses SPOON to parse Java source files and extract metamodel information.")
public class JavaModelReader implements Callable<Integer> {

    @Option(names = {"-i", "--input"}, required = true, description = "The input directory to process.")
    private File inputPath;

    @Option(names = {"-m", "--mode"}, defaultValue = "single",
            description = "Processing mode: 'single' (default) for a single project, 'multi-repo' for a directory containing multiple repositories.")
    private String mode;

    @Option(names = {"-j", "--json"}, description = "Enable JSON generation.")
    private boolean generateJson;

    @Option(names = {"-o", "--output"}, description = "File path to save the JSON output.")
    private File outputPath;

    @Option(names = {"--no-summary"}, description = "Disable the human-readable metamodel summary on the console.")
    private boolean noSummary;

    // ── Data classes for JSON output ──────────────────────────────────────

    public record FieldInfo(String name, String type, String visibility, boolean isStatic, boolean isFinal) {}
    public record ParameterInfo(String name, String type) {}
    public record MethodInfo(String name, String returnType, String visibility, boolean isStatic, boolean isAbstract, List<ParameterInfo> parameters) {}
    public record TypeInfo(String qualifiedName, String simpleName, String kind, String superClass, List<String> interfaces, List<FieldInfo> fields, List<MethodInfo> methods, boolean isAbstract, boolean isFinal) {}
    public record ProjectModel(String projectName, List<TypeInfo> types) {}
    public record RunResult(String mode, String generatedAt, List<ProjectModel> projects) {}

    // ── Entry Point ───────────────────────────────────────────────────────

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JavaModelReader()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║       Java Metamodel Reader (SPOON)              ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("► Input path: " + inputPath.getAbsolutePath());
        System.out.println("► Mode:       " + mode);
        System.out.println();

        if (!inputPath.exists() || !inputPath.isDirectory()) {
            System.err.println("Error: Input path does not exist or is not a directory.");
            return 1;
        }

        List<ProjectModel> parsedProjects = new ArrayList<>();

        try {
            if ("multi-repo".equalsIgnoreCase(mode)) {
                List<Path> subDirs = Files.list(inputPath.toPath())
                        .filter(Files::isDirectory)
                        .collect(Collectors.toList());

                if (subDirs.isEmpty()) {
                    System.out.println("No subdirectories found in multi-repo mode.");
                } else {
                    System.out.println("Processing " + subDirs.size() + " repositories in parallel...");
                    List<ProjectModel> results = subDirs.parallelStream()
                            .map(repoPath -> {
                                System.out.println("  -> Started processing: " + repoPath.getFileName());
                                ProjectModel pm = processProject(repoPath.toFile(), repoPath.getFileName().toString());
                                System.out.println("  <- Finished processing: " + repoPath.getFileName());
                                return pm;
                            })
                            .collect(Collectors.toList());
                    parsedProjects.addAll(results);
                }
            } else {
                // Single project mode
                parsedProjects.add(processProject(inputPath, inputPath.getName()));
            }

            // Output Handling
            if (!noSummary) {
                printSummary(parsedProjects);
            }

            if (generateJson || outputPath != null) {
                handleJsonOutput(parsedProjects);
            } else if (noSummary) {
                // If they said no summary and didn't ask for JSON, note that nothing was output.
                System.out.println("Execution complete. (Both summary and JSON outputs were disabled).");
            }

        } catch (Exception e) {
            System.err.println("An error occurred during processing:");
            e.printStackTrace();
            return 1;
        }

        return 0;
    }

    // ── SPOON Processing ──────────────────────────────────────────────────

    private ProjectModel processProject(File sourceDir, String projectName) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir.getAbsolutePath());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(17);
        
        try {
            launcher.buildModel();
        } catch (Exception e) {
            System.err.println("  [Warning] SPOON encountered an error parsing " + projectName + ": " + e.getMessage());
        }

        CtModel model = launcher.getModel();
        List<TypeInfo> types = new ArrayList<>();

        for (CtType<?> type : model.getAllTypes()) {
            types.add(extractTypeInfo(type));
        }
        
        return new ProjectModel(projectName, types);
    }

    private static TypeInfo extractTypeInfo(CtType<?> type) {
        String kind = getKind(type);

        String superClass = null;
        if (type instanceof CtClass<?> ctClass) {
            CtTypeReference<?> superRef = ctClass.getSuperclass();
            if (superRef != null && !"java.lang.Object".equals(superRef.getQualifiedName())) {
                superClass = superRef.getQualifiedName();
            }
        }

        List<String> interfaces = type.getSuperInterfaces().stream()
                .map(CtTypeReference::getQualifiedName)
                .collect(Collectors.toList());

        List<FieldInfo> fields = type.getFields().stream()
                .map(f -> new FieldInfo(
                        f.getSimpleName(),
                        f.getType() != null ? f.getType().getSimpleName() : "unknown",
                        f.getVisibility() != null ? f.getVisibility().toString() : "package-private",
                        f.isStatic(),
                        f.isFinal()
                )).collect(Collectors.toList());

        List<MethodInfo> methods = type.getMethods().stream()
                .map(m -> new MethodInfo(
                        m.getSimpleName(),
                        m.getType() != null ? m.getType().getSimpleName() : "void",
                        m.getVisibility() != null ? m.getVisibility().toString() : "package-private",
                        m.isStatic(),
                        m.isAbstract(),
                        m.getParameters().stream()
                                .map(p -> new ParameterInfo(
                                        p.getSimpleName(),
                                        p.getType() != null ? p.getType().getSimpleName() : "unknown"
                                )).collect(Collectors.toList())
                )).collect(Collectors.toList());

        return new TypeInfo(type.getQualifiedName(), type.getSimpleName(), kind, superClass, 
                            interfaces, fields, methods, type.isAbstract(), type.isFinal());
    }

    private static String getKind(CtType<?> type) {
        if (type instanceof CtEnum<?>) return "enum";
        if (type instanceof CtInterface<?>) return "interface";
        if (type instanceof CtRecord) return "record";
        if (type.isAnnotationType()) return "annotation";
        return "class";
    }

    // ── Output Handlers ───────────────────────────────────────────────────

    private void printSummary(List<ProjectModel> projects) {
        for (ProjectModel pm : projects) {
            System.out.println("\n═══════════════════════════════════════════════════");
            System.out.println("  Project: " + pm.projectName() + " (" + pm.types().size() + " type(s) found)");
            System.out.println("═══════════════════════════════════════════════════");

            for (TypeInfo t : pm.types()) {
                System.out.println();
                System.out.printf("  ┌─ %s %s%s%s%n",
                        t.kind().toUpperCase(),
                        t.qualifiedName(),
                        t.isAbstract() ? " [abstract]" : "",
                        t.isFinal() ? " [final]" : "");

                if (t.superClass() != null) {
                    System.out.println("  │  extends: " + t.superClass());
                }
                if (!t.interfaces().isEmpty()) {
                    System.out.println("  │  implements: " + String.join(", ", t.interfaces()));
                }

                if (!t.fields().isEmpty()) {
                    System.out.println("  │");
                    System.out.println("  │  Fields:");
                    for (FieldInfo f : t.fields()) {
                        System.out.printf("  │    %-10s %-15s %-20s%s%s%n",
                                f.visibility(), f.type(), f.name(),
                                f.isStatic() ? " [static]" : "", f.isFinal() ? " [final]" : "");
                    }
                }

                if (!t.methods().isEmpty()) {
                    System.out.println("  │");
                    System.out.println("  │  Methods:");
                    for (MethodInfo m : t.methods()) {
                        String params = m.parameters().stream()
                                .map(p -> p.type() + " " + p.name())
                                .collect(Collectors.joining(", "));
                        System.out.printf("  │    %-10s %-15s %s(%s)%s%s%n",
                                m.visibility(), m.returnType(), m.name(), params,
                                m.isStatic() ? " [static]" : "", m.isAbstract() ? " [abstract]" : "");
                    }
                }
                System.out.println("  └─────────────────────────────────────────────");
            }
        }
    }

    private void handleJsonOutput(List<ProjectModel> projects) {
        RunResult result = new RunResult(mode, new Date().toString(), projects);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonString = gson.toJson(result);

        if (outputPath != null) {
            try (FileWriter writer = new FileWriter(outputPath)) {
                writer.write(jsonString);
                System.out.println("\n[SUCCESS] JSON successfully written to " + outputPath.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("\n[ERROR] Could not write JSON to file: " + e.getMessage());
            }
        } else if (generateJson) {
            System.out.println("\n═══════════════════════════════════════════════════");
            System.out.println("  JSON Output");
            System.out.println("═══════════════════════════════════════════════════");
            System.out.println(jsonString);
        }
    }
}
