/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only T-card inspection and explicit ICS 215 resource snapshot acceptance.
 *
 * <p>Card inspection does not assign missing identities or infer worksheet
 * classifications. The operator supplies both the target and counting unit.</p>
 */
public final class Ics215ResourceReview {
	/**
	 * Immutable candidate summary for the T-card review dialog.
	 *
	 * @param resourceId stable T-card identity, or blank when missing.
	 * @param displayName operator-facing card label.
	 * @param cardCategory recorded card discriminator label.
	 * @param status status recorded on the card.
	 * @param location location recorded on the card.
	 * @param assignmentContext assignment context recorded on the card.
	 * @param missingIdentity true when the card has no usable stable identity.
	 * @param ambiguousIdentity true when another card has the same identity.
	 * @param crewMemberOverlapNeedsReview true when a crew's personnel count may overlap other cards.
	 */
	public record Candidate(String resourceId, String displayName, String cardCategory,
			String status, String location, String assignmentContext, boolean missingIdentity,
			boolean ambiguousIdentity, boolean crewMemberOverlapNeedsReview) {
	}

	/**
	 * Result of refreshing a previously accepted Have snapshot.
	 *
	 * @param observed newly observed resource counts.
	 * @param manualOverrideRetained true when accepted manual counts remain distinct.
	 * @param discrepancies differences from the previously accepted evidence.
	 */
	public record RefreshResult(Ics215Form.CountPair observed, boolean manualOverrideRetained,
			List<String> discrepancies) {
	}

	/** Prevents utility construction. */
	private Ics215ResourceReview() {
	}

	/**
	 * Inspects T-cards without mutating cards or generating missing identities.
	 *
	 * @param data incident containing the recorded cards.
	 * @return all card candidates with identity and overlap warnings.
	 */
	public static List<Candidate> inspect(AppData data) {
		Map<String, Integer> identityCounts = new HashMap<>();
		for (TCard card : data.getTCards()) {
			if (card != null && usable(card.peekResourceId())) {
				identityCounts.merge(card.peekResourceId(), 1, Integer::sum);
			}
		}
		List<Candidate> result = new ArrayList<>();
		for (TCard card : data.getTCards()) {
			if (card == null) {
				continue;
			}
			String id = card.peekResourceId();
			boolean missing = !usable(id);
			boolean ambiguous = !missing && identityCounts.getOrDefault(id, 0) > 1;
			String displayName = card.getResourceIdentifier();
			if (displayName.isBlank()) {
				displayName = card.getPersonName();
			}
			if (displayName.isBlank()) {
				displayName = card.getCardType().getLabel();
			}
			boolean crewOverlap = card.getCardType() == TCardType.CREW && card.getNumberOfPersons() > 0;
			result.add(new Candidate(id == null ? "" : id, displayName,
					card.getCardType().getLabel(), card.getStatus(), card.getLocation(),
					card.getSourceRef(), missing, ambiguous, crewOverlap));
		}
		return List.copyOf(result);
	}

	/**
	 * Accepts an explicitly classified unique-resource pool for one heading.
	 *
	 * @param data incident whose T-cards are being reviewed.
	 * @param form worksheet receiving the snapshot.
	 * @param headingId stable heading identity.
	 * @param selectedResources operator-selected identity classifications.
	 * @param manualSupplemental additional non-T-card counts.
	 * @param acceptedAt snapshot acceptance time.
	 */
	public static void acceptOnHand(AppData data, Ics215Form form, String headingId,
			List<Ics215Form.ResourceLink> selectedResources,
			Ics215Form.CountPair manualSupplemental, LocalDateTime acceptedAt) {
		Ics215Form.ResourceHeading heading = requireHeading(form, headingId);
		List<Ics215Form.ResourceLink> links = validateSelections(data, form, null, headingId, selectedResources);
		Ics215Form.CountPair manual = requireNonnegative(manualSupplemental);
		Ics215Form.CountPair linkedCounts = count(links);
		heading.linkedResources = links;
		heading.linkedResourceIds = links.stream().map(link -> link.resourceId).toList();
		heading.manualSupplemental = manual;
		heading.acceptedOnHand = add(linkedCounts, manual);
		heading.acceptedAt = acceptedAt;
		heading.linksRequireReview = false;
		form.validateOrThrow();
	}

