/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Host-mode secure transport, device admission and lifecycle. A host shares one
 * incident over mutual TLS using a host-run certificate authority. Devices pair
 * with a short-lived, single-use invitation on a narrow unauthenticated
 * enrollment listener and afterwards use certificate-authenticated endpoints that
 * expose the {@link org.sarmanagement.icsforms.persistence.IncidentService}
 * contract. Standalone mode never starts any listener from this package. See
 * {@code docs/host-api-contract.md} and {@code docs/host-mode-plan.md}.
 */
package org.sarmanagement.icsforms.host;
