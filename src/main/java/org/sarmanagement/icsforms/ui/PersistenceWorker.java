/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Dialog;
import java.awt.Window;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

/**
 * Runs detached startup and dialog persistence operations outside Swing dispatch.
 * The modal wait pumps Swing's secondary event loop while blocking operator input,
 * preserving synchronous callers without permitting reentrant dialog actions.
 */
public final class PersistenceWorker {
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "incident-dialog-persistence");
		thread.setDaemon(true);
		return thread;
	});

	/** Prevents construction of the shared worker utility. */
	private PersistenceWorker() {
	}

	/**
	 * Completes a persistence operation before returning, keeping Swing responsive.
	 * Suppliers must not access Swing components or editor-owned mutable documents.
	 *
	 * @param <T> result type.
	 * @param owner window whose input is blocked during the operation, or null.
	 * @param operation detached persistence operation.
	 * @return completed operation result.
	 * @throws IllegalStateException if waiting is interrupted.
	 * @throws RuntimeException if persistence fails; the original backend exception is preserved.
	 */
	public static <T> T call(Window owner, Supplier<T> operation) {
		JDialog waiting = null;
		if (SwingUtilities.isEventDispatchThread()) {
			waiting = new JDialog(owner, "Working with saved incidents", Dialog.ModalityType.APPLICATION_MODAL);
			waiting.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			JLabel label = new JLabel("Please wait…");
			label.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
			waiting.add(label);
			waiting.pack();
			waiting.setLocationRelativeTo(owner);
		}
		JDialog blocker = waiting;
		FutureTask<T> task = new FutureTask<>(operation::get) {
			/** Releases the modal wait on Swing dispatch after persistence completes. */
			@Override
			protected void done() {
				if (blocker != null) {
					SwingUtilities.invokeLater(blocker::dispose);
				}
			}
		};
		IO.execute(task);
		try {
			if (waiting != null) {
				waiting.setVisible(true);
			}
			return task.get();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for incident persistence", exception);
		} catch (ExecutionException exception) {
			if (exception.getCause() instanceof RuntimeException failure) {
				throw failure;
			}
			if (exception.getCause() instanceof Error failure) {
				throw failure;
			}
			throw new IllegalStateException("Incident persistence failed", exception.getCause());
		} finally {
			if (waiting != null) {
				waiting.dispose();
			}
		}
	}
}
