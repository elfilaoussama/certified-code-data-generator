package com.pipeline.orchestrator;

import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;

final class PipelineGui {

    private PipelineGui() {
    }

    static void launch(PipelineCommands.CommonOptions common, String defaultSource, String defaultOut) {
        SwingUtilities.invokeLater(() -> new PipelineGuiFrame(common, defaultSource, defaultOut).setVisible(true));
    }

    static void launchAndWait(PipelineCommands.CommonOptions common, String defaultSource, String defaultOut) throws InterruptedException {
        CountDownLatch closed = new CountDownLatch(1);

        SwingUtilities.invokeLater(() -> {
            try {
                PipelineGuiFrame frame = new PipelineGuiFrame(common, defaultSource, defaultOut);
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        closed.countDown();
                    }

                    @Override
                    public void windowClosing(WindowEvent e) {
                        // Ensure the latch is released even if windowClosed isn't fired for some reason.
                        closed.countDown();
                    }
                });
                frame.setVisible(true);
            } catch (Throwable t) {
                t.printStackTrace(System.err);
                closed.countDown();
            }
        });

        closed.await();
    }
}
