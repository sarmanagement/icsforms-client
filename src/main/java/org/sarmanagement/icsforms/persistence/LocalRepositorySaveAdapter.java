/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.sarmanagement.icsforms.model.AppData;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Save-only compatibility destination for the legacy single-document repository.
 * It preserves the repository's concurrency guarantees and opaque, unchanged save
 * token; it does not expose incident management or add revision validation.
 *
 * @deprecated new application code should use a full incident store.
 */
@Deprecated
public final class LocalRepositorySaveAdapter implements IncidentStore {
	private final LocalRepository repository;

	/**
	 * Wraps a legacy destination without exposing editor-owned data to its worker.
	 *
	 * @param repository legacy document repository.
	 */
	public LocalRepositorySaveAdapter(LocalRepository repository) {
		this.repository = Objects.requireNonNull(repository);
	}

	/** {@inheritDoc} */
	@Override
	public RevisionToken save(IncidentId id, AppData data, RevisionToken expected) {
		repository.save(data);
		return expected;
	}

	/** {@inheritDoc} */
	@Override
	public IncidentId create(AppData data) {
		throw new UnsupportedOperationException("Legacy adapter supports saves only.");
	}

	/** {@inheritDoc} */
	@Override
	public StoredIncident loadSnapshot(IncidentId id) {
		throw new UnsupportedOperationException("Legacy adapter supports saves only.");
	}

	/** {@inheritDoc} */
	@Override
	public List<IncidentSummary> list() {
		throw new UnsupportedOperationException("Legacy adapter supports saves only.");
	}

	/** {@inheritDoc} */
	@Override
	public Optional<IncidentId> importFile(Path jsonFile) {
		throw new UnsupportedOperationException("Legacy adapter supports saves only.");
	}

	/** {@inheritDoc} */
	@Override
	public void exportFile(IncidentId id, Path target) {
		throw new UnsupportedOperationException("Legacy adapter supports saves only.");
	}

	/** {@inheritDoc} */
	@Override
	public StoreDescriptor describe() {
		return new StoreDescriptor("Legacy repository", repository.getFilePath().toString());
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		// The legacy repository has no resources to release.
	}
}
