/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sarmanagement.icsforms.model.AppData;

import java.util.Objects;
import java.util.Optional;

/**
 * Whole-document adapter for today's store API. Capture on the editor thread after
 * flushing editors; the worker owns the resulting document until completion.
 *
 * @param incidentId target incident
 * @param generation editor binding generation
 * @param editVersion represented local edit version
 * @param expectedRevision expected opaque store revision
 * @param snapshot exclusively owned detached document, never the live editor model
 * @param store destination store
 */
public record FullDocumentSnapshot(IncidentId incidentId, long generation, long editVersion,
		RevisionToken expectedRevision, AppData snapshot, IncidentStore store) implements SaveRequest {
	private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
			.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
			.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

	/**
	 * Validates the work envelope. The caller transfers ownership of the snapshot.
	 *
	 * @param incidentId target incident
	 * @param generation editor binding generation
	 * @param editVersion represented local edit version
	 * @param expectedRevision expected opaque store revision
	 * @param snapshot exclusively owned detached document
	 * @param store destination store
	 */
	public FullDocumentSnapshot {
		Objects.requireNonNull(incidentId);
		Objects.requireNonNull(expectedRevision);
		Objects.requireNonNull(snapshot);
		Objects.requireNonNull(store);
	}

	/**
	 * Copies the complete persisted model, including legacy fields, with the
	 * application's Jackson model contract. This method performs no store I/O.
	 *
	 * @param live editor-owned model, read only on its owner thread
	 * @return detached document with no mutable references to the source
	 */
	public static AppData detach(AppData live) {
		return SNAPSHOT_MAPPER.convertValue(live, AppData.class);
	}

	/**
	 * Reuses this exclusively owned payload with an accepted predecessor token.
	 *
	 * @param revision accepted predecessor revision
	 * @return rebased snapshot work
	 */
	@Override
	public SaveRequest withExpectedRevision(RevisionToken revision) {
		return new FullDocumentSnapshot(incidentId, generation, editVersion, revision, snapshot, store);
	}

	/**
	 * Carries the committed document's legacy numeric metadata into the exclusively
	 * owned queued snapshot. This preserves legacy atomic-save bridges without
	 * parsing or making assumptions about the backend's opaque accepted token.
	 *
	 * @param predecessor completed work from this binding
	 * @param revision accepted predecessor revision
	 * @return snapshot prepared against its durable predecessor
	 */
	@Override
	public SaveRequest afterPredecessor(SaveRequest predecessor, RevisionToken revision) {
		if (predecessor instanceof FullDocumentSnapshot full && incidentId.equals(full.incidentId)
				&& generation == full.generation && store == full.store) {
			AppData rebased = detach(snapshot);
			rebased.setRevision(full.snapshot.getRevision());
			return new FullDocumentSnapshot(incidentId, generation, editVersion, revision, rebased, store);
		}
		return withExpectedRevision(revision);
	}

	/**
	 * Full snapshots may replace a preceding full snapshot of the same binding.
	 *
	 * @param newer next queued work
	 * @return latest snapshot when compatible, otherwise empty
	 */
	@Override
	public Optional<SaveRequest> coalesce(SaveRequest newer) {
		if (newer instanceof FullDocumentSnapshot next && incidentId.equals(next.incidentId)
				&& generation == next.generation && editVersion <= next.editVersion
				&& expectedRevision.equals(next.expectedRevision) && store == next.store) {
			return Optional.of(next);
		}
		return Optional.empty();
	}

	/**
	 * Writes only the detached document and accepts the backend's opaque token.
	 *
	 * @return committed revision
	 */
	@Override
	public RevisionToken persist() {
		return store.save(incidentId, snapshot, expectedRevision);
	}
}
