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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Verifies asynchronous preview failure and stale-result handling. */
class AsyncFormPreviewPanelTest {
	@Test
	void narrowInputsReusePagesAndFailedReplacementCanRetry() throws Exception {
		AppData source = new AppData();
		AtomicInteger renders = new AtomicInteger();
		CountDownLatch replacementStarted = new CountDownLatch(1);
		CountDownLatch releaseReplacement = new CountDownLatch(1);
		AsyncFormPreviewPanel panel = new AsyncFormPreviewPanel(new PdfFormRenderer() {
			/** {@inheritDoc} */
			@Override
			public String getFormKey() {
				return "Narrow input retry regression";
			}

			/** {@inheritDoc} */
			@Override
			public void render(AppData data, Path outputFile) throws IOException {
				int invocation = renders.incrementAndGet();
				if (invocation == 2) {
					replacementStarted.countDown();
					try {
						if (!releaseReplacement.await(10, TimeUnit.SECONDS)) {
							throw new IOException("replacement release timed out");
						}
					} catch (InterruptedException exception) {
						Thread.currentThread().interrupt();
						throw new IOException(exception);
					}
				}
				if (invocation == 3) {
					throw new IOException("expected retryable failure");
				}
				try (PDDocument document = new PDDocument()) {
					for (int page = 0; page < invocation; page++) {
						document.addPage(new PDPage());
					}
					document.save(outputFile.toFile());
				}
			}
		});
		try {
			SwingUtilities.invokeAndWait(() -> panel.requestPreview(source, "same-review", source.getMedicalPlan()));
			waitFor(() -> panel.displayedPageCount() == 1);
			SwingUtilities.invokeAndWait(() -> {
				Object generation = Ics205PanelTest.field(panel, "requestGeneration");
				Object pages = ((javax.swing.JPanel) Ics205PanelTest.field(panel, "pages")).getComponent(0);
				source.getForm201().setMapSketch("irrelevant");
				panel.requestPreview(source, "same-review", source.getMedicalPlan());
				assertEquals(generation, Ics205PanelTest.field(panel, "requestGeneration"));
				org.junit.jupiter.api.Assertions.assertSame(pages,
						((javax.swing.JPanel) Ics205PanelTest.field(panel, "pages")).getComponent(0));
				assertEquals(1, renders.get());
				source.getMedicalPlan().setCall911Procedure("Relevant change");
				panel.requestPreview(source, "same-review", source.getMedicalPlan());
			});
			assertTrue(replacementStarted.await(5, TimeUnit.SECONDS));
			SwingUtilities.invokeAndWait(() -> {
				assertEquals(1, panel.displayedPageCount(), "previous pages remain while replacement renders");
				assertTrue(panel.previewStateText().contains("Rendering"));
			});
			releaseReplacement.countDown();
			waitFor(() -> panel.displayedPageCount() == 2);
			SwingUtilities.invokeAndWait(() -> {
				source.getMedicalPlan().setCall911Procedure("Retryable change");
				panel.requestPreview(source, "same-review", source.getMedicalPlan());
			});
			waitFor(() -> panel.previewStateText().contains("expected retryable failure"));
			SwingUtilities.invokeAndWait(() -> {
				assertEquals(2, panel.displayedPageCount(), "failed replacement does not clear completed pages");
				panel.requestPreview(source, "same-review", source.getMedicalPlan());
			});
			waitFor(() -> panel.displayedPageCount() == 4);
			assertEquals(4, renders.get());
		} finally {
			releaseReplacement.countDown();
			SwingUtilities.invokeAndWait(panel::cancel);
		}
	}

	@Test
	void snapshotPreservesTCardsWithoutGeneratingLiveIdentities() throws Exception {
		AppData source = new AppData();
		var card = new org.sarmanagement.icsforms.model.TCard();
		card.setPersonName("Recorded responder");
		source.getTCards().add(card);
		AtomicReference<AppData> rendered = new AtomicReference<>();
		AsyncFormPreviewPanel panel = new AsyncFormPreviewPanel(new PdfFormRenderer() {
			/** {@inheritDoc} */
			@Override
			public String getFormKey() {
				return "T-card snapshot test";
			}

			/** {@inheritDoc} */
			@Override
			public void render(AppData data, Path outputFile) throws IOException {
				rendered.set(data);
				try (PDDocument document = new PDDocument()) {
					document.addPage(new PDPage());
					document.save(outputFile.toFile());
				}
			}
		});
		try {
			SwingUtilities.invokeAndWait(() -> panel.requestPreview(source, "t-card-snapshot"));
			waitFor(() -> panel.displayedPageCount() == 1);
			assertEquals(1, rendered.get().getTCards().size());
			assertEquals("Recorded responder", rendered.get().getTCards().get(0).getPersonName());
			assertNotSame(card, rendered.get().getTCards().get(0));
			assertNull(card.peekResourceId());
		} finally {
			SwingUtilities.invokeAndWait(panel::cancel);
		}
	}