	/**
	 * Accepts an explicitly classified Have snapshot for one work row and heading.
	 *
	 * @param data incident whose T-cards are being reviewed.
	 * @param form worksheet receiving the snapshot.
	 * @param rowId stable work-row identity.
	 * @param headingId stable resource-heading identity.
	 * @param selectedResources operator-selected identity classifications.
	 * @param manualSupplemental additional non-T-card counts.
	 * @param acceptedAt snapshot acceptance time.
	 * @return accepted Have snapshot.
	 */
	public static Ics215Form.CountPair acceptRowHave(AppData data, Ics215Form form, String rowId,
			String headingId, List<Ics215Form.ResourceLink> selectedResources,
			Ics215Form.CountPair manualSupplemental, LocalDateTime acceptedAt) {
		Ics215Form.ResourceAllocation allocation = requireAllocation(form, rowId, headingId);
		List<Ics215Form.ResourceLink> links = validateSelections(data, form, rowId, headingId, selectedResources);
		Ics215Form.CountPair manual = requireNonnegative(manualSupplemental);
		Ics215Form.CountPair accepted = add(count(links), manual);
		allocation.linkedResources = links;
		allocation.linkedResourceIds = links.stream().map(link -> link.resourceId).toList();
		allocation.manualSupplemental = manual;
		allocation.acceptedHave = accepted;
		allocation.acceptedAt = acceptedAt;
		allocation.linksRequireReview = false;
		allocation.manualOverride = false;
		form.validateOrThrow();
		return accepted;
	}

	/**
	 * Rechecks currently linked identities without replacing a dated or manually overridden snapshot.
	 *
	 * @param data latest incident state.
	 * @param form worksheet containing the accepted snapshot.
	 * @param rowId stable work-row identity.
	 * @param headingId stable resource-heading identity.
	 * @return observed discrepancy and whether the accepted manual override was retained.
	 */
	public static RefreshResult reviewRowHave(AppData data, Ics215Form form, String rowId, String headingId) {
		Ics215Form.ResourceAllocation allocation = requireAllocation(form, rowId, headingId);
		List<String> expected = allocation.linkedResourceIds == null ? List.of() : allocation.linkedResourceIds;
		Map<String, Candidate> live = new LinkedHashMap<>();
		for (Candidate candidate : inspect(data)) {
			if (!candidate.missingIdentity() && !candidate.ambiguousIdentity()) {
				live.put(candidate.resourceId(), candidate);
			}
		}
		List<String> discrepancies = new ArrayList<>();
		for (String id : expected) {
			if (!live.containsKey(id)) {
				discrepancies.add("Linked resource is missing or ambiguous: " + id);
			}
		}
		if (allocation.linkedResources != null) {
			for (Ics215Form.ResourceLink link : allocation.linkedResources) {
				Candidate candidate = live.get(link.resourceId);
				if (candidate != null && (!equal(link.recordedStatus, candidate.status())
						|| !equal(link.recordedLocation, candidate.location())
						|| !equal(link.recordedAssignmentContext, candidate.assignmentContext()))) {
					discrepancies.add("Recorded status/location/assignment changed for " + link.resourceId);
				}
			}
		}
		Ics215Form.CountPair observed = allocation.linkedResources == null
				? new Ics215Form.CountPair()
				: count(allocation.linkedResources.stream()
						.filter(link -> live.containsKey(link.resourceId)).toList());
		if (!allocation.manualOverride) {
			discrepancies.add("Accepted Have values are dated snapshots; use explicit acceptance to refresh them.");
		}
		return new RefreshResult(observed, allocation.manualOverride, List.copyOf(discrepancies));
	}

	/**
	 * Copies only supported assignment information into a new ICS 204 draft.
	 *
	 * @param form source worksheet.
	 * @param row source work row.
	 * @return a new unapproved assignment draft without invented named resources.
	 */
	public static Ics204Form createIcs204Draft(Ics215Form form, Ics215Form.WorkRow row) {
		Ics204Form draft = new Ics204Form();
		draft.setManagementContext(row.managementContext);
		draft.setBranch(row.branch.isBlank() ? form.getBranch() : row.branch);
		draft.setDivision(row.division);
		draft.setGroup(row.group);
		draft.setStagingArea(row.stagingArea);
		draft.setSharedWorkAssignment(row.assignment);
		draft.setSpecialInstructions(join(form.getSpecialInstructions(), row.specialInstructions));
		return draft;
	}

