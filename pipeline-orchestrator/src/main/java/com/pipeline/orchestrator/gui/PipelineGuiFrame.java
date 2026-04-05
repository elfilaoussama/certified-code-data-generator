package com.pipeline.orchestrator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

final class PipelineGuiFrame extends JFrame {

    private final PipelineCommands.CommonOptions common;

    private final JTextField sourceField = new JTextField();
    private final JComboBox<String> modeBox = new JComboBox<>(new String[]{"single", "multi-repo"});
    private final JTextField outField = new JTextField();
    private final JTextField recoreField = new JTextField();
    private final JCheckBox detailsBox = new JCheckBox("Details (broken rules)");
    private final JCheckBox summaryBox = new JCheckBox("Summary (java-metamodel)");
    private final JTextField instanceField = new JTextField();

    private final JTextArea logArea = new JTextArea();
    private final JButton runButton = new JButton("Run pipeline");
    private final JButton extractButton = new JButton("Extract only");
    private final JButton verifyButton = new JButton("Verify only (instance)");

    private final JLabel statusLabel = new JLabel("Idle");
    private final JProgressBar progressBar = new JProgressBar();

    PipelineGuiFrame(PipelineCommands.CommonOptions common, String defaultSource, String defaultOut) {
        super("Certified Dataset Generator — Pipeline");
        this.common = common != null ? common : new PipelineCommands.CommonOptions();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1040, 720);
        setLocationRelativeTo(null);

        setJMenuBar(buildMenuBar());

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(content);

        content.add(buildFormPanel(defaultSource, defaultOut), BorderLayout.NORTH);
        content.add(buildLogPanel(), BorderLayout.CENTER);
        content.add(buildStatusPanel(), BorderLayout.SOUTH);

        wireActions();