	@Test
	void failureLeavesPreviewSurfaceAvailable() throws Exception {
		AsyncFormPreviewPanel panel = new AsyncFormPreviewPanel(new PdfFormRenderer() {
			/** {@inheritDoc} */
			@Override
			public String getFormKey() {
				return "Failure test";
			}

			/** {@inheritDoc} */
			@Override
			public void render(AppData data, Path outputFile) throws IOException {
				throw new IOException("expected failure");
			}
		});

		SwingUtilities.invokeAndWait(() -> panel.requestPreview(new AppData(), "failure-key"));
		waitFor(() -> panel.previewStateText().contains("expected failure"));

		assertEquals(0, panel.displayedPageCount());
		assertTrue(panel.previewStateText().contains("PDF unavailable"));
		SwingUtilities.invokeAndWait(panel::cancel);
	}

	@Test
	void obsoleteCompletionCannotReplaceNewerPreview() throws Exception {
		AtomicInteger invocation = new AtomicInteger();
		CountDownLatch firstStarted = new CountDownLatch(1);
		PdfFormRenderer renderer = new PdfFormRenderer() {
			/** {@inheritDoc} */
			@Override
			public String getFormKey() {
				return "Stale test";
			}

			/** {@inheritDoc} */
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
			/** {@inheritDoc} */
			@Override
			public String getFormKey() {
				return "In-flight invalidation test";
			}

			/** {@inheritDoc} */
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
			/** {@inheritDoc} */
			@Override
			public String getFormKey() {
				return "Temporary storage test";
			}

			/** {@inheritDoc} */
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

	@Test
	void unchangedRequestsReuseWorkButReviewChangesRenderAgain() throws Exception {
		AtomicInteger invocation = new AtomicInteger();
		AsyncFormPreviewPanel panel = new AsyncFormPreviewPanel(new PdfFormRenderer() {
			/** {@inheritDoc} */
			@Override
			public String getFormKey() {
				return "Current review test";
			}

			/** {@inheritDoc} */
			@Override
			public void render(AppData data, Path outputFile) throws IOException {
				invocation.incrementAndGet();
				try (PDDocument document = new PDDocument()) {
					document.addPage(new PDPage());
					document.save(outputFile.toFile());
				}
			}
		});
		AppData data = new AppData();
		SwingUtilities.invokeAndWait(() -> {
			panel.requestPreview(data, "same-content");
			panel.requestPreview(data, "same-content");
		});
		waitFor(() -> panel.displayedPageCount() == 1);
		assertEquals(1, invocation.get());
		SwingUtilities.invokeAndWait(() -> {
			data.recordPlanReview(org.sarmanagement.icsforms.model.PlanReviewArea.HUMAN_MEDICAL, "Reviewer", null);
			panel.requestPreview(data, "same-content");
		});
		waitFor(() -> invocation.get() == 2 && panel.previewStateText().contains("Current PDF"));
		assertTrue(panel.previewStateText().contains("Current PDF"));
		SwingUtilities.invokeAndWait(panel::cancel);
	}

	@Test
	void keepsPreviousPagesWhileReplacementRenders() throws Exception {
		CountDownLatch replacementStarted = new CountDownLatch(1);
		CountDownLatch releaseReplacement = new CountDownLatch(1);
		AtomicInteger invocation = new AtomicInteger();
		AsyncFormPreviewPanel panel = new AsyncFormPreviewPanel(new PdfFormRenderer() {
			/** {@inheritDoc} */
			@Override
			public String getFormKey() {
				return "Retained pages test";
			}

			/** {@inheritDoc} */
			@Override
			public void render(AppData data, Path outputFile) throws IOException {
				if (invocation.incrementAndGet() == 2) {
					replacementStarted.countDown();
					try {
						if (!releaseReplacement.await(5, TimeUnit.SECONDS)) {
							throw new IOException("Replacement timed out");
						}
					} catch (InterruptedException exception) {
						Thread.currentThread().interrupt();
						throw new IOException(exception);
					}
				}
				try (PDDocument document = new PDDocument()) {
					document.addPage(new PDPage());
					document.save(outputFile.toFile());
				}
			}
		});
		try {
			SwingUtilities.invokeAndWait(() -> panel.requestPreview(new AppData(), "first"));
			waitFor(() -> panel.displayedPageCount() == 1);
			SwingUtilities.invokeAndWait(() -> panel.requestPreview(new AppData(), "second"));
			assertTrue(replacementStarted.await(2, TimeUnit.SECONDS));
			SwingUtilities.invokeAndWait(() -> {
				assertEquals(1, panel.displayedPageCount());
				assertTrue(panel.previewStateText().contains("Rendering"));
			});
			releaseReplacement.countDown();
			waitFor(() -> panel.previewStateText().contains("Current PDF"));
		} finally {
			releaseReplacement.countDown();
			SwingUtilities.invokeAndWait(panel::cancel);
		}
	}

	/**
	 * Waits for an asynchronous PDF state while processing queued Swing completions.
	 *
	 * @param condition expected state.
	 * @throws Exception when the EDT cannot be synchronized or the wait is interrupted.
	 */
	static void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
		CountDownLatch completed = new CountDownLatch(1);
		javax.swing.Timer timer = new javax.swing.Timer(10, event -> {
			if (condition.getAsBoolean()) {
				completed.countDown();
			}
		});
		try {
			SwingUtilities.invokeAndWait(timer::start);
			assertTrue(completed.await(10, TimeUnit.SECONDS), "bounded asynchronous completion");
		} finally {
			SwingUtilities.invokeAndWait(timer::stop);
		}
	}
}