	/**
	 * Validates requested identities against current cards and other row allocations.
	 *
	 * @param data incident supplying live cards.
	 * @param form worksheet holding current allocations.
	 * @param targetRowId target row, or null for incident-wide on-hand.
	 * @param selected operator classifications.
	 * @return detached, normalized links.
	 */
	private static List<Ics215Form.ResourceLink> validateSelections(AppData data, Ics215Form form,
			String targetRowId, String targetHeadingId, List<Ics215Form.ResourceLink> selected) {
		Map<String, Candidate> candidates = new LinkedHashMap<>();
		for (Candidate candidate : inspect(data)) {
			candidates.putIfAbsent(candidate.resourceId(), candidate);
		}
		Set<String> selectedIds = new HashSet<>();
		List<Ics215Form.ResourceLink> result = new ArrayList<>();
		if (selected == null) {
			return result;
		}
		for (Ics215Form.ResourceLink link : selected) {
			if (link == null || !usable(link.resourceId) || link.unit == null
					|| !selectedIds.add(link.resourceId)) {
				throw new IllegalArgumentException("Select unique, identified resources and classify each count unit.");
			}
			Candidate candidate = candidates.get(link.resourceId);
			if (candidate == null || candidate.missingIdentity() || candidate.ambiguousIdentity()) {
				throw new IllegalArgumentException("A selected T-card identity is missing or ambiguous: "
						+ link.resourceId);
			}
			if (TCardType.HEADER.getLabel().equals(candidate.cardCategory())) {
				throw new IllegalArgumentException("ICS 219 header cards cannot be counted as resources.");
			}
			if (candidate.crewMemberOverlapNeedsReview()) {
				throw new IllegalArgumentException("Resolve crew/member overlap before counting " + link.resourceId);
			}
			if (targetRowId != null && allocatedElsewhere(form, targetRowId, targetHeadingId, link.resourceId)) {
				throw new IllegalArgumentException("A resource cannot be allocated to multiple work rows: "
						+ link.resourceId);
			}
			if (targetRowId == null && linkedToOtherHeading(form, targetHeadingId, link.resourceId)) {
				throw new IllegalArgumentException("A resource cannot be counted under multiple headings: "
						+ link.resourceId);
			}
			Ics215Form.ResourceLink copy = new Ics215Form.ResourceLink();
			copy.resourceId = link.resourceId;
			copy.unit = link.unit;
			copy.recordedStatus = candidate.status();
			copy.recordedLocation = candidate.location();
			copy.recordedAssignmentContext = candidate.assignmentContext();
			result.add(copy);
		}
		return List.copyOf(result);
	}

