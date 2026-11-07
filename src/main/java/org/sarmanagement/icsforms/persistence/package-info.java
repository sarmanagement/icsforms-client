/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Store-independent incident persistence contracts and a multi-incident JSON
 * file store. Each file has its own backup; legacy single-file workspaces are
 * imported without changing the source. Opaque snapshot revisions provide
 * whole-document concurrency independently of paths and optional edit locks.
 * Partial persistence, outboxes and synchronization cursors are intentionally
 * unspecified. See {@link org.sarmanagement.icsforms.persistence.IncidentStore}
 * for identity, mutation, lifecycle and local-transfer guarantees.
 */
package org.sarmanagement.icsforms.persistence;
