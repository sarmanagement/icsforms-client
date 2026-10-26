/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** An external incident document failed validation before any incident was created. */
public class ImportValidationException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private final Path sourceFile;
	private final List<Problem> problems;

	/** Classification of an import diagnostic. */
	public enum Severity {
		ERROR, WARNING
	}

	/**
	 * A safe, actionable diagnostic that does not include document values.
	 *
	 * @param severity diagnostic classification
	 * @param path JSON property path, rooted at {@code $}
	 * @param message explanation of the issue
	 * @param suggestedFix recommended corrective action
	 */
	public record Problem(Severity severity, String path, String message, String suggestedFix) {
	}

	/**
	 * Creates a validation failure retaining its original parsing cause.
	 *
	 * @param sourceFile external source path
	 * @param problems immutable diagnostic snapshot
	 * @param cause original parsing or binding failure, if any
	 */
	public ImportValidationException(Path sourceFile, List<Problem> problems, Throwable cause) {
		super("Cannot import incident from " + sourceFile + ": " + problems.size() + " problem(s)", cause);
		this.sourceFile = Objects.requireNonNull(sourceFile);
		this.problems = List.copyOf(problems);
	}

	/**
	 * Returns the original source path.
	 *
	 * @return source path
	 */
	public Path getSourceFile() {
		return sourceFile;
	}

	/**
	 * Returns the immutable diagnostic snapshot.
	 *
	 * @return structured problems
	 */
	public List<Problem> getProblems() {
		return problems;
	}
}