	/**
	 * Finds whether another work row already counts an identity.
	 *
	 * @param form worksheet to inspect.
	 * @param targetRowId row receiving a refreshed allocation.
	 * @param resourceId candidate resource.
	 * @return true when a different row already allocates the identity.
	 */
	private static boolean allocatedElsewhere(Ics215Form form, String targetRowId, String targetHeadingId,
			String resourceId) {
		for (Ics215Form.WorkRow row : form.getWorkRows()) {
			if (row == null || row.resourceAllocations == null) {
				continue;
			}
			for (Map.Entry<String, Ics215Form.ResourceAllocation> entry : row.resourceAllocations.entrySet()) {
				if (row.id.equals(targetRowId) && entry.getKey().equals(targetHeadingId)) {
					continue;
				}
				Ics215Form.ResourceAllocation allocation = entry.getValue();
				if (allocation != null && allocation.linkedResourceIds != null
						&& allocation.linkedResourceIds.contains(resourceId)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Finds whether another incident-wide heading pool already counts an identity.
	 *
	 * @param form worksheet to inspect.
	 * @param targetHeadingId heading receiving the snapshot.
	 * @param resourceId candidate resource.
	 * @return true when another heading already includes the identity.
	 */
	private static boolean linkedToOtherHeading(Ics215Form form, String targetHeadingId, String resourceId) {
		for (Ics215Form.ResourceHeading heading : form.getResourceHeadings()) {
			if (heading != null && !heading.id.equals(targetHeadingId) && heading.linkedResourceIds != null
					&& heading.linkedResourceIds.contains(resourceId)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Counts only the operator-selected resource units.
	 *
	 * @param links explicit links.
	 * @return unique single-resource and team counts.
	 */
	private static Ics215Form.CountPair count(List<Ics215Form.ResourceLink> links) {
		int singles = 0;
		int teams = 0;
		Set<String> identities = new HashSet<>();
		for (Ics215Form.ResourceLink link : links) {
			if (link == null || !usable(link.resourceId) || link.unit == null || !identities.add(link.resourceId)) {
				throw new IllegalArgumentException("Resource links require unique identities and explicit units.");
			}
			if (link.unit == Ics215Form.ResourceUnit.SINGLE_RESOURCE) {
				singles = Math.addExact(singles, 1);
			} else {
				teams = Math.addExact(teams, 1);
			}
		}
		return new Ics215Form.CountPair(singles, teams);
	}

	/**
	 * Combines linked and supplemental quantities without treating unknown as zero.
	 *
	 * @param linked linked identity counts.
	 * @param manual manual supplemental counts.
	 * @return combined quantities.
	 */
	private static Ics215Form.CountPair add(Ics215Form.CountPair linked, Ics215Form.CountPair manual) {
		return new Ics215Form.CountPair(sum(linked.singleResources, manual.singleResources),
				sum(linked.teams, manual.teams));
	}

	/**
	 * Sums two known quantities with overflow checking.
	 *
	 * @param first first quantity.
	 * @param second second quantity.
	 * @return sum, or null when either input is unknown.
	 */
	private static Integer sum(Integer first, Integer second) {
		return first == null || second == null ? null : Math.addExact(first, second);
	}

	/**
	 * Requires explicit, nonnegative supplemental quantities.
	 *
	 * @param counts supplemental quantities.
	 * @return detached counts.
	 */
	private static Ics215Form.CountPair requireNonnegative(Ics215Form.CountPair counts) {
		if (counts == null || !counts.validate().isEmpty()) {
			throw new IllegalArgumentException("Manual supplemental counts must be specified and nonnegative.");
		}
		return new Ics215Form.CountPair(counts.singleResources, counts.teams);
	}

	/**
	 * Resolves a heading by stable identity.
	 *
	 * @param form worksheet.
	 * @param headingId heading identity.
	 * @return matching heading.
	 */
	private static Ics215Form.ResourceHeading requireHeading(Ics215Form form, String headingId) {
		Ics215Form.ResourceHeading heading = form.heading(headingId);
		if (heading == null) {
			throw new IllegalArgumentException("Select an existing resource heading.");
		}
		return heading;
	}

	/**
	 * Resolves a row allocation by stable identities, creating its empty model if absent.
	 *
	 * @param form worksheet.
	 * @param rowId row identity.
	 * @param headingId heading identity.
	 * @return allocation.
	 */
	private static Ics215Form.ResourceAllocation requireAllocation(Ics215Form form, String rowId,
			String headingId) {
		Ics215Form.WorkRow row = form.getWorkRows().stream()
				.filter(candidate -> candidate != null && candidate.id.equals(rowId)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Select an existing work row."));
		requireHeading(form, headingId);
		if (row.resourceAllocations == null) {
			row.resourceAllocations = new LinkedHashMap<>();
		}
		return row.resourceAllocations.computeIfAbsent(headingId, ignored -> new Ics215Form.ResourceAllocation());
	}

	/**
	 * Checks for a nonblank identity without invoking TCard.getResourceId().
	 *
	 * @param value candidate identity.
	 * @return true when the identity is available.
	 */
	private static boolean usable(String value) {
		return value != null && !value.isBlank();
	}

	/**
	 * Compares nullable snapshot strings without treating null as current data.
	 *
	 * @param first accepted snapshot text.
	 * @param second current text.
	 * @return true when normalized values differ.
	 */
	private static boolean equal(String first, String second) {
		return (first == null ? "" : first).equals(second == null ? "" : second);
	}

	/**
	 * Combines worksheet- and row-specific instructions without discarding either.
	 *
	 * @param first worksheet-level text.
	 * @param second row-level text.
	 * @return joined instructions.
	 */
	private static String join(String first, String second) {
		if (first == null || first.isBlank()) {
			return second == null ? "" : second;
		}
		return second == null || second.isBlank() ? first : first + "\n" + second;
	}
}
