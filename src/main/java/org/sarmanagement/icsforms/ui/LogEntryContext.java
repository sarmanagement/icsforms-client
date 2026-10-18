// SPDX-License-Identifier: GPL-3.0-or-later
package org.sarmanagement.icsforms.ui;

import java.awt.Component;

/**
 * Context supplied by the component requesting a new activity entry.
 *
 * @param source initiating component, including an unsaved editor
 * @param taskId task identifier, when known
 * @param assignmentId assignment identifier, when known
 * @param resourceId resource display identifier, when known
 */
public record LogEntryContext(Component source, String taskId, String assignmentId, String resourceId) {
}
