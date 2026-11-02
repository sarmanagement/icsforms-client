/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

import javax.swing.SwingUtilities;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.Window;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides deterministic cleanup and asynchronous error reporting for Swing tests.
 */
public class SwingTestLifecycleExtension
		implements BeforeEachCallback, AfterEachCallback, TestExecutionExceptionHandler {
	private static final ConcurrentLinkedQueue<AppController> CONTROLLERS = new ConcurrentLinkedQueue<>();
	private static final ConcurrentLinkedQueue<Throwable> FAILURES = new ConcurrentLinkedQueue<>();
	private static final AtomicBoolean EVENT_QUEUE_INSTALLED = new AtomicBoolean();
	private Thread.UncaughtExceptionHandler previousUncaughtExceptionHandler;

	/**
	 * Registers a controller for test teardown and makes autosave failures visible.
	 *
	 * @param controller controller owned by the current test.
	 * @return the same controller for use by the test.
	 */
	public static AppController track(AppController controller) {
		CONTROLLERS.add(controller);
		controller.setAutosaveErrorHandler(exception -> {
			FAILURES.add(new AssertionError("Unexpected autosave persistence failure", exception));
		});
		return controller;
	}

	/**
	 * Drains asynchronous failures captured for the current test.
	 *
	 * @return captured asynchronous failures.
	 */
	static List<Throwable> drainCapturedFailures() {
		List<Throwable> failures = new ArrayList<>();
		Throwable failure;
		while ((failure = FAILURES.poll()) != null) {
			failures.add(failure);
		}
		return failures;
	}

	/**
	 * Installs test-scoped Swing and uncaught-exception handlers.
	 *
	 * @param context current test context.
	 * @throws Exception when the Swing event queue cannot be installed.
	 */
	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		CONTROLLERS.clear();
		FAILURES.clear();
		previousUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> FAILURES.add(
				new AssertionError("Uncaught exception on thread " + thread.getName(), exception)));
		if (EVENT_QUEUE_INSTALLED.compareAndSet(false, true)) {
			runOnEdt(() -> Toolkit.getDefaultToolkit().getSystemEventQueue().push(new CapturingEventQueue()));
		}
	}

	/**
	 * Disposes all windows, closes registered controllers, and fails on asynchronous errors.
	 *
	 * @param context current test context.
	 * @throws Exception when teardown fails or an asynchronous exception was recorded.
	 */
	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		try {
			runOnEdt(() -> {
				for (Window window : Window.getWindows()) {
					if (window instanceof MainFrame frame) {
						trackControllerFromFrame(frame);
					}
					window.dispose();
				}
				for (AppController controller : CONTROLLERS) {
					controller.close();
				}
			});
			runOnEdt(() -> {
			});
			if (!FAILURES.isEmpty()) {
				AssertionError failure = new AssertionError("Unexpected asynchronous Swing/test exception");
				FAILURES.forEach(failure::addSuppressed);
				throw failure;
			}
		} finally {
			CONTROLLERS.clear();
			FAILURES.clear();
			Thread.setDefaultUncaughtExceptionHandler(previousUncaughtExceptionHandler);
		}
	}

	/**
	 * Prints all thread stacks when a bounded test execution times out.
	 *
	 * @param context current test context.
	 * @param throwable exception raised by test execution.
	 * @throws Throwable the original test exception.
	 */
	@Override
	public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
		if (throwable instanceof TimeoutException) {
			printThreadDump(context);
		}
		throw throwable;
	}

	/**
	 * Applies the test failure handler to the controller owned by a main window.
	 *
	 * @param frame window whose controller must be released.
	 */
	private static void trackControllerFromFrame(MainFrame frame) {
		try {
			Field field = MainFrame.class.getDeclaredField("controller");
			field.setAccessible(true);
			track((AppController) field.get(frame));
		} catch (ReflectiveOperationException exception) {
			FAILURES.add(new AssertionError("Unable to close the test window controller", exception));
		}
	}

	/**
	 * Runs a cleanup operation on Swing's event dispatch thread.
	 *
	 * @param operation operation to execute.
	 * @throws Exception when the operation fails.
	 */
	private static void runOnEdt(Runnable operation) throws Exception {
		if (SwingUtilities.isEventDispatchThread()) {
			operation.run();
			return;
		}
		try {
			SwingUtilities.invokeAndWait(operation);
		} catch (InvocationTargetException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof Exception checkedException) {
				throw checkedException;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException(cause);
		}
	}

	/**
	 * Writes a diagnostic snapshot of every live thread to standard error.
	 *
	 * @param context timed-out test context.
	 */
	private static void printThreadDump(ExtensionContext context) {
		StringBuilder dump = new StringBuilder("Thread dump after timeout in ")
				.append(context.getDisplayName()).append(':').append(System.lineSeparator());
		for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
			dump.append('"').append(entry.getKey().getName()).append("\" ")
					.append(entry.getKey().getState()).append(System.lineSeparator());
			for (StackTraceElement frame : entry.getValue()) {
				dump.append("\tat ").append(frame).append(System.lineSeparator());
			}
		}
		System.err.print(dump);
	}

	/**
	 * Captures any exception escaping Swing event dispatch for the active test.
	 */
	private static final class CapturingEventQueue extends EventQueue {
		/**
		 * Dispatches one event while recording failures for test teardown.
		 *
		 * @param event event to dispatch.
		 */
		@Override
		protected void dispatchEvent(java.awt.AWTEvent event) {
			try {
				super.dispatchEvent(event);
			} catch (Throwable exception) {
				FAILURES.add(new AssertionError("Uncaught exception on the Swing event dispatch thread", exception));
			}
		}
	}
}
