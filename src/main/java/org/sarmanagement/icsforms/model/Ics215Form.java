/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Operational Planning Worksheet (ICS 215), distinct from the ICS 215A safety analysis.
 *
 * <p>Nullable quantities preserve the distinction between an unknown value and an
 * explicitly entered zero. Resource classifications and resource links are always
 * operator-confirmed; this model never infers resource kind or team composition.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Ics215Form {
	private static final ObjectMapper COPIER = new ObjectMapper().registerModule(new JavaTimeModule());

	/** ICS-215 paper choices that do not alter the shared PDF settings. */
	public enum PaperSize {
		LETTER, A4, LEGAL, TABLOID
	}

	/** Operator-selected counting category for an identity link. */
	public enum ResourceUnit {
		SINGLE_RESOURCE, STRIKE_TEAM_OR_TASK_FORCE
	}

	/** Stable T-card identity and explicit worksheet counting classification. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ResourceLink {
		/** Stable T-card resource identity. */
		public String resourceId = "";
		/** Explicit operator-selected counting unit; never inferred from card details. */
		public ResourceUnit unit;
	}

	/**
	 * Count of individual resources and explicitly classified Strike Teams/Task Forces.
	 * Either component may be null when it has not been specified.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class CountPair {
		/** Number of individual resources, or null when unspecified. */
		public Integer singleResources;
		/** Number of Strike Teams/Task Forces, or null when unspecified. */
		public Integer teams;

		/** Creates an unspecified count pair. */
		public CountPair() {
		}

		/**
		 * Creates a count pair.
		 *
		 * @param singleResources individual-resource count.
		 * @param teams team/task-force count.
		 */
		public CountPair(Integer singleResources, Integer teams) {
			this.singleResources = singleResources;
			this.teams = teams;
		}

		/**
		 * Computes component-wise required-minus-have quantities.
		 *
		 * @param have accepted count snapshot.
		 * @return signed need values, with unknown components left unspecified.
		 */
		public CountPair need(CountPair have) {
			if (have == null) {
				return new CountPair();
			}
			return new CountPair(difference(singleResources, have.singleResources),
					difference(teams, have.teams));
		}

		/**
		 * Sums a collection without silently treating unknown components as zero.
		 *
		 * @param values count pairs to sum.
		 * @return component totals; an unspecified input makes that component unknown.
		 */
		public static CountPair sum(Collection<CountPair> values) {
			if (values == null || values.isEmpty()) {
				return new CountPair(0, 0);
			}
			long singles = 0;
			long teams = 0;
			boolean singlesKnown = true;
			boolean teamsKnown = true;
			for (CountPair value : values) {
				if (value == null || value.singleResources == null) {
					singlesKnown = false;
				} else {
					singles = Math.addExact(singles, value.singleResources.longValue());
				}
				if (value == null || value.teams == null) {
					teamsKnown = false;
				} else {
					teams = Math.addExact(teams, value.teams.longValue());
				}
			}
			return new CountPair(singlesKnown ? Math.toIntExact(singles) : null,
					teamsKnown ? Math.toIntExact(teams) : null);
		}

		/**
		 * Computes nonnegative order quantities for known components.
		 *
		 * @param required required totals.
		 * @param have confirmed incident-wide on-hand totals.
		 * @return nonnegative need-to-order quantities, with unknown inputs unknown.
		 */
		public static CountPair needToOrder(CountPair required, CountPair have) {
			if (required == null || have == null) {
				return new CountPair();
			}
			return new CountPair(orderQuantity(required.singleResources, have.singleResources),
					orderQuantity(required.teams, have.teams));
		}

		/**
		 * Validates explicitly entered quantities.
		 *
		 * @return validation messages for negative components.
		 */
		public List<String> validate() {
			List<String> errors = new ArrayList<>();
			if (singleResources != null && singleResources < 0) {
				errors.add("Single-resource counts cannot be negative.");
			}
			if (teams != null && teams < 0) {
				errors.add("Strike Team/Task Force counts cannot be negative.");
			}
			return errors;
		}

		/**
		 * Computes a component difference while preserving unknown inputs.
		 *
		 * @param required required quantity.
		 * @param have accepted have quantity.
		 * @return signed difference or null if either input is unknown.
		 */
		private static Integer difference(Integer required, Integer have) {
			return required == null || have == null ? null : Math.subtractExact(required, have);
		}

		/**
		 * Computes a component order quantity.
		 *
		 * @param required required total.
		 * @param have confirmed incident-wide on-hand total.
		 * @return nonnegative order quantity, or null for unknown inputs.
		 */
		private static Integer orderQuantity(Integer required, Integer have) {
			return required == null || have == null ? null : Math.max(0, Math.subtractExact(required, have));
		}
	}

	/**
	 * Operator-defined ordered resource column, including its counting unit and
	 * incident-wide on-hand snapshot.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ResourceHeading {
		/** Stable heading identity. */
		public String id = UUID.randomUUID().toString();
		/** Operator-entered category. */
		public String category = "";
		/** Operator-entered kind. */
		public String kind = "";
		/** Operator-entered type. */
		public String type = "";
		/** Explicit unit used for quantities in this column. */
		public String countingUnit = "";
		/** Confirmed unique incident-wide pool, including unallocated resources. */
		public CountPair acceptedOnHand;
		/** Explicitly linked on-hand identities captured with the accepted snapshot. */
		public List<String> linkedResourceIds = new ArrayList<>();
		/** Operator classifications for linked identities. */
		public List<ResourceLink> linkedResources = new ArrayList<>();
		/** Supplemental manual counts, kept separate from linked counts. */
		public CountPair manualSupplemental = new CountPair();
		/** Time the on-hand evidence was accepted. */
		public LocalDateTime acceptedAt;
		/** True when retained live identities require explicit revalidation. */
		public boolean linksRequireReview;
	}

	/** Work assignment row in the worksheet matrix. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class WorkRow {
		/** Stable row identity used by selections and ICS 204 draft links. */
		public String id = UUID.randomUUID().toString();
		/** Branch for this assignment. */
		public String branch = "";
		/** Explicit ICS 204 management-context key. */
		public String managementContext = Ics204Form.MANAGEMENT_STAGING_AREA;
		/** Division name when selected as the management context. */
		public String division = "";
		/** Group name when selected as the management context. */
		public String group = "";
		/** Other management context. */
		public String other = "";
		/** Staging location when selected as the management context. */
		public String stagingArea = "";
		/** Work assignment. */
		public String assignment = "";
		/** Special instructions for this work row. */
		public String specialInstructions = "";
		/** Overhead positions not already included in counted resources. */
		public String overheadPositions = "";
		/** Special equipment for this assignment. */
		public String specialEquipment = "";
		/** Supplies for this assignment. */
		public String supplies = "";
		/** Reporting location. */
		public String reportingLocation = "";
		/** Requested arrival date and time, rendered in 24-hour time. */
		public LocalDateTime requestedArrival;
		/** Required/have snapshots by stable resource-heading identity. */
		public Map<String, ResourceAllocation> resourceAllocations = new LinkedHashMap<>();
		/** Stable ICS 204 form identity created from this row, if any. */
		public String ics204DraftFormId = "";
	}

	/** Required and accepted Have evidence for one row and resource heading. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ResourceAllocation {
		/** Operator-entered requirement. */
		public CountPair required = new CountPair();
		/** Accepted row-level Have snapshot; never silently recalculated. */
		public CountPair acceptedHave;
		/** Explicitly linked T-card identities for the accepted snapshot. */
		public List<String> linkedResourceIds = new ArrayList<>();
		/** Operator classifications for linked identities. */
		public List<ResourceLink> linkedResources = new ArrayList<>();
		/** Supplemental manual count, distinct from linked identities. */
		public CountPair manualSupplemental = new CountPair();
		/** Time the Have evidence was accepted. */
		public LocalDateTime acceptedAt;
		/** True when retained live identities require explicit revalidation. */
		public boolean linksRequireReview;
		/** Operator override retained across resource reviews. */
		public boolean manualOverride;
	}

	private String worksheetId = UUID.randomUUID().toString();
	private LocalDateTime targetPeriodStart;
	private LocalDateTime targetPeriodEnd;
	private String branch = "";
	private String incidentManagementContext = Ics204Form.MANAGEMENT_STAGING_AREA;
	private String division = "";
	private String group = "";
	private String other = "";
	private String stagingArea = "";
	private String specialInstructions = "";
	private String preparerName = "";
	private String preparerPositionTitle = "";
	private String preparerSignature = "";
	private LocalDateTime preparedDateTime;
	private PaperSize paperSize = PaperSize.LETTER;
	private List<ResourceHeading> resourceHeadings = new ArrayList<>();
	private List<WorkRow> workRows = new ArrayList<>();

	/** Creates an empty worksheet. */
	public Ics215Form() {
	}

	/**
	 * Returns this worksheet's stable identity.
	 *
	 * @return worksheet UUID.
	 */
	public String getWorksheetId() {
		return worksheetId;
	}

	/**
	 * Sets this worksheet's stable identity.
	 *
	 * @param worksheetId worksheet UUID; blank values are replaced.
	 */
	public void setWorksheetId(String worksheetId) {
		this.worksheetId = worksheetId == null || worksheetId.isBlank()
				? UUID.randomUUID().toString() : worksheetId;
	}

	/** @return target operational-period start, independent of Shared dates. */
	public LocalDateTime getTargetPeriodStart() {
		return targetPeriodStart;
	}

	/**
	 * Sets the worksheet's target-period start without changing Shared context.
	 *
	 * @param targetPeriodStart target start date/time.
	 */
	public void setTargetPeriodStart(LocalDateTime targetPeriodStart) {
		this.targetPeriodStart = targetPeriodStart;
	}

	/** @return target operational-period end, independent of Shared dates. */
	public LocalDateTime getTargetPeriodEnd() {
		return targetPeriodEnd;
	}

	/**
	 * Sets the worksheet's target-period end without changing Shared context.
	 *
	 * @param targetPeriodEnd target end date/time.
	 */
	public void setTargetPeriodEnd(LocalDateTime targetPeriodEnd) {
		this.targetPeriodEnd = targetPeriodEnd;
	}

	/** @return branch context for the worksheet. */
	public String getBranch() {
		return branch;
	}

	/** @param branch branch context for the worksheet. */
	public void setBranch(String branch) {
		this.branch = safe(branch);
	}

	/** @return explicit ICS 204 management-context key. */
	public String getIncidentManagementContext() {
		return incidentManagementContext;
	}

	/** @param incidentManagementContext management-context key for ICS 204 mapping. */
	public void setIncidentManagementContext(String incidentManagementContext) {
		this.incidentManagementContext = safe(incidentManagementContext);
	}

	/** @return division context. */
	public String getDivision() {
		return division;
	}

	/** @param division division context. */
	public void setDivision(String division) {
		this.division = safe(division);
	}

	/** @return group context. */
	public String getGroup() {
		return group;
	}

	/** @param group group context. */
	public void setGroup(String group) {
		this.group = safe(group);
	}

	/** @return other management context. */
	public String getOther() {
		return other;
	}

	/** @param other other management context. */
	public void setOther(String other) {
		this.other = safe(other);
	}

	/** @return staging location. */
	public String getStagingArea() {
		return stagingArea;
	}

	/** @param stagingArea staging location. */
	public void setStagingArea(String stagingArea) {
		this.stagingArea = safe(stagingArea);
	}

	/** @return worksheet-level special instructions. */
	public String getSpecialInstructions() {
		return specialInstructions;
	}

	/** @param specialInstructions worksheet-level special instructions. */
	public void setSpecialInstructions(String specialInstructions) {
		this.specialInstructions = safe(specialInstructions);
	}

	/** @return preparer's name. */
	public String getPreparerName() {
		return preparerName;
	}

	/** @param preparerName preparer's name. */
	public void setPreparerName(String preparerName) {
		this.preparerName = safe(preparerName);
	}

	/** @return preparer's position or title. */
	public String getPreparerPositionTitle() {
		return preparerPositionTitle;
	}

	/** @param preparerPositionTitle preparer's position or title. */
	public void setPreparerPositionTitle(String preparerPositionTitle) {
		this.preparerPositionTitle = safe(preparerPositionTitle);
	}

	/** @return signature representation. */
	public String getPreparerSignature() {
		return preparerSignature;
	}

	/** @param preparerSignature signature representation. */
	public void setPreparerSignature(String preparerSignature) {
		this.preparerSignature = safe(preparerSignature);
	}

	/** @return preparation date/time. */
	public LocalDateTime getPreparedDateTime() {
		return preparedDateTime;
	}

	/** @param preparedDateTime preparation date/time. */
	public void setPreparedDateTime(LocalDateTime preparedDateTime) {
		this.preparedDateTime = preparedDateTime;
	}

	/** @return ICS-215-specific page size. */
	public PaperSize getPaperSize() {
		return paperSize;
	}

	/** @param paperSize ICS-215-specific page size; null restores Letter. */
	public void setPaperSize(PaperSize paperSize) {
		this.paperSize = paperSize == null ? PaperSize.LETTER : paperSize;
	}

	/** @return ordered resource headings. */
	public List<ResourceHeading> getResourceHeadings() {
		if (resourceHeadings == null) {
			resourceHeadings = new ArrayList<>();
		}
		return resourceHeadings;
	}

	/** @param resourceHeadings ordered resource headings. */
	public void setResourceHeadings(List<ResourceHeading> resourceHeadings) {
		this.resourceHeadings = resourceHeadings == null ? new ArrayList<>() : resourceHeadings;
	}

	/** @return ordered assignment rows. */
	public List<WorkRow> getWorkRows() {
		if (workRows == null) {
			workRows = new ArrayList<>();
		}
		return workRows;
	}

	/** @param workRows ordered assignment rows. */
	public void setWorkRows(List<WorkRow> workRows) {
		this.workRows = workRows == null ? new ArrayList<>() : workRows;
	}

	/**
	 * Calculates one assignment's Required-minus-Have cell.
	 *
	 * @param rowId stable work-row identity.
	 * @param headingId stable resource-heading identity.
	 * @return signed need pair, with unknown components preserved.
	 */
	public CountPair need(String rowId, String headingId) {
		ResourceAllocation allocation = allocation(rowId, headingId);
		return allocation == null ? new CountPair() : required(allocation).need(allocation.acceptedHave);
	}

	/**
	 * Sums required counts down the worksheet for one heading.
	 *
	 * @param headingId stable resource-heading identity.
	 * @return total required counts; unknown row components remain unknown.
	 */
	public CountPair totalRequired(String headingId) {
		List<CountPair> counts = new ArrayList<>();
		for (WorkRow row : getWorkRows()) {
			ResourceAllocation allocation = row == null ? null : row.resourceAllocations.get(headingId);
			counts.add(allocation == null ? null : required(allocation));
		}
		return CountPair.sum(counts);
	}

	/**
	 * Returns the accepted incident-wide on-hand pool for a heading.
	 *
	 * @param headingId stable resource-heading identity.
	 * @return confirmed count pair, or an unspecified pair when no snapshot exists.
	 */
	public CountPair totalOnHand(String headingId) {
		ResourceHeading heading = heading(headingId);
		return heading == null || heading.acceptedOnHand == null
				? new CountPair() : heading.acceptedOnHand;
	}

	/**
	 * Calculates order quantities from required totals and confirmed on-hand snapshots.
	 *
	 * @param headingId stable resource-heading identity.
	 * @return nonnegative order quantities, preserving unknown totals.
	 */
	public CountPair totalNeedToOrder(String headingId) {
		return CountPair.needToOrder(totalRequired(headingId), totalOnHand(headingId));
	}

	/**
	 * Checks dates, identities, quantities, heading references, and resource-link conflicts.
	 *
	 * @return validation problems; an empty list means the worksheet is internally consistent.
	 */
	public List<String> validate() {
		List<String> errors = new ArrayList<>();
		if (targetPeriodStart != null && targetPeriodEnd != null && targetPeriodStart.isAfter(targetPeriodEnd)) {
			errors.add("Target operational-period start must not be after its end.");
		}
		validateIdentities(errors);
		Set<String> headingIds = new LinkedHashSet<>();
		for (ResourceHeading heading : getResourceHeadings()) {
			if (heading == null || !validIdentity(heading.id) || !headingIds.add(heading.id)) {
				errors.add("Resource headings must have unique nonblank identities.");
				continue;
			}
			validateCounts(errors, heading.acceptedOnHand, "On-hand counts");
			validateCounts(errors, heading.manualSupplemental, "Manual on-hand counts");
		}
		Set<String> allocatedResources = new LinkedHashSet<>();
		for (WorkRow row : getWorkRows()) {
			if (row == null) {
				errors.add("Work rows cannot be null.");
				continue;
			}
			if (row.resourceAllocations == null) {
				row.resourceAllocations = new LinkedHashMap<>();
			}
			for (Map.Entry<String, ResourceAllocation> entry : row.resourceAllocations.entrySet()) {
				if (!headingIds.contains(entry.getKey())) {
					errors.add("A work row refers to a missing resource heading.");
				}
				ResourceAllocation allocation = entry.getValue();
				if (allocation == null) {
					errors.add("Resource allocations cannot be null.");
					continue;
				}
				validateCounts(errors, allocation.required, "Required counts");
				validateCounts(errors, allocation.acceptedHave, "Have counts");
				validateCounts(errors, allocation.manualSupplemental, "Manual Have counts");
				for (String resourceId : safeList(allocation.linkedResourceIds)) {
					if (!validIdentity(resourceId) || !allocatedResources.add(resourceId)) {
						errors.add("A linked resource identity is blank or allocated more than once.");
					}
				}
			}
		}
		return errors;
	}

	/**
	 * Throws when the worksheet cannot be saved or promoted safely.
	 *
	 * @throws IllegalArgumentException when validation finds an invalid value.
	 */
	public void validateOrThrow() {
		List<String> errors = validate();
		if (!errors.isEmpty()) {
			throw new IllegalArgumentException(String.join(" ", errors));
		}
	}

	/**
	 * Creates a detached worksheet copy for period archives and PDF export.
	 *
	 * @return independent copy.
	 */
	public Ics215Form copy() {
		return COPIER.convertValue(this, Ics215Form.class);
	}

	/**
	 * Marks accepted linked identities for review without discarding the dated counts.
	 */
	public void markLinkedResourcesForReview() {
		for (ResourceHeading heading : getResourceHeadings()) {
			if (heading != null && !safeList(heading.linkedResourceIds).isEmpty()) {
				heading.linksRequireReview = true;
			}
		}
		for (WorkRow row : getWorkRows()) {
			if (row == null || row.resourceAllocations == null) {
				continue;
			}
			for (ResourceAllocation allocation : row.resourceAllocations.values()) {
				if (allocation != null && !safeList(allocation.linkedResourceIds).isEmpty()) {
					allocation.linksRequireReview = true;
				}
			}
		}
	}

	/**
	 * Returns one allocation by stable row and heading identities.
	 *
	 * @param rowId stable row identity.
	 * @param headingId stable heading identity.
	 * @return allocation, or null when absent.
	 */
	public ResourceAllocation allocation(String rowId, String headingId) {
		for (WorkRow row : getWorkRows()) {
			if (row != null && row.id != null && row.id.equals(rowId)) {
				return row.resourceAllocations == null ? null : row.resourceAllocations.get(headingId);
			}
		}
		return null;
	}

	/**
	 * Returns one resource heading by stable identity.
	 *
	 * @param headingId stable heading identity.
	 * @return heading, or null when absent.
	 */
	public ResourceHeading heading(String headingId) {
		for (ResourceHeading heading : getResourceHeadings()) {
			if (heading != null && heading.id != null && heading.id.equals(headingId)) {
				return heading;
			}
		}
		return null;
	}

	/**
	 * Normalizes an allocation's Required value without collapsing null to zero.
	 *
	 * @param allocation row/heading allocation.
	 * @return requirement pair, or an unknown pair.
	 */
	private static CountPair required(ResourceAllocation allocation) {
		return allocation.required == null ? new CountPair() : allocation.required;
	}

	/**
	 * Adds identity and uniqueness validation for headings and rows.
	 *
	 * @param errors validation messages to append to.
	 */
	private void validateIdentities(List<String> errors) {
		Set<String> identities = new LinkedHashSet<>();
		for (ResourceHeading heading : getResourceHeadings()) {
			if (heading == null || !validIdentity(heading.id) || !identities.add("heading:" + heading.id)) {
				errors.add("Resource headings must have unique nonblank identities.");
			}
		}
		identities.clear();
		for (WorkRow row : getWorkRows()) {
			if (row == null || !validIdentity(row.id) || !identities.add(row.id)) {
				errors.add("Work rows must have unique nonblank identities.");
			}
		}
	}

	/**
	 * Appends errors for negative quantities.
	 *
	 * @param errors validation messages to append to.
	 * @param counts counts to inspect.
	 * @param label field label.
	 */
	private static void validateCounts(List<String> errors, CountPair counts, String label) {
		if (counts != null) {
			for (String error : counts.validate()) {
				errors.add(label + ": " + error);
			}
		}
	}

	/**
	 * Tests for a nonblank stable identity.
	 *
	 * @param value candidate identity.
	 * @return true when the identity is usable.
	 */
	private static boolean validIdentity(String value) {
		return value != null && !value.isBlank();
	}

	/**
	 * Returns a null-free list for safe model inspection.
	 *
	 * @param values possibly absent list.
	 * @return source list, or an empty list.
	 */
	private static List<String> safeList(List<String> values) {
		return values == null ? List.of() : values;
	}

	/**
	 * Normalizes nullable operator-entered text.
	 *
	 * @param value source text.
	 * @return nonnull text.
	 */
	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
