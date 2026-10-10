/**
 * Store-independent incident persistence contracts and a multi-incident JSON
 * file store. Each file has its own backup; legacy single-file workspaces are
 * imported without changing the source. An H2 store and remote synchronization
 * are planned for later implementations of the same contract.
 */
package org.sarmanagement.icsforms.persistence;
