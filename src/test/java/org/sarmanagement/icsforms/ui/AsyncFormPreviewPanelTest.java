/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.pdf.PdfFormRenderer;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies asynchronous preview failure and stale-result handling. */
class AsyncFormPreviewPanelTest {
	@Test
	void failureLeavesPreviewSurfaceAvailable() throws Exception {
		AsyncFormPreviewPanel panel = new AsyncFormPreviewPanel(new PdfFormRenderer() {
			@Override
			public String getFormKey() {
				return "Failure test";
			}

			@Override
			public void render(AppData data, Path outputFile) throws IOException {
				throw new IOException("expected failure");
			}
		});

		SwingUtilities.invokeAndWait(() -> panel.requestPreview(new AppData(), "failure-key"));
		waitFor(() -> panel.previewStateText().contains("expected failure"));

		assertEquals(0, panel.displayedPageCount());
		assertTrue(panel.previewStateText().contains("Preview unavailable"));
		SwingUtilities.invokeAndWait(panel::cancel);
	}

	@Test
	void obsoleteCompletionCannotReplaceNewerPreview() throws Exception {
		AtomicInteger invocation = new AtomicInteger();
		CountDownLatch firstStarted = new CountDownLatch(1);
		PdfFormRenderer renderer = new PdfFormRenderer() {
			@Override
			public String getFormKey() {
				return "Stale test";
			}

			@Override
			public void render(AppData data, Path outputFile) throws IOException {
				int current = invocation.incrementAndGet();
				if (current == 1) {
					firstStarted.countDown();
					try {
						Thread.sleep(350);
					} catch (InterruptedException ignored) {
						// Deliberately emulate a renderer that completes after cancellation.
					}
				}
				try (PDDocument document = new PDDocument()) {
					document.addPage(new PDPage());
					if (current > 1) {
						document.addPage(new PDPage());
					}
					document.save(outputFile.toFile());
				}
			}
		};
		AsyncFormPreviewPanel panel = new AsyncFormPreviewPanel(renderer);
		SwingUtilities.invokeAndWait(() -> panel.requestPreview(new AppData(), "older"));
		assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

		SwingUtilities.invokeAndWait(() -> panel.requestPreview(new AppData(), "newer"));
		waitFor(() -> panel.displayedPageCount() == 2);

		assertEquals(2, panel.displayedPageCount());
		SwingUtilities.invokeAndWait(panel::cancel);
	}

	@Test
	void contentChangeInvalidatesFirstPreviewStillRendering() throws Exception {
		CountDownLatch renderStarted = new CountDownLatch(1);
		CountDownLatch renderCompleted = new CountDownLatch(1);
		AsyncFormPreviewPanel panel = new AsyncFormPreviewPanel(new PdfFormRenderer() {
			@Override
			public String getFormKey() {
				return "In-flight invalidation test";
			}

			@Override
			public void render(AppData data, Path outputFile) throws IOException {
				renderStarted.countDown();
				try {
					Thread.sleep(250);
				} catch (InterruptedException ignored) {
					// Deliberately emulate a renderer that finishes after cancellation.
				}
				try (PDDocument document = new PDDocument()) {
					document.addPage(new PDPage());
					document.save(outputFile.toFile());
				} finally {
					renderCompleted.countDown();
				}
			}
		});
		SwingUtilities.invokeAndWait(() -> panel.requestPreview(new AppData(), "in-flight-old-content"));
		assertTrue(renderStarted.await(2, TimeUnit.SECONDS));

		SwingUtilities.invokeAndWait(panel::markOutOfDate);
		assertTrue(renderCompleted.await(2, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
		});

		assertEquals(0, panel.displayedPageCount());
		assertTrue(panel.previewStateText().contains("Structured information changed"));
		SwingUtilities.invokeAndWait(panel::cancel);
	}

	@Test
	void rendersUsingSystemTemporaryStorage() throws Exception {
		AtomicReference<Path> output = new AtomicReference<>();
		AsyncFormPreviewPanel panel = new AsyncFormPreviewPanel(new PdfFormRenderer() {
			@Override
			public String getFormKey() {
				return "Temporary storage test";
			}

			@Override
			public void render(AppData data, Path outputFile) throws IOException {
				output.set(outputFile);
				try (PDDocument document = new PDDocument()) {
					document.addPage(new PDPage());
					document.save(outputFile.toFile());
				}
			}
		});

		SwingUtilities.invokeAndWait(() -> panel.requestPreview(new AppData(), "temporary-storage"));
		waitFor(() -> panel.displayedPageCount() == 1);

		assertEquals(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize(),
				output.get().getParent().toAbsolutePath().normalize());
		SwingUtilities.invokeAndWait(panel::cancel);
	}

	private void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
			Thread.sleep(25);
			SwingUtilities.invokeAndWait(() -> {
			});
		}
		assertTrue(condition.getAsBoolean());
	}
}
