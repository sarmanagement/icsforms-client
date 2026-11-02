/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

/**
 * Keeps one full-form editor bound to the incident and model revision it loaded.
 */
final class EditorCommitGuard {
	private final AppController controller;
	private AppController.EditorCommitBinding binding;

	/**
	 * Captures the initial editor binding.
	 *
	 * @param controller owning incident controller.
	 */
	EditorCommitGuard(AppController controller) {
		this.controller = controller;
		binding = controller.captureEditorCommitBinding();
		System.err.println("Created EditorCommitGuard " + System.identityHashCode(this)
				+ " from " + Thread.currentThread().getStackTrace()[3]);
	}

	/**
	 * Applies an editor write only when its loaded model revision is still current.
	 *
	 * @param source editor responsible for linked values.
	 * @param edit synchronous editor write.
	 * @return true when the write was accepted.
	 */
	boolean commit(AppController.LinkSource source, Runnable edit) {
		if (controller.isEditorCommitInProgress()) {
			controller.noteEditorCommit(this);
			edit.run();
			return true;
		}
		if (!controller.commitEditorIfCurrent(binding, source, edit)) {
			return false;
		}
		rebase();
		return true;
	}

	/**
	 * Commits one field against the current model revision without flushing stale editor controls.
	 *
	 * @param edit synchronous update to one field.
	 * @return true when the incident binding is still current.
	 */
	boolean commitField(Runnable edit) {
		if (controller.isEditorCommitInProgress()) {
			controller.noteEditorCommit(this);
			edit.run();
			return true;
		}
		if (!controller.commitEditorIfCurrent(binding, AppController.LinkSource.NONE, edit)) {
			return false;
		}
		rebase();
		return true;
	}

	/**
	 * Binds the editor to the current incident and model revision after refresh.
	 */
	void rebase() {
		binding = controller.captureEditorCommitBinding();
	}

	/**
	 * Restores a previously observed binding while retained drafts await confirmation.
	 *
	 * @param observedBinding revision associated with the preserved draft.
	 */
	void rebase(AppController.EditorCommitBinding observedBinding) {
		binding = observedBinding;
	}

	/**
	 * Tests whether the editor still observes the current incident and model revision.
	 *
	 * @return true when no model write has occurred since the editor was bound.
	 */
	boolean isCurrent() {
		boolean current = controller.isCurrent(binding.incidentBinding())
				&& binding.revision() == controller.captureEditorCommitBinding().revision();
		if (!current) {
			System.err.println("Stale EditorCommitGuard " + System.identityHashCode(this) + " at " + binding
					+ ", current " + controller.captureEditorCommitBinding());
		}
		return current;
	}
}