        appendLog("GUI ready.\n");
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        JMenuItem openOut = new JMenuItem("Open output folder");
        openOut.addActionListener(e -> openOutputFolder());
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> dispatchEvent(new java.awt.event.WindowEvent(this, java.awt.event.WindowEvent.WINDOW_CLOSING)));
        file.add(openOut);
        file.addSeparator();
        file.add(exit);

        JMenu log = new JMenu("Log");
        JMenuItem clear = new JMenuItem("Clear");
        clear.addActionListener(e -> logArea.setText(""));
        JMenuItem save = new JMenuItem("Save as…");
        save.addActionListener(e -> saveLogAs());
        log.add(clear);
        log.add(save);

        bar.add(file);
        bar.add(log);
        return bar;
    }

    private JPanel buildFormPanel(String defaultSource, String defaultOut) {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;

        int row = 0;
        addRow(form, c, row++, "Source", sourceField, e -> chooseDirectory(sourceField));
        addRow(form, c, row++, "Mode", modeBox, null);
        addRow(form, c, row++, "Output dir", outField, e -> chooseDirectory(outField));
        addRow(form, c, row++, ".recore (optional)", recoreField, e -> chooseFile(recoreField));
        addRow(form, c, row++, "Instance (verify only)", instanceField, e -> chooseFile(instanceField));

        JPanel flags = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        flags.add(detailsBox);
        flags.add(summaryBox);

        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(new JLabel("Options"), c);
        c.gridx = 1;
        c.gridy = row;
        c.gridwidth = 2;
        c.weightx = 1;
        form.add(flags, c);
        c.gridwidth = 1;
        row++;

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        buttons.add(runButton);
        buttons.add(extractButton);
        buttons.add(verifyButton);

        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(new JLabel("Actions"), c);
        c.gridx = 1;
        c.gridy = row;
        c.gridwidth = 2;
        c.weightx = 1;
        form.add(buttons, c);
        c.gridwidth = 1;

        // Defaults
        if (defaultSource != null && !defaultSource.isBlank()) {
            sourceField.setText(defaultSource);
        }
        if (defaultOut != null && !defaultOut.isBlank()) {
            outField.setText(defaultOut);
        } else {
            outField.setText("pipeline-output");
        }
        recoreField.setText("");
        instanceField.setText("");
        summaryBox.setSelected(true);

        return form;
    }

    private JComponent buildLogPanel() {
        logArea.setEditable(false);
        logArea.setFont(pickLogFont(12));
        return new JScrollPane(logArea);
    }

    private static Font pickLogFont(int size) {
        // Prefer a monospace font that can display box-drawing glyphs, otherwise the GUI shows '?'.
        String[] candidates = new String[]{
                "Cascadia Mono",
                "Consolas",
                "Lucida Console",
                Font.MONOSPACED
        };

        for (String name : candidates) {
            Font f = new Font(name, Font.PLAIN, size);
            if (canDisplayUiGlyphs(f)) {
                return f;
            }
        }

        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    private static boolean canDisplayUiGlyphs(Font f) {
        if (f == null) {
            return false;
        }
        return f.canDisplay('╔')
                && f.canDisplay('╗')
                && f.canDisplay('║')
                && f.canDisplay('═')
                && f.canDisplay('►');
    }

    private JComponent buildStatusPanel() {
        JPanel status = new JPanel(new BorderLayout(10, 0));
        status.setBorder(new EmptyBorder(6, 6, 6, 6));

        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(160, 14));

        statusLabel.setText("Idle");

        status.add(statusLabel, BorderLayout.CENTER);
        status.add(progressBar, BorderLayout.EAST);
        return status;
    }

    private void wireActions() {
        runButton.addActionListener(this::onRun);
        extractButton.addActionListener(this::onExtract);
        verifyButton.addActionListener(this::onVerify);
    }

    private void addRow(JPanel form, GridBagConstraints c, int row, String label, JComponent field, java.awt.event.ActionListener browse) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(new JLabel(label), c);

        c.gridx = 1;
        c.gridy = row;
        c.weightx = 1;
        form.add(field, c);

        c.gridx = 2;
        c.gridy = row;
        c.weightx = 0;
        if (browse != null) {
            JButton b = new JButton("Browse");
            b.addActionListener(browse);
            form.add(b, c);
        } else {
            form.add(Box.createHorizontalStrut(80), c);
        }
    }

    private void onRun(ActionEvent e) {
        String source = sourceField.getText().trim();
        String out = outField.getText().trim();
        if (source.isEmpty() || out.isEmpty()) {
            appendLog("[ERROR] Source and Output dir are required.\n");
            return;
        }

        appendLog("\n=== RUN (extract + verify) ===\n");
        runAsync("Running pipeline…", pub -> runExtractAndVerify(source, out, pub));
    }

    private void onExtract(ActionEvent e) {
        String source = sourceField.getText().trim();
        String out = outField.getText().trim();
        if (source.isEmpty() || out.isEmpty()) {
            appendLog("[ERROR] Source and Output dir are required.\n");
            return;
        }

        appendLog("\n=== EXTRACT ===\n");
        runAsync("Extracting…", pub -> runExtractOnly(source, out, pub));
    }

    private void onVerify(ActionEvent e) {
        String instance = instanceField.getText().trim();
        String out = outField.getText().trim();
        if (instance.isEmpty() || out.isEmpty()) {
            appendLog("[ERROR] Instance and Output dir are required.\n");
            return;
        }

        appendLog("\n=== VERIFY ===\n");
        runAsync("Verifying…", pub -> runVerifyOnly(instance, out, pub));
    }

    @FunctionalInterface
    private interface WorkerTask {
        int run(Consumer<GuiEvent> publish) throws Exception;
    }

    private record GuiEvent(Kind kind, String text) {
        enum Kind { LOG, STATUS }
    }

    private void runAsync(String initialStatus, WorkerTask task) {
        setBusy(true);
        setStatus(initialStatus);

        SwingWorker<Integer, GuiEvent> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                Objects.requireNonNull(task, "task");
                return task.run(ev -> publish(ev));
            }

            @Override
            protected void process(List<GuiEvent> chunks) {
                for (GuiEvent ev : chunks) {
                    if (ev == null) {
                        continue;
                    }
                    if (ev.kind() == GuiEvent.Kind.LOG) {
                        appendLog(ev.text());
                    } else if (ev.kind() == GuiEvent.Kind.STATUS) {
                        setStatus(ev.text());
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    int code = get();
                    appendLog("\n=== DONE (exit=" + code + ") ===\n");
                    setStatus(code == 0 ? "Done" : "Done (exit=" + code + ")");
                } catch (Exception ex) {
                    appendLog("\n[ERROR] " + ex.getMessage() + "\n");
                    setStatus("Error");
                } finally {
                    setBusy(false);
                }
            }
        };

        worker.execute();
    }

    private int runExtractAndVerify(String source, String out, Consumer<GuiEvent> pub) throws Exception {
        Path baseDir = Path.of("").toAbsolutePath();
        Path metamodelDir = common.resolveMetamodelDir(baseDir);
        Path verifierDir = common.resolveVerifierDir(baseDir);

        Platform effectivePlatform = common.effectivePlatform();
        boolean convertPaths = common.convertWindowsPaths();

        String mode = Objects.toString(modeBox.getSelectedItem(), "single");

        Path outBaseDirAbs = PathUtils.resolveAgainst(baseDir, convertPaths, out);
        Files.createDirectories(outBaseDirAbs);
        Path sourceAbs = PathUtils.resolveAgainst(baseDir, convertPaths, source);

        if (!"multi-repo".equalsIgnoreCase(mode)) {
            String repoName = repoNameFromPath(sourceAbs);
            Path outDirAbs = ensureRepoOutDir(outBaseDirAbs, repoName);
            Files.createDirectories(outDirAbs);

            Path spoonJsonAbs = outDirAbs.resolve("spoon.json");
            Path verifierOutAbs = outDirAbs.resolve("verifier");
            Files.createDirectories(verifierOutAbs);
            Path reportAbs = verifierOutAbs.resolve("report.json");

            pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "Output folder: " + outDirAbs + "\n"));

            pub.accept(new GuiEvent(GuiEvent.Kind.STATUS, "[1/2] Extracting metamodel…"));
            pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "[1/2] Extracting metamodel…\n"));

            List<String> extractArgs = List.of(
                    "-i", PathUtils.toModuleArg(metamodelDir, convertPaths, sourceAbs.toString()),
                    "-m", "single",
                    "--json",
                    "-o", PathUtils.toModuleArg(metamodelDir, convertPaths, spoonJsonAbs.toString()),
                    summaryBox.isSelected() ? "" : "--no-summary"
            );

            pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "java-metamodel args: " + formatArgs(extractArgs) + "\n"));
            int ec = ProcessRunner.runStreaming(metamodelDir, effectivePlatform, "./run.sh", "run.ps1", extractArgs,
                    s -> pub.accept(new GuiEvent(GuiEvent.Kind.LOG, s)));
            if (ec != 0) {
                return ec;
            }

            pub.accept(new GuiEvent(GuiEvent.Kind.STATUS, "[2/2] Verifying invariants…"));
            pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "\n[2/2] Verifying invariants…\n"));

            ArrayList<String> verifyArgs = new ArrayList<>();
            verifyArgs.add("-i");
            verifyArgs.add(PathUtils.toModuleArg(verifierDir, convertPaths, spoonJsonAbs.toString()));
            verifyArgs.add("-o");
            verifyArgs.add(PathUtils.toModuleArg(verifierDir, convertPaths, verifierOutAbs.toString()));

            String recore = recoreField.getText().trim();
            if (!recore.isEmpty()) {
                verifyArgs.add("-r");
                verifyArgs.add(PathUtils.toModuleArg(verifierDir, convertPaths, recore));
            }

            if (detailsBox.isSelected()) {
                verifyArgs.add("--details");
            }

            verifyArgs.add("--report");
            verifyArgs.add(PathUtils.toModuleArg(verifierDir, convertPaths, reportAbs.toString()));

            pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "verifier args: " + formatArgs(verifyArgs) + "\n"));

            return ProcessRunner.runStreaming(verifierDir, effectivePlatform, "./run.sh", "run.ps1", verifyArgs,
                    s -> pub.accept(new GuiEvent(GuiEvent.Kind.LOG, s)));
        }

        // Multi-repo mode: run the whole pipeline per immediate subdirectory.
        if (!Files.isDirectory(sourceAbs)) {
            pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "[ERROR] In multi-repo mode, Source must be a directory containing repos: " + sourceAbs + "\n"));
            return 2;
        }

        List<Path> repos;
        try (var s = Files.list(sourceAbs)) {
            repos = s.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName() != null ? p.getFileName().toString() : ""))
                    .toList();
        }

        int failures = 0;
        int idx = 0;
        for (Path repoDir : repos) {
            idx++;
            String repoName = repoNameFromPath(repoDir);
            Path outDirAbs = ensureRepoOutDir(outBaseDirAbs, repoName);
            Files.createDirectories(outDirAbs);

            Path spoonJsonAbs = outDirAbs.resolve("spoon.json");
            Path verifierOutAbs = outDirAbs.resolve("verifier");
            Files.createDirectories(verifierOutAbs);
            Path reportAbs = verifierOutAbs.resolve("report.json");

            pub.accept(new GuiEvent(GuiEvent.Kind.STATUS, "Repo " + idx + "/" + repos.size() + ": " + repoName));
            pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "\n=== REPO " + repoName + " ===\n"));
            pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "Output folder: " + outDirAbs + "\n"));

            List<String> extractArgs = List.of(
                    "-i", PathUtils.toModuleArg(metamodelDir, convertPaths, repoDir.toString()),
                    "-m", "single",
                    "--json",
                    "-o", PathUtils.toModuleArg(metamodelDir, convertPaths, spoonJsonAbs.toString()),
                    summaryBox.isSelected() ? "" : "--no-summary"
            );

            pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "java-metamodel args: " + formatArgs(extractArgs) + "\n"));
            int ec = ProcessRunner.runStreaming(metamodelDir, effectivePlatform, "./run.sh", "run.ps1", extractArgs,
                    s2 -> pub.accept(new GuiEvent(GuiEvent.Kind.LOG, s2)));
            if (ec != 0) {
                failures++;
                pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "[ERROR] Extract failed for " + repoName + " (exit=" + ec + ")\n"));
                continue;
            }

            ArrayList<String> verifyArgs = new ArrayList<>();
            verifyArgs.add("-i");
            verifyArgs.add(PathUtils.toModuleArg(verifierDir, convertPaths, spoonJsonAbs.toString()));
            verifyArgs.add("-o");
            verifyArgs.add(PathUtils.toModuleArg(verifierDir, convertPaths, verifierOutAbs.toString()));

            String recore = recoreField.getText().trim();
            if (!recore.isEmpty()) {
                verifyArgs.add("-r");
                verifyArgs.add(PathUtils.toModuleArg(verifierDir, convertPaths, recore));
            }
            if (detailsBox.isSelected()) {
                verifyArgs.add("--details");
            }
            verifyArgs.add("--report");
            verifyArgs.add(PathUtils.toModuleArg(verifierDir, convertPaths, reportAbs.toString()));

            pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "verifier args: " + formatArgs(verifyArgs) + "\n"));
            int vc = ProcessRunner.runStreaming(verifierDir, effectivePlatform, "./run.sh", "run.ps1", verifyArgs,
                    s2 -> pub.accept(new GuiEvent(GuiEvent.Kind.LOG, s2)));
            if (vc != 0) {
                failures++;
                pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "[ERROR] Verify failed for " + repoName + " (exit=" + vc + ")\n"));
            }
        }

        return failures == 0 ? 0 : 1;
    }

    private static String repoNameFromPath(Path repoDir) {
        if (repoDir == null) {
            return "repo";
        }
        Path name = repoDir.getFileName();
        if (name == null) {
            return "repo";
        }
        String s = name.toString().trim();
        return s.isEmpty() ? "repo" : s;
    }

    private static Path ensureRepoOutDir(Path baseOutDir, String repoName) {
        if (baseOutDir == null) {
            throw new IllegalArgumentException("baseOutDir is required");
        }
        if (repoName == null || repoName.isBlank()) {
            return baseOutDir;
        }
        Path last = baseOutDir.getFileName();
        if (last != null && repoName.equals(last.toString())) {
            return baseOutDir;
        }
        return baseOutDir.resolve(repoName);
    }

    private int runExtractOnly(String source, String out, Consumer<GuiEvent> pub) throws Exception {
        Path baseDir = Path.of("").toAbsolutePath();
        Path metamodelDir = common.resolveMetamodelDir(baseDir);

        Platform effectivePlatform = common.effectivePlatform();
        boolean convertPaths = common.convertWindowsPaths();

        String mode = Objects.toString(modeBox.getSelectedItem(), "single");
        Path outBaseDirAbs = PathUtils.resolveAgainst(baseDir, convertPaths, out);
        Files.createDirectories(outBaseDirAbs);

        Path sourceAbs = PathUtils.resolveAgainst(baseDir, convertPaths, source);
        pub.accept(new GuiEvent(GuiEvent.Kind.STATUS, "Extracting metamodel…"));

        if (!"multi-repo".equalsIgnoreCase(mode)) {
            String repoName = repoNameFromPath(sourceAbs);
            Path outDirAbs = ensureRepoOutDir(outBaseDirAbs, repoName);
            Files.createDirectories(outDirAbs);
            Path spoonJsonAbs = outDirAbs.resolve("spoon.json");
            pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "Output folder: " + outDirAbs + "\n"));

            ArrayList<String> args = new ArrayList<>();
            args.add("-i");
            args.add(PathUtils.toModuleArg(metamodelDir, convertPaths, sourceAbs.toString()));
            args.add("-m");
            args.add("single");
            args.add("--json");
            args.add("-o");
            args.add(PathUtils.toModuleArg(metamodelDir, convertPaths, spoonJsonAbs.toString()));
            if (!summaryBox.isSelected()) {
                args.add("--no-summary");
            }

            pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "java-metamodel args: " + formatArgs(args) + "\n"));
            return ProcessRunner.runStreaming(metamodelDir, effectivePlatform, "./run.sh", "run.ps1", args,
                    s -> pub.accept(new GuiEvent(GuiEvent.Kind.LOG, s)));
        }

        if (!Files.isDirectory(sourceAbs)) {
            pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "[ERROR] In multi-repo mode, Source must be a directory containing repos: " + sourceAbs + "\n"));
            return 2;
        }

        List<Path> repos;
        try (var s = Files.list(sourceAbs)) {
            repos = s.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName() != null ? p.getFileName().toString() : ""))
                    .toList();
        }

        int failures = 0;
        int idx = 0;
        for (Path repoDir : repos) {
            idx++;
            String repoName = repoNameFromPath(repoDir);
            Path outDirAbs = ensureRepoOutDir(outBaseDirAbs, repoName);
            Files.createDirectories(outDirAbs);
            Path spoonJsonAbs = outDirAbs.resolve("spoon.json");
            pub.accept(new GuiEvent(GuiEvent.Kind.STATUS, "Repo " + idx + "/" + repos.size() + ": " + repoName));
            pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "\n=== REPO " + repoName + " ===\n"));
            pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "Output folder: " + outDirAbs + "\n"));

            ArrayList<String> args = new ArrayList<>();
            args.add("-i");
            args.add(PathUtils.toModuleArg(metamodelDir, convertPaths, repoDir.toString()));
            args.add("-m");
            args.add("single");
            args.add("--json");
            args.add("-o");
            args.add(PathUtils.toModuleArg(metamodelDir, convertPaths, spoonJsonAbs.toString()));
            if (!summaryBox.isSelected()) {
                args.add("--no-summary");
            }

            pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "java-metamodel args: " + formatArgs(args) + "\n"));
            int ec = ProcessRunner.runStreaming(metamodelDir, effectivePlatform, "./run.sh", "run.ps1", args,
                    s2 -> pub.accept(new GuiEvent(GuiEvent.Kind.LOG, s2)));
            if (ec != 0) {
                failures++;
                pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "[ERROR] Extract failed for " + repoName + " (exit=" + ec + ")\n"));
            }
        }

        return failures == 0 ? 0 : 1;
    }

    private int runVerifyOnly(String instance, String out, Consumer<GuiEvent> pub) throws Exception {
        Path baseDir = Path.of("").toAbsolutePath();
        Path verifierDir = common.resolveVerifierDir(baseDir);

        Platform effectivePlatform = common.effectivePlatform();
        boolean convertPaths = common.convertWindowsPaths();

        Path outDirAbs = PathUtils.resolveAgainst(baseDir, convertPaths, out).resolve("verifier");
        Files.createDirectories(outDirAbs);
        Path reportAbs = outDirAbs.resolve("report.json");

        Path instanceAbs = PathUtils.resolveAgainst(baseDir, convertPaths, instance);

        pub.accept(new GuiEvent(GuiEvent.Kind.STATUS, "Verifying invariants…"));

        ArrayList<String> args = new ArrayList<>();
        args.add("-i");
        args.add(PathUtils.toModuleArg(verifierDir, convertPaths, instanceAbs.toString()));
        args.add("-o");
        args.add(PathUtils.toModuleArg(verifierDir, convertPaths, outDirAbs.toString()));

        String recore = recoreField.getText().trim();
        if (!recore.isEmpty()) {
            args.add("-r");
            args.add(PathUtils.toModuleArg(verifierDir, convertPaths, recore));
        }

        if (detailsBox.isSelected()) {
            args.add("--details");
        }
        args.add("--report");
        args.add(PathUtils.toModuleArg(verifierDir, convertPaths, reportAbs.toString()));

        pub.accept(new GuiEvent(GuiEvent.Kind.LOG, "verifier args: " + formatArgs(args) + "\n"));

        return ProcessRunner.runStreaming(verifierDir, effectivePlatform, "./run.sh", "run.ps1", args,
                s -> pub.accept(new GuiEvent(GuiEvent.Kind.LOG, s)));
    }

    private static String formatArgs(List<String> args) {
        if (args == null || args.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String a : args) {
            if (a == null || a.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(quoteForLog(a));
        }
        return sb.toString();
    }

    private static String quoteForLog(String s) {
        if (s == null) {
            return "";
        }
        if (s.indexOf(' ') < 0 && s.indexOf('\t') < 0) {
            return s;
        }
        return '"' + s.replace("\"", "\\\"") + '"';
    }

    private void setBusy(boolean busy) {
        runButton.setEnabled(!busy);
        extractButton.setEnabled(!busy);
        verifyButton.setEnabled(!busy);
        progressBar.setVisible(busy);
    }

    private void setStatus(String s) {
        statusLabel.setText(s == null || s.isBlank() ? "" : s);
    }

    private void appendLog(String s) {
        logArea.append(s);
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void chooseDirectory(JTextField target) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (!target.getText().isBlank()) {
            fc.setCurrentDirectory(new java.io.File(target.getText().trim()));
        }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            target.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseFile(JTextField target) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (!target.getText().isBlank()) {
            java.io.File current = new java.io.File(target.getText().trim());
            if (current.getParentFile() != null) {
                fc.setCurrentDirectory(current.getParentFile());
            }
        }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            target.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void openOutputFolder() {
        String out = outField.getText().trim();
        if (out.isEmpty()) {
            appendLog("[WARN] Output dir is empty.\n");
            return;
        }

        try {
            Path baseDir = Path.of("").toAbsolutePath();
            boolean convert = common.convertWindowsPaths();
            Path resolved = PathUtils.resolveAgainst(baseDir, convert, out);
            if (!Files.exists(resolved)) {
                appendLog("[WARN] Output dir does not exist: " + resolved + "\n");
                return;
            }
            if (!Desktop.isDesktopSupported()) {
                appendLog("[WARN] Desktop integration not supported on this platform.\n");
                return;
            }
            Desktop.getDesktop().open(resolved.toFile());
        } catch (Exception ex) {
            appendLog("[WARN] Failed to open output folder: " + ex.getMessage() + "\n");
        }
    }

    private void saveLogAs() {
        JFileChooser fc = new JFileChooser();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        fc.setSelectedFile(new java.io.File("pipeline-log-" + ts + ".txt"));

        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path out = fc.getSelectedFile().toPath();
        try {
            Files.writeString(out, logArea.getText(), StandardCharsets.UTF_8);
            appendLog("[INFO] Log saved: " + out + "\n");
        } catch (IOException ex) {
            appendLog("[WARN] Failed to save log: " + ex.getMessage() + "\n");
        }
    }
}
