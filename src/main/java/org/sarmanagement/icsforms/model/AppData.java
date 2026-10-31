/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Canonical root document for the local first-cut ICS workspace. Shared
 * incident context, form content, and linked SAR task scaffolding are persisted
 * together.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppData {
	/** Current persistence schema version for JSON storage. */
	public static final int CURRENT_SCHEMA_VERSION = 16;
	private static final ObjectMapper MEDICAL_PLAN_COPIER = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	/**
	 * A lightweight snapshot of the shared context at the end of one operational
	 * period, captured when the incident advances to the next period.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class OperationalPeriodRecord {
		private LocalDateTime periodStart;
		private LocalDateTime periodEnd;
		private IapPlanningStage iapPlanningStage = IapPlanningStage.NOT_RECORDED;
		private List<String> incidentCommanders = new ArrayList<>();
		private MedicalPlan medicalPlan;
		private CanineMedicalPlan canineMedicalPlan;
		private Ics205Form form205;
		private Ics215aForm form215a;
		private Ics215Form form215;
		private Ics215Form nextPeriodForm215;
		private Ics208Form form208;
		private Ics202Form form202;
		private List<Ics204Form> forms204 = new ArrayList<>();
		private List<SarTaskAssignment> sarTaskAssignments = new ArrayList<>();
		private PlanReviewAssertion safetyReview;
		private PlanReviewAssertion humanMedicalReview;
		private PlanReviewAssertion canineMedicalReview;
		private PlanReviewAssertion communicationsReview;

		/** Creates an empty record. */
		public OperationalPeriodRecord() {
		}

		/**
		 * Returns the operational period start date/time.
		 *
		 * @return start date/time for the recorded period, or {@code null} when unset.
		 */
		public LocalDateTime getPeriodStart() {
			return periodStart;
		}
		/**
		 * Sets the operational period start date/time.
		 *
		 * @param periodStart
		 *            start date/time for the recorded period.
		 */
		public void setPeriodStart(LocalDateTime periodStart) {
			this.periodStart = periodStart;
		}

		/**
		 * Returns the operational period end date/time.
		 *
		 * @return end date/time for the recorded period, or {@code null} when unset.
		 */
		public LocalDateTime getPeriodEnd() {
			return periodEnd;
		}
		/**
		 * Sets the operational period end date/time.
		 *
		 * @param periodEnd
		 *            end date/time for the recorded period.
		 */
		public void setPeriodEnd(LocalDateTime periodEnd) {
			this.periodEnd = periodEnd;
		}

		/**
		 * Returns the final explicitly recorded planning-cycle stage for this period.
		 *
		 * @return archived planning stage.
		 */
		public IapPlanningStage getIapPlanningStage() {
			return iapPlanningStage == null ? IapPlanningStage.NOT_RECORDED : iapPlanningStage;
		}

		/**
		 * Sets the final planning-cycle stage for this period.
		 *
		 * @param iapPlanningStage archived stage; null becomes not recorded.
		 */
		public void setIapPlanningStage(IapPlanningStage iapPlanningStage) {
			this.iapPlanningStage = iapPlanningStage == null ? IapPlanningStage.NOT_RECORDED : iapPlanningStage;
		}

		/**
		 * Returns the incident commander / unified-command names for this period.
		 *
		 * @return commander names captured for the recorded period.
		 */
		public List<String> getIncidentCommanders() {
			return incidentCommanders;
		}
		/**
		 * Sets the incident commander / unified-command names for this period.
		 *
		 * @param incidentCommanders
		 *            commander names; {@code null} is treated as an empty list.
		 */
		public void setIncidentCommanders(List<String> incidentCommanders) {
			this.incidentCommanders = incidentCommanders == null ? new ArrayList<>() : incidentCommanders;
		}

		/**
		 * Returns the archived standard medical plan for this period.
		 *
		 * @return archived plan, or null when no plan existed.
		 */
		public MedicalPlan getMedicalPlan() {
			return medicalPlan;
		}

		/**
		 * Sets the archived standard medical plan.
		 *
		 * @param medicalPlan archived plan.
		 */
		public void setMedicalPlan(MedicalPlan medicalPlan) {
			this.medicalPlan = medicalPlan;
		}

		/**
		 * Returns the archived canine medical plan for this period.
		 *
		 * @return archived plan, or null when no plan existed.
		 */
		public CanineMedicalPlan getCanineMedicalPlan() {
			return canineMedicalPlan;
		}

		/**
		 * Sets the archived canine medical plan.
		 *
		 * @param canineMedicalPlan archived plan.
		 */
		public void setCanineMedicalPlan(CanineMedicalPlan canineMedicalPlan) {
			this.canineMedicalPlan = canineMedicalPlan;
		}

		/**
		 * Returns the archived communications plan in this record's period context.
		 * @return archived plan, or null for legacy records without one
		 */
		public Ics205Form getForm205() {
			return form205;
		}

		/**
		 * Sets the archived communications plan.
		 * @param form205 archived plan, or null when absent
		 */
		public void setForm205(Ics205Form form205) {
			this.form205 = form205;
		}

		/** @return archived safety analysis, or null for legacy records. */
		public Ics215aForm getForm215a() {
			return form215a;
		}

		/** @param form215a archived safety analysis. */
		public void setForm215a(Ics215aForm form215a) {
			this.form215a = form215a;
		}

		/** @return archived active-period ICS 215 worksheet. */
		public Ics215Form getForm215() {
			if (form215 == null) {
				form215 = new Ics215Form();
			}
			return form215;
		}

		/** @param form215 archived active-period ICS 215 worksheet. */
		public void setForm215(Ics215Form form215) {
			this.form215 = form215;
		}

		/** @return archived next-period ICS 215 worksheet. */
		public Ics215Form getNextPeriodForm215() {
			if (nextPeriodForm215 == null) {
				nextPeriodForm215 = new Ics215Form();
			}
			return nextPeriodForm215;
		}

		/** @param nextPeriodForm215 archived next-period ICS 215 worksheet. */
		public void setNextPeriodForm215(Ics215Form nextPeriodForm215) {
			this.nextPeriodForm215 = nextPeriodForm215;
		}

		/** @return archived safety message and site plan, or null for legacy records. */
		public Ics208Form getForm208() {
			return form208;
		}

		/** @param form208 archived safety message and site plan. */
		public void setForm208(Ics208Form form208) {
			this.form208 = form208;
		}

		/** @return archived objective form, or null for legacy records. */
		public Ics202Form getForm202() {
			return form202;
		}

		/** @param form202 archived objective form. */
		public void setForm202(Ics202Form form202) {
			this.form202 = form202;
		}

		/** @return archived assignment forms. */
		public List<Ics204Form> getForms204() {
			return forms204;
		}

		/** @param forms204 archived assignment forms. */
		public void setForms204(List<Ics204Form> forms204) {
			this.forms204 = forms204 == null ? new ArrayList<>() : forms204;
		}

		/** @return archived linked SAR task snapshots. */
		public List<SarTaskAssignment> getSarTaskAssignments() {
			return sarTaskAssignments;
		}

		/** @param sarTaskAssignments archived linked SAR task snapshots. */
		public void setSarTaskAssignments(List<SarTaskAssignment> sarTaskAssignments) {
			this.sarTaskAssignments = sarTaskAssignments == null ? new ArrayList<>() : sarTaskAssignments;
		}

		/** @return archived safety review assertion, or null for legacy records. */
		public PlanReviewAssertion getSafetyReview() {
			return safetyReview;
		}

		/** @param safetyReview archived safety review assertion. */
		public void setSafetyReview(PlanReviewAssertion safetyReview) {
			this.safetyReview = safetyReview;
		}

		/** @return archived human medical review assertion, or null for legacy records. */
		public PlanReviewAssertion getHumanMedicalReview() {
			return humanMedicalReview;
		}

		/** @param humanMedicalReview archived human medical review assertion. */
		public void setHumanMedicalReview(PlanReviewAssertion humanMedicalReview) {
			this.humanMedicalReview = humanMedicalReview;
		}

		/** @return archived canine medical review assertion, or null for legacy records. */
		public PlanReviewAssertion getCanineMedicalReview() {
			return canineMedicalReview;
		}

		/** @param canineMedicalReview archived canine medical review assertion. */
		public void setCanineMedicalReview(PlanReviewAssertion canineMedicalReview) {
			this.canineMedicalReview = canineMedicalReview;
		}

		/** @return archived communications review assertion, or null for legacy records. */
		public PlanReviewAssertion getCommunicationsReview() {
			return communicationsReview;
		}

		/** @param communicationsReview archived communications review assertion. */
		public void setCommunicationsReview(PlanReviewAssertion communicationsReview) {
			this.communicationsReview = communicationsReview;
		}
	}

	private int schemaVersion = CURRENT_SCHEMA_VERSION;
	private String incidentId;
	private long revision = 1;
	private Instant createdAt;
	private Instant updatedAt;
	private String originNodeId = "";
	private boolean useSystemTimeZone = true;
	private String configuredTimeZoneId = "UTC";
	private IncidentMode incidentMode = IncidentMode.SAR;
	private IapPhase iapPhase = IapPhase.PRE_OP;
	private IapPlanningStage iapPlanningStage = IapPlanningStage.NOT_RECORDED;
	private List<OperationalPeriodRecord> operationalPeriodHistory = new ArrayList<>();
	private IncidentContext incidentContext = new IncidentContext();
	private OrganizationalChart organizationalChart = new OrganizationalChart();
	private MedicalUnitLeaderAssignment medicalUnitLeaderAssignment;
	private Ics201Form form201 = new Ics201Form();
	private Ics202Form form202 = new Ics202Form();
	private Ics215aForm form215a = new Ics215aForm();
	private Ics215Form form215 = new Ics215Form();
	private Ics215Form nextPeriodForm215 = new Ics215Form();
	@JsonIgnore
	private transient boolean ics215ExportNextPeriod;
	private boolean ics215Enabled;
	private Ics208Form form208 = new Ics208Form();
	private String form205aIapPage = "";
	private Ics207Form form207 = new Ics207Form();
	private Ics204Form form204 = new Ics204Form();
	private Ics205Form form205 = new Ics205Form();
	private boolean ics205Enabled = true;
	private List<Ics204Form> additionalForms204 = new ArrayList<>();
	private List<Ics214Form> activityLogs = new ArrayList<>();
	private List<ActivityEventType> activityEventTypes = new ArrayList<>();
	private List<SarTaskAssignment> sarTaskAssignments = new ArrayList<>();
	private List<SafetyHazardReport> archivedSafetyHazardReports = new ArrayList<>();
	private List<SafetyAnalysisEntry> commonSafetyHazards = new ArrayList<>();
	private List<ClueLogEntry> clueLogEntries = new ArrayList<>();
	private List<OpenActionItem> openActionItems = new ArrayList<>();
	private boolean openActionTrackerEnabled;
	private boolean ics233Portrait;
	private boolean clueLogIcs233Enabled;
	private List<TCard> tCards = new ArrayList<>();
	private PdfLayoutSettings pdfLayoutSettings = new PdfLayoutSettings();
	private MedicalPlan medicalPlan = new MedicalPlan();
	private CanineMedicalPlan canineMedicalPlan = new CanineMedicalPlan();
	private VetDirectory vetDirectory = new VetDirectory();
	private StagingLocation stagingLocation = new StagingLocation();
	private List<IncidentLocation> incidentLocations = new ArrayList<>();
	private String canineRoutingBaseLocationId = "";
	private PlanReviewAssertion safetyReview = new PlanReviewAssertion();
	private PlanReviewAssertion humanMedicalReview = new PlanReviewAssertion();
	private PlanReviewAssertion canineMedicalReview = new PlanReviewAssertion();
	private PlanReviewAssertion communicationsReview = new PlanReviewAssertion();

	/**
	 * Creates an empty incident document.
	 */
	public AppData() {
	}

	/**
	 * Returns regular action items independently of SAR clues.
	 * @return mutable action list, never null
	 */
	public List<OpenActionItem> getOpenActionItems() {
		return openActionItems;
	}

	/**
	 * Sets regular action items, excluding null records.
	 * @param openActionItems action list; null becomes empty
	 */
	public void setOpenActionItems(List<OpenActionItem> openActionItems) {
		this.openActionItems = new ArrayList<>();
		if (openActionItems != null) {
			for (OpenActionItem item : openActionItems) {
				if (item != null) {
					this.openActionItems.add(item);
				}
			}
		}
	}

	/**
	 * Returns whether the independent action tracker is enabled.
	 * @return true when enabled
	 */
	public boolean isOpenActionTrackerEnabled() {
		return openActionTrackerEnabled;
	}

	/**
	 * Enables or hides the tracker without discarding its records.
	 * @param openActionTrackerEnabled true to enable
	 */
	public void setOpenActionTrackerEnabled(boolean openActionTrackerEnabled) {
		this.openActionTrackerEnabled = openActionTrackerEnabled;
	}

	/**
	 * Returns the ICS 233 page orientation.
	 * @return true for portrait; false for the default landscape
	 */
	public boolean isIcs233Portrait() {
		return ics233Portrait;
	}

	/**
	 * Sets the ICS 233 page orientation.
	 * @param ics233Portrait true for portrait
	 */
	public void setIcs233Portrait(boolean ics233Portrait) {
		this.ics233Portrait = ics233Portrait;
	}

	/**
	 * Returns whether clue export uses the adapted ICS 233 layout.
	 * @return true for adapted output; false for standard clue output
	 */
	public boolean isClueLogIcs233Enabled() {
		return clueLogIcs233Enabled;
	}

	/**
	 * Selects adapted clue output without changing stored clues.
	 * @param clueLogIcs233Enabled true for adapted ICS 233 output
	 */
	public void setClueLogIcs233Enabled(boolean clueLogIcs233Enabled) {
		this.clueLogIcs233Enabled = clueLogIcs233Enabled;
	}

	/**
	 * Creates a populated incident document.
	 *
	 * @param incidentContext
	 *            shared incident metadata.
	 * @param form202
	 *            incident objectives form content.
	 * @param form204
	 *            assignment list form content.
	 * @param sarTaskAssignments
	 *            SAR task scaffold records linked to assignments.
	 */
	public AppData(IncidentContext incidentContext, Ics202Form form202, Ics204Form form204,
			List<SarTaskAssignment> sarTaskAssignments) {
		this.incidentContext = incidentContext == null ? new IncidentContext() : incidentContext;
		this.organizationalChart = new OrganizationalChart();
		this.form201 = new Ics201Form();
		this.form202 = form202 == null ? new Ics202Form() : form202;
		this.form204 = form204 == null ? new Ics204Form() : form204;
		if (sarTaskAssignments != null) {
			this.sarTaskAssignments = sarTaskAssignments;
		}
	}

	/**
	 * Advances this document to a new subsequent operational period in place.
	 *
	 * <p>
	 * The organizational chart and ICS 201 initial-response record are preserved as
	 * historical context. Nonblank SAR hazard reports are archived before ICS 202,
	 * ICS 204, SAR tasks, clue log, T-cards, and
	 * activity logs are cleared so that the new period starts fresh. The IAP phase
	 * is set to {@link IapPhase#DURING_OP}. The incident context, incident mode,
	 * and activity event types are retained.
	 * Medical plan content is copied forward with its review workflow reset.
	 * The communications plan is archived, but the new period's plan starts empty;
	 * use {@link #advanceToNewOperationalPeriod(boolean)} to explicitly carry it.
	 * Incident-level open action items and tracker/export preferences are also
	 * retained so unfinished IC/UC work carries into the next operational period.
	 * </p>
	 */
	public void advanceToNewOperationalPeriod() {
		advanceToNewOperationalPeriod(false);
	}

	/**
	 * Advances the period, archiving an independent communications-plan snapshot
	 * with the completed period's start and end. Existing medical-plan and shared
	 * incident lifecycle behavior is unchanged.
	 * @param bringForwardCommunicationsPlan true to copy channels and instructions;
	 *            false to start with an empty plan. Dates, signature, and page are
	 *            always cleared in the new period.
	 */
	public void advanceToNewOperationalPeriod(boolean bringForwardCommunicationsPlan) {
		advanceToNewOperationalPeriod(bringForwardCommunicationsPlan, false);
	}

	/**
	 * Advances to a subsequent period and optionally promotes the planned ICS 215 worksheet.
	 *
	 * <p>Promotion is explicit. It validates target dates before changing the incident,
	 * archives detached outgoing worksheets, applies target dates to Shared context,
	 * and retains accepted Have snapshots while marking linked resources for review.</p>
	 *
	 * @param bringForwardCommunicationsPlan true to copy channels and instructions.
	 * @param promotePlannedIcs215 true to promote the next-period worksheet.
	 * @throws IllegalArgumentException when worksheet promotion has invalid target dates or data.
	 */
	public void advanceToNewOperationalPeriod(boolean bringForwardCommunicationsPlan,
			boolean promotePlannedIcs215) {
		Ics215Form promotedWorksheet = null;
		if (promotePlannedIcs215) {
			validateIcs215Promotion();
			promotedWorksheet = getNextPeriodForm215().copy();
			promotedWorksheet.markLinkedResourcesForReview();
		}
		// Capture the current operational period context before clearing.
		OperationalPeriodRecord record = new OperationalPeriodRecord();
		if (incidentContext != null) {
			record.setPeriodStart(incidentContext.getOperationalPeriodStart());
			record.setPeriodEnd(incidentContext.getOperationalPeriodEnd());
		}
		if (organizationalChart != null) {
			record.setIncidentCommanders(new ArrayList<>(organizationalChart.getIncidentCommanders()));
		}
		record.setMedicalPlan(copyMedicalPlan(getMedicalPlan()));
		record.setCanineMedicalPlan(copyCanineMedicalPlan(getCanineMedicalPlan()));
		record.setForm205(getForm205().deepCopy());
		record.setForm215a(MEDICAL_PLAN_COPIER.convertValue(getForm215a(), Ics215aForm.class));
		record.setForm215(getForm215().copy());
		record.setNextPeriodForm215(getNextPeriodForm215().copy());
		record.setForm208(MEDICAL_PLAN_COPIER.convertValue(getForm208(), Ics208Form.class));
		record.setIapPlanningStage(getIapPlanningStage());
		record.setForm202(MEDICAL_PLAN_COPIER.convertValue(getForm202(), Ics202Form.class));
		List<Ics204Form> archivedForms204 = new ArrayList<>();
		archivedForms204.add(MEDICAL_PLAN_COPIER.convertValue(getForm204(), Ics204Form.class));
		for (Ics204Form form : getAdditionalForms204()) {
			archivedForms204.add(MEDICAL_PLAN_COPIER.convertValue(form, Ics204Form.class));
		}
		record.setForms204(archivedForms204);
		record.setSarTaskAssignments(MEDICAL_PLAN_COPIER.convertValue(getSarTaskAssignments(),
				MEDICAL_PLAN_COPIER.getTypeFactory().constructCollectionType(List.class, SarTaskAssignment.class)));
		record.setSafetyReview(copyReview(getReviewAssertion(PlanReviewArea.SAFETY)));
		record.setHumanMedicalReview(copyReview(getReviewAssertion(PlanReviewArea.HUMAN_MEDICAL)));
		record.setCanineMedicalReview(copyReview(getReviewAssertion(PlanReviewArea.CANINE_MEDICAL)));
		record.setCommunicationsReview(copyReview(getReviewAssertion(PlanReviewArea.COMMUNICATIONS)));
		operationalPeriodHistory.add(record);
		getArchivedSafetyHazardReports().addAll(archiveSafetyHazardReports(sarTaskAssignments,
				record.getPeriodStart(), record.getPeriodEnd()));

		this.iapPhase = IapPhase.DURING_OP;
		this.iapPlanningStage = IapPlanningStage.NOT_RECORDED;
		this.form202 = new Ics202Form();
		this.form205aIapPage = "";
		this.form205 = bringForwardCommunicationsPlan ? record.getForm205().copyForNewPeriod() : new Ics205Form();
		this.form215 = promotedWorksheet == null ? new Ics215Form() : promotedWorksheet;
		if (promotedWorksheet != null) {
			incidentContext.setOperationalPeriodStart(promotedWorksheet.getTargetPeriodStart());
			incidentContext.setOperationalPeriodEnd(promotedWorksheet.getTargetPeriodEnd());
			this.nextPeriodForm215 = new Ics215Form();
		}
		this.form204 = new Ics204Form();
		this.additionalForms204 = new ArrayList<>();
		this.sarTaskAssignments = new ArrayList<>();
		this.clueLogEntries = new ArrayList<>();
		this.tCards = new ArrayList<>();
		this.activityLogs = new ArrayList<>();
		this.medicalPlan = copyMedicalPlan(record.getMedicalPlan());
		this.medicalPlan.resetReviewState();
		this.canineMedicalPlan = copyCanineMedicalPlan(record.getCanineMedicalPlan());
		this.canineMedicalPlan.resetReviewState();
		this.safetyReview = new PlanReviewAssertion();
		this.humanMedicalReview = new PlanReviewAssertion();
		this.canineMedicalReview = new PlanReviewAssertion();
		this.communicationsReview = new PlanReviewAssertion();
	}

	/**
	 * Validates the planned worksheet before the period transition mutates any incident data.
	 *
	 * @throws IllegalArgumentException when target dates or worksheet values are invalid.
	 */
	public void validateIcs215Promotion() {
		Ics215Form planned = getNextPeriodForm215();
		planned.validateOrThrow();
		if (planned.getTargetPeriodStart() == null || planned.getTargetPeriodEnd() == null) {
			throw new IllegalArgumentException("ICS 215 promotion requires target start and end date/time.");
		}
		if (planned.getTargetPeriodStart().isAfter(planned.getTargetPeriodEnd())) {
			throw new IllegalArgumentException("ICS 215 target start must not be after its end.");
		}
	}

	/**
	 * Creates a deep copy of a human medical plan, including its nested entries.
	 *
	 * @param plan source plan.
	 * @return independent plan copy.
	 */
	private static MedicalPlan copyMedicalPlan(MedicalPlan plan) {
		return MEDICAL_PLAN_COPIER.convertValue(plan, MedicalPlan.class);
	}

	/**
	 * Creates a deep copy of a canine medical plan, including facility snapshots.
	 *
	 * @param plan source plan.
	 * @return independent plan copy.
	 */
	private static CanineMedicalPlan copyCanineMedicalPlan(CanineMedicalPlan plan) {
		return MEDICAL_PLAN_COPIER.convertValue(plan, CanineMedicalPlan.class);
	}

	/**
	 * Creates an independent review assertion copy.
	 *
	 * @param review source assertion.
	 * @return independent assertion.
	 */
	private static PlanReviewAssertion copyReview(PlanReviewAssertion review) {
		return MEDICAL_PLAN_COPIER.convertValue(review, PlanReviewAssertion.class);
	}

	/**
	 * Returns the JSON schema version.
	 *
	 * @return persisted schema version.
	 */
	public int getSchemaVersion() {
		return schemaVersion;
	}

	/**
	 * Sets the JSON schema version.
	 *
	 * @param schemaVersion
	 *            schema version value.
	 */
	public void setSchemaVersion(int schemaVersion) {
		this.schemaVersion = schemaVersion;
	}

	/**
	 * Returns the stable identity of this incident document without assigning one.
	 *
	 * @return incident UUID, or {@code null}/blank when not yet assigned.
	 */
	public String getIncidentId() {
		return incidentId;
	}

	/**
	 * Sets the incident document identity during deserialization or migration.
	 *
	 * @param incidentId
	 *            incident UUID; missing values are assigned by
	 *            {@link #normalizeIdentity()}.
	 */
	public void setIncidentId(String incidentId) {
		this.incidentId = incidentId;
	}

	/**
	 * Returns the document revision for persistence coordination.
	 *
	 * @return persisted revision.
	 */
	public long getRevision() {
		return revision;
	}

	/**
	 * Sets the document revision without treating deserialization as an edit.
	 *
	 * @param revision
	 *            persisted revision.
	 */
	public void setRevision(long revision) {
		this.revision = revision;
	}

	/**
	 * Returns the stored document creation instant without initializing it.
	 *
	 * @return creation instant, or {@code null} when not yet assigned.
	 */
	public Instant getCreatedAt() {
		return createdAt;
	}

	/**
	 * Sets the document creation instant.
	 *
	 * @param createdAt
	 *            creation instant, or {@code null} when not yet assigned.
	 */
	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	/**
	 * Returns the stored last modification instant without initializing it.
	 *
	 * @return modification instant, or {@code null} when not yet assigned.
	 */
	public Instant getUpdatedAt() {
		return updatedAt;
	}

	/**
	 * Sets the last modification instant without marking the document as edited.
	 *
	 * @param updatedAt
	 *            modification instant, or {@code null} when not yet assigned.
	 */
	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	/**
	 * Returns the identifier of the node that originated this document.
	 *
	 * @return origin node identifier, or blank when not yet assigned.
	 */
	public String getOriginNodeId() {
		return originNodeId == null ? "" : originNodeId;
	}

	/**
	 * Sets the origin node identifier supplied by the persistence layer.
	 *
	 * @param originNodeId
	 *            origin node identifier; {@code null} clears it.
	 */
	public void setOriginNodeId(String originNodeId) {
		this.originNodeId = originNodeId == null ? "" : originNodeId;
	}

	/**
	 * Assigns missing incident and child identities and missing document timestamps.
	 * Existing identifiers, timestamps, revisions, and origin node identifiers are
	 * preserved, except duplicate channel IDs within a single communications plan
	 * are replaced after their first occurrence. Channel IDs may recur across
	 * period snapshots to retain external links.
	 * Call only after the persistence layer has had an opportunity to
	 * supply an incident ID and timestamps for legacy documents.
	 */
	public void normalizeIdentity() {
		if (incidentId == null || incidentId.isBlank()) {
			incidentId = UUID.randomUUID().toString();
		}
		if (createdAt == null) {
			createdAt = updatedAt == null ? UtcTimestamp.nowInstant() : updatedAt;
		}
		if (updatedAt == null) {
			updatedAt = createdAt;
		}
		getForm205().normalizeIdentity();
		for (OperationalPeriodRecord record : operationalPeriodHistory) {
			if (record != null && record.getForm205() != null) {
				record.getForm205().normalizeIdentity();
			}
		}
		if (tCards != null) {
			for (TCard card : tCards) {
				if (card != null) {
					card.getResourceId();
				}
			}
		}
		if (clueLogEntries != null) {
			for (ClueLogEntry entry : clueLogEntries) {
				if (entry != null) {
					entry.getEntryId();
				}
			}
		}
		if (sarTaskAssignments != null) {
			for (SarTaskAssignment task : sarTaskAssignments) {
				if (task != null) {
					task.getTaskId();
				}
			}
		}
		if (activityLogs != null) {
			for (Ics214Form log : activityLogs) {
				if (log != null) {
					List<ActivityLogEntry> entries = log.getActivityLog();
					if (entries == null) {
						continue;
					}
					for (ActivityLogEntry entry : entries) {
						if (entry != null) {
							entry.getEntryId();
						}
					}
				}
			}
		}
	}

	/**
	 * Returns the current communications plan, including channels hidden on output.
	 * @return current plan, never null
	 */
	public Ics205Form getForm205() {
		return form205;
	}

	/**
	 * Replaces the current communications plan.
	 * @param form205 communications plan; null becomes an empty plan
	 */
	public void setForm205(Ics205Form form205) {
		this.form205 = form205 == null ? new Ics205Form() : form205;
	}

	/**
	 * Returns whether the ICS 205 workspace and export are enabled.
	 * @return true when enabled, including for legacy documents by default
	 */
	public boolean isIcs205Enabled() {
		return ics205Enabled;
	}

	/**
	 * Enables or hides ICS 205 without discarding stored channels.
	 * @param ics205Enabled true to enable
	 */
	public void setIcs205Enabled(boolean ics205Enabled) {
		this.ics205Enabled = ics205Enabled;
	}

	/**
	 * Returns whether timestamps display in the system timezone.
	 * @return true for system-local display
	 */
	public boolean isUseSystemTimeZone() {
		return useSystemTimeZone;
	}

	/**
	 * Chooses system-local or explicitly configured timestamp display.
	 * @param useSystemTimeZone true for system-local display
	 */
	public void setUseSystemTimeZone(boolean useSystemTimeZone) {
		this.useSystemTimeZone = useSystemTimeZone;
	}

	/**
	 * Returns the explicitly configured IANA timezone identifier.
	 * @return timezone identifier
	 */
	public String getConfiguredTimeZoneId() {
		return configuredTimeZoneId;
	}

	/**
	 * Sets the explicit display timezone without changing persisted UTC dates.
	 * @param configuredTimeZoneId IANA identifier; null becomes UTC
	 */
	public void setConfiguredTimeZoneId(String configuredTimeZoneId) {
		this.configuredTimeZoneId = configuredTimeZoneId == null || configuredTimeZoneId.isBlank()
				? "UTC"
				: configuredTimeZoneId.trim();
	}

	/**
	 * Returns the operational period history — one record per completed period, in
	 * chronological order. The current (in-progress) period is NOT included; it
	 * appears only after {@link #advanceToNewOperationalPeriod()} is called.
	 *
	 * @return operational period history (never {@code null}).
	 */
	public List<OperationalPeriodRecord> getOperationalPeriodHistory() {
		return operationalPeriodHistory;
	}

	/**
	 * Sets the operational period history.
	 *
	 * @param operationalPeriodHistory
	 *            period history; {@code null} is treated as empty.
	 */
	public void setOperationalPeriodHistory(List<OperationalPeriodRecord> operationalPeriodHistory) {
		this.operationalPeriodHistory = operationalPeriodHistory == null ? new ArrayList<>() : operationalPeriodHistory;
	}

	/**
	 * Returns the shared incident metadata.
	 *
	 * @return shared incident context.
	 */
	public IncidentContext getIncidentContext() {
		return incidentContext;
	}

	/**
	 * Sets the shared incident metadata.
	 *
	 * @param incidentContext
	 *            shared incident context.
	 */
	public void setIncidentContext(IncidentContext incidentContext) {
		this.incidentContext = incidentContext == null ? new IncidentContext() : incidentContext;
	}

	/**
	 * Returns the organizational chart data shared across forms.
	 *
	 * @return organizational chart data.
	 */
	public OrganizationalChart getOrganizationalChart() {
		return organizationalChart;
	}

	/**
	 * Returns the authoritative Medical Unit Leader assignment, reconciling legacy
	 * duplicate fields the first time it is accessed.
	 *
	 * <p>For files predating the shared assignment, the migration prefers the Org
	 * Chart role name, then the supplemental ICS 203 role, then the human plan's
	 * preparer field, and finally the canine plan's preparer field. Conflicting
	 * legacy names remain in their original fields and are also recorded on the
	 * shared assignment for review. The designated medical service/person field is
	 * intentionally not treated as a Medical Unit Leader assignment.</p>
	 *
	 * @return canonical Medical Unit Leader assignment.
	 */
	public MedicalUnitLeaderAssignment getMedicalUnitLeaderAssignment() {
		if (medicalUnitLeaderAssignment == null) {
			reconcileMedicalUnitLeaderAssignment();
		}
		return medicalUnitLeaderAssignment;
	}

	/**
	 * Returns the existing shared medical assignment without reconciling legacy
	 * fields or creating an assignment.
	 * @return stored assignment, or null when not yet initialized
	 */
	@JsonIgnore
	public MedicalUnitLeaderAssignment peekMedicalUnitLeaderAssignment() {
		return medicalUnitLeaderAssignment;
	}

	/**
	 * Sets the authoritative Medical Unit Leader assignment.
	 *
	 * @param medicalUnitLeaderAssignment shared assignment; null triggers legacy reconciliation.
	 */
	public void setMedicalUnitLeaderAssignment(MedicalUnitLeaderAssignment medicalUnitLeaderAssignment) {
		this.medicalUnitLeaderAssignment = medicalUnitLeaderAssignment;
		if (this.medicalUnitLeaderAssignment != null) {
			preserveMedicalUnitLeaderLegacyConflicts();
		}
	}

	/**
	 * Migrates legacy role copies without erasing any of their values.
	 */
	public void reconcileMedicalUnitLeaderAssignment() {
		if (medicalUnitLeaderAssignment == null) {
			medicalUnitLeaderAssignment = new MedicalUnitLeaderAssignment();
			String legacyName = firstNonBlank(
					organizationalChart == null ? "" : organizationalChart.getMedicalUnitLeaderName(),
					legacyIcs203MedicalUnitLeader(),
					medicalPlan == null ? "" : medicalPlan.getPreparedByMedicalUnitLeader(),
					canineMedicalPlan == null ? "" : canineMedicalPlan.getPreparedByMedicalUnitLeader());
			medicalUnitLeaderAssignment.setName(legacyName);
			if (organizationalChart != null) {
				medicalUnitLeaderAssignment.setRadioChannel(organizationalChart.getMedicalUnitLeaderRadio());
				medicalUnitLeaderAssignment.setPhoneNumber(organizationalChart.getMedicalUnitLeaderPhone());
			}
		}
		preserveMedicalUnitLeaderLegacyConflicts();
	}

	/**
	 * Records legacy role values that disagree with the canonical assignment.
	 */
	private void preserveMedicalUnitLeaderLegacyConflicts() {
		String canonicalName = medicalUnitLeaderAssignment.getName().trim();
		preserveIfConflicting("organizationalChart.medicalUnitLeaderName",
				organizationalChart == null ? "" : organizationalChart.getMedicalUnitLeaderName(), canonicalName);
		preserveIfConflicting("organizationalChart.assignmentList.medicalUnitLeader",
				legacyIcs203MedicalUnitLeader(), canonicalName);
		preserveIfConflicting("medicalPlan.preparedByMedicalUnitLeader",
				medicalPlan == null ? "" : medicalPlan.getPreparedByMedicalUnitLeader(), canonicalName);
		preserveIfConflicting("canineMedicalPlan.preparedByMedicalUnitLeader",
				canineMedicalPlan == null ? "" : canineMedicalPlan.getPreparedByMedicalUnitLeader(), canonicalName);
	}

	/**
	 * Retains a nonblank legacy value that differs from the canonical role name.
	 *
	 * @param source legacy field path.
	 * @param value original value.
	 * @param canonicalName authoritative value.
	 */
	private void preserveIfConflicting(String source, String value, String canonicalName) {
		if (value != null && !value.isBlank() && !value.trim().equals(canonicalName)) {
			medicalUnitLeaderAssignment.preserveLegacyConflict(source, value);
		}
	}

	/**
	 * Reads the legacy Medical Unit Leader entry from the supplemental ICS 203 form.
	 *
	 * @return legacy assigned name, or blank when unavailable.
	 */
	private String legacyIcs203MedicalUnitLeader() {
		return organizationalChart == null || organizationalChart.getAssignmentList() == null
				? "" : organizationalChart.getAssignmentList()
						.getPosition(Ics203Form.Position.MEDICAL_UNIT_LEADER);
	}

	/**
	 * Returns the first nonblank legacy value in migration-priority order.
	 *
	 * @param values ordered candidate names.
	 * @return trimmed first nonblank name or blank.
	 */
	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return "";
	}

	/**
	 * Sets the organizational chart data shared across forms.
	 *
	 * @param organizationalChart
	 *            organizational chart data.
	 */
	public void setOrganizationalChart(OrganizationalChart organizationalChart) {
		this.organizationalChart = organizationalChart == null ? new OrganizationalChart() : organizationalChart;
	}

	/**
	 * Returns ICS 201 content.
	 *
	 * @return incident briefing form.
	 */
	public Ics201Form getForm201() {
		return form201;
	}

	/**
	 * Sets ICS 201 content.
	 *
	 * @param form201
	 *            incident briefing form.
	 */
	public void setForm201(Ics201Form form201) {
		this.form201 = form201 == null ? new Ics201Form() : form201;
	}

	/**
	 * Returns ICS 202 content.
	 *
	 * @return incident objectives form.
	 */
	public Ics202Form getForm202() {
		return form202;
	}

	/**
	 * Sets ICS 202 content.
	 *
	 * @param form202
	 *            incident objectives form.
	 */
	public void setForm202(Ics202Form form202) {
		this.form202 = form202 == null ? new Ics202Form() : form202;
	}

	/**
	 * Returns ICS 215A incident action plan safety analysis.
	 *
	 * @return safety analysis form.
	 */
	public Ics215aForm getForm215a() {
		if (form215a == null) {
			form215a = new Ics215aForm();
		}
		return form215a;
	}

	/**
	 * Sets ICS 215A incident action plan safety analysis.
	 *
	 * @param form215a safety analysis form; null resets to an empty form.
	 */
	public void setForm215a(Ics215aForm form215a) {
		this.form215a = form215a == null ? new Ics215aForm() : form215a;
	}

	/**
	 * Returns the active-period ICS 215 operational planning worksheet.
	 *
	 * @return active-period worksheet.
	 */
	public Ics215Form getForm215() {
		if (form215 == null) {
			form215 = new Ics215Form();
		}
		return form215;
	}

	/**
	 * Sets the active-period ICS 215 worksheet.
	 *
	 * @param form215 active-period worksheet; null resets it.
	 */
	public void setForm215(Ics215Form form215) {
		this.form215 = form215 == null ? new Ics215Form() : form215;
	}

	/**
	 * Returns the independently planned next-period ICS 215 worksheet.
	 *
	 * @return next-period worksheet.
	 */
	public Ics215Form getNextPeriodForm215() {
		if (nextPeriodForm215 == null) {
			nextPeriodForm215 = new Ics215Form();
		}
		return nextPeriodForm215;
	}

	/**
	 * Sets the independently planned next-period ICS 215 worksheet.
	 *
	 * @param nextPeriodForm215 next-period worksheet; null resets it.
	 */
	public void setNextPeriodForm215(Ics215Form nextPeriodForm215) {
		this.nextPeriodForm215 = nextPeriodForm215 == null ? new Ics215Form() : nextPeriodForm215;
	}

	/**
	 * Returns whether the current PDF export should use the planned worksheet.
	 *
	 * @return true for the next-period worksheet, false for the active-period worksheet.
	 */
	@JsonIgnore
	public boolean isIcs215ExportNextPeriod() {
		return ics215ExportNextPeriod;
	}

	/**
	 * Selects the worksheet used for one transient PDF export.
	 *
	 * @param ics215ExportNextPeriod true to export the planned worksheet.
	 */
	@JsonIgnore
	public void setIcs215ExportNextPeriod(boolean ics215ExportNextPeriod) {
		this.ics215ExportNextPeriod = ics215ExportNextPeriod;
	}

	/**
	 * Returns whether the optional ICS 215 workspace is enabled for this incident.
	 *
	 * @return true when ICS 215 is visible.
	 */
	public boolean isIcs215Enabled() {
		return ics215Enabled;
	}

	/**
	 * Sets the persisted per-incident ICS 215 workspace visibility preference.
	 *
	 * @param ics215Enabled true to show the optional workspace.
	 */
	public void setIcs215Enabled(boolean ics215Enabled) {
		this.ics215Enabled = ics215Enabled;
	}

	/**
	 * Returns ICS 208 safety message/plan.
	 *
	 * @return safety message/plan.
	 */
	public Ics208Form getForm208() {
		if (form208 == null) {
			form208 = new Ics208Form();
		}
		return form208;
	}

	/**
	 * Sets ICS 208 safety message/plan.
	 *
	 * @param form208 safety message/plan; null resets to an empty form.
	 */
	public void setForm208(Ics208Form form208) {
		this.form208 = form208 == null ? new Ics208Form() : form208;
	}

	/**
	 * Returns the ICS 205A IAP page number assigned during bundle export.
	 *
	 * @return ICS 205A IAP page number, or blank when not assigned.
	 */
	public String getForm205aIapPage() {
		return form205aIapPage == null ? "" : form205aIapPage;
	}

	/**
	 * Sets the ICS 205A IAP page number assigned during bundle export.
	 *
	 * @param form205aIapPage
	 *            ICS 205A IAP page number.
	 */
	public void setForm205aIapPage(String form205aIapPage) {
		this.form205aIapPage = form205aIapPage == null ? "" : form205aIapPage;
	}

	/**
	 * Returns ICS 207 content.
	 *
	 * @return organization chart form.
	 */
	public Ics207Form getForm207() {
		return form207;
	}

	/**
	 * Sets ICS 207 content.
	 *
	 * @param form207
	 *            organization chart form.
	 */
	public void setForm207(Ics207Form form207) {
		this.form207 = form207 == null ? new Ics207Form() : form207;
	}

	/**
	 * Returns ICS 204 content.
	 *
	 * @return assignment list form.
	 */
	public Ics204Form getForm204() {
		return form204;
	}

	/**
	 * Sets ICS 204 content.
	 *
	 * @param form204
	 *            assignment list form.
	 */
	public void setForm204(Ics204Form form204) {
		this.form204 = form204 == null ? new Ics204Form() : form204;
	}

	/**
	 * Returns additional ICS 204 forms beyond the primary assignment list.
	 *
	 * @return additional ICS 204 forms.
	 */
	public List<Ics204Form> getAdditionalForms204() {
		return additionalForms204;
	}

	/**
	 * Sets additional ICS 204 forms beyond the primary assignment list.
	 *
	 * @param additionalForms204
	 *            additional ICS 204 forms.
	 */
	public void setAdditionalForms204(List<Ics204Form> additionalForms204) {
		this.additionalForms204 = additionalForms204 == null ? new ArrayList<>() : additionalForms204;
	}

	/**
	 * Returns ICS 214 activity logs.
	 *
	 * @return ICS 214 activity logs.
	 */
	public List<Ics214Form> getActivityLogs() {
		return activityLogs;
	}

	/**
	 * Sets ICS 214 activity logs.
	 *
	 * @param activityLogs
	 *            ICS 214 activity logs.
	 */
	public void setActivityLogs(List<Ics214Form> activityLogs) {
		this.activityLogs = activityLogs == null ? new ArrayList<>() : activityLogs;
	}

	/**
	 * Returns the configured activity event types used across all ICS 214 logs.
	 *
	 * <p>
	 * When this list is empty the application populates it with
	 * {@link ActivityEventType#defaultTypes()} on startup. Operators may add custom
	 * types to extend the built-in set.
	 * </p>
	 *
	 * @return configured activity event types.
	 */
	public List<ActivityEventType> getActivityEventTypes() {
		return activityEventTypes;
	}

	/**
	 * Sets the configured activity event types.
	 *
	 * @param activityEventTypes
	 *            configured activity event types.
	 */
	public void setActivityEventTypes(List<ActivityEventType> activityEventTypes) {
		this.activityEventTypes = activityEventTypes == null ? new ArrayList<>() : activityEventTypes;
	}

	/**
	 * Returns linked SAR task records.
	 *
	 * @return SAR task scaffold list.
	 */
	public List<SarTaskAssignment> getSarTaskAssignments() {
		return sarTaskAssignments;
	}

	/**
	 * Sets linked SAR task records.
	 *
	 * @param sarTaskAssignments
	 *            SAR task scaffold list.
	 */
	public void setSarTaskAssignments(List<SarTaskAssignment> sarTaskAssignments) {
		this.sarTaskAssignments = sarTaskAssignments == null ? new ArrayList<>() : sarTaskAssignments;
	}

	/**
	 * Returns SAR hazard reports archived from completed operational periods.
	 *
	 * @return archived reports, never null.
	 */
	public List<SafetyHazardReport> getArchivedSafetyHazardReports() {
		if (archivedSafetyHazardReports == null) {
			archivedSafetyHazardReports = new ArrayList<>();
		}
		return archivedSafetyHazardReports;
	}

	/**
	 * Replaces the archived SAR hazard report history.
	 *
	 * @param archivedSafetyHazardReports archived reports; null becomes empty.
	 */
	public void setArchivedSafetyHazardReports(List<SafetyHazardReport> archivedSafetyHazardReports) {
		this.archivedSafetyHazardReports = archivedSafetyHazardReports == null
				? new ArrayList<>()
				: archivedSafetyHazardReports;
	}

	/**
	 * Copies nonblank current-period debrief hazards into immutable-period records.
	 *
	 * @param tasks current-period task assignments.
	 * @param periodStart completed period start.
	 * @param periodEnd completed period end.
	 * @return snapshots of reported hazards.
	 */
	private static List<SafetyHazardReport> archiveSafetyHazardReports(List<SarTaskAssignment> tasks,
			LocalDateTime periodStart, LocalDateTime periodEnd) {
		List<SafetyHazardReport> reports = new ArrayList<>();
		if (tasks == null) {
			return reports;
		}
		for (SarTaskAssignment task : tasks) {
			if (task == null || task.getHazardsObserved().isBlank()) {
				continue;
			}
			SafetyHazardReport report = new SafetyHazardReport();
			report.setTaskId(task.getAssignmentId().isBlank() ? task.getTaskId() : task.getAssignmentId());
			report.setTaskLabel(task.getAssignmentTeamNumber().isBlank()
					? task.getResourceIdentifier()
					: task.getAssignmentTeamNumber());
			report.setIncidentArea(java.util.stream.Stream.of(task.getBranch(), task.getDivision(), task.getGroup())
					.filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining(", ")));
			report.setHazards(task.getHazardsObserved());
			report.setPeriodStart(periodStart);
			report.setPeriodEnd(periodEnd);
			report.setDebriefingCompleted(task.isDebriefingCompleted());
			report.setAnalysisEntryId(task.getSafetyHazardAnalysisEntryId());
			report.setNotedBySafetyOfficer(task.getSafetyHazardNotedBy());
			report.setNotedDateTime(task.getSafetyHazardNotedDateTime());
			reports.add(report);
		}
		return reports;
	}

	/**
	 * Returns this incident's reusable common hazard and mitigation library.
	 *
	 * @return reusable hazard entries, never null.
	 */
	public List<SafetyAnalysisEntry> getCommonSafetyHazards() {
		if (commonSafetyHazards == null) {
			commonSafetyHazards = new ArrayList<>();
		}
		return commonSafetyHazards;
	}

	/**
	 * Replaces the reusable common hazard and mitigation library.
	 *
	 * @param commonSafetyHazards library entries; null becomes empty.
	 */
	public void setCommonSafetyHazards(List<SafetyAnalysisEntry> commonSafetyHazards) {
		this.commonSafetyHazards = commonSafetyHazards == null ? new ArrayList<>() : commonSafetyHazards;
	}

	/**
	 * Returns shared clue log entries collected from task debriefings.
	 *
	 * @return clue log entries.
	 */
	public List<ClueLogEntry> getClueLogEntries() {
		return clueLogEntries;
	}

	/**
	 * Sets shared clue log entries collected from task debriefings.
	 *
	 * @param clueLogEntries
	 *            clue log entries.
	 */
	public void setClueLogEntries(List<ClueLogEntry> clueLogEntries) {
		this.clueLogEntries = clueLogEntries == null ? new ArrayList<>() : clueLogEntries;
	}

	/**
	 * Returns the IAP preparation phase (pre-operational or during operational
	 * period).
	 *
	 * <p>
	 * Defaults to {@link IapPhase#PRE_OP} for backward compatibility.
	 * </p>
	 *
	 * @return IAP phase.
	 */
	public IapPhase getIapPhase() {
		return iapPhase == null ? IapPhase.PRE_OP : iapPhase;
	}

	/**
	 * Sets the IAP preparation phase.
	 *
	 * @param iapPhase
	 *            IAP phase; {@code null} is treated as {@link IapPhase#PRE_OP}.
	 */
	public void setIapPhase(IapPhase iapPhase) {
		this.iapPhase = iapPhase == null ? IapPhase.PRE_OP : iapPhase;
	}

	/**
	 * Returns the explicitly recorded IAP planning-cycle stage.
	 *
	 * @return planning-cycle stage, never null.
	 */
	public IapPlanningStage getIapPlanningStage() {
		return iapPlanningStage == null ? IapPlanningStage.NOT_RECORDED : iapPlanningStage;
	}

	/**
	 * Sets the explicit IAP planning-cycle stage.
	 *
	 * @param iapPlanningStage stage; null becomes not recorded.
	 */
	public void setIapPlanningStage(IapPlanningStage iapPlanningStage) {
		this.iapPlanningStage = iapPlanningStage == null ? IapPlanningStage.NOT_RECORDED : iapPlanningStage;
	}

	/**
	 * Returns the incident operational mode (SAR or Generic).
	 *
	 * <p>
	 * Defaults to {@link IncidentMode#SAR} for backward compatibility with existing
	 * files that do not carry an explicit mode field.
	 * </p>
	 *
	 * @return incident mode.
	 */
	public IncidentMode getIncidentMode() {
		return incidentMode == null ? IncidentMode.SAR : incidentMode;
	}

	/**
	 * Sets the incident operational mode.
	 *
	 * @param incidentMode
	 *            incident mode; {@code null} is treated as
	 *            {@link IncidentMode#SAR}.
	 */
	public void setIncidentMode(IncidentMode incidentMode) {
		this.incidentMode = incidentMode == null ? IncidentMode.SAR : incidentMode;
	}

	/**
	 * Returns the list of T-Card (ICS 219) resource status records for this
	 * incident.
	 *
	 * @return T-card list.
	 */
	public List<TCard> getTCards() {
		return tCards;
	}

	/**
	 * Sets the list of T-Card records.
	 *
	 * @param tCards
	 *            T-card list.
	 */
	public void setTCards(List<TCard> tCards) {
		this.tCards = tCards == null ? new ArrayList<>() : tCards;
	}

	/**
	 * Returns the current period's standard medical plan.
	 *
	 * @return standard medical plan, never null.
	 */
	public MedicalPlan getMedicalPlan() {
		if (medicalPlan == null) {
			medicalPlan = new MedicalPlan();
		}
		return medicalPlan;
	}

	/**
	 * Returns the stored medical plan without initializing a missing legacy plan.
	 * @return stored plan, or null when absent
	 */
	@JsonIgnore
	public MedicalPlan peekMedicalPlan() {
		return medicalPlan;
	}

	/**
	 * Sets the current period's standard medical plan.
	 *
	 * @param medicalPlan plan; null becomes inactive and empty.
	 */
	public void setMedicalPlan(MedicalPlan medicalPlan) {
		this.medicalPlan = medicalPlan == null ? new MedicalPlan() : medicalPlan;
	}

	/**
	 * Returns the independent current period's canine medical plan.
	 *
	 * @return canine medical plan, never null.
	 */
	public CanineMedicalPlan getCanineMedicalPlan() {
		if (canineMedicalPlan == null) {
			canineMedicalPlan = new CanineMedicalPlan();
		}
		return canineMedicalPlan;
	}

	/**
	 * Returns the stored canine plan without initializing a missing legacy plan.
	 * @return stored plan, or null when absent
	 */
	@JsonIgnore
	public CanineMedicalPlan peekCanineMedicalPlan() {
		return canineMedicalPlan;
	}

	/**
	 * Sets the independent current period's canine medical plan.
	 *
	 * @param canineMedicalPlan plan; null becomes inactive and empty.
	 */
	public void setCanineMedicalPlan(CanineMedicalPlan canineMedicalPlan) {
		this.canineMedicalPlan = canineMedicalPlan == null ? new CanineMedicalPlan() : canineMedicalPlan;
	}

	/**
	 * Returns incident-owned copies of imported regional veterinarian records.
	 *
	 * @return reusable directory, never null.
	 */
	public VetDirectory getVetDirectory() {
		if (vetDirectory == null) {
			vetDirectory = new VetDirectory();
		}
		return vetDirectory;
	}

	/**
	 * Replaces the incident-owned regional directory.
	 *
	 * @param vetDirectory directory; null becomes empty.
	 */
	public void setVetDirectory(VetDirectory vetDirectory) {
		this.vetDirectory = vetDirectory == null ? new VetDirectory() : vetDirectory;
	}

	/**
	 * Returns the shared incident staging/base location.
	 *
	 * @return staging/base location, never null.
	 */
	public StagingLocation getStagingLocation() {
		if (stagingLocation == null) {
			stagingLocation = new StagingLocation();
		}
		return stagingLocation;
	}

	/**
	 * Sets the shared incident staging/base location.
	 *
	 * @param stagingLocation location; null becomes empty.
	 */
	public void setStagingLocation(StagingLocation stagingLocation) {
		this.stagingLocation = stagingLocation == null ? new StagingLocation() : stagingLocation;
	}

	/**
	 * Returns the incident-owned ICP, staging, base, and other location records.
	 *
	 * @return mutable location list, never null.
	 */
	public List<IncidentLocation> getIncidentLocations() {
		if (incidentLocations == null) {
			incidentLocations = new ArrayList<>();
		}
		return incidentLocations;
	}

	/**
	 * Replaces incident-owned operational locations.
	 *
	 * @param incidentLocations location list; null becomes empty.
	 */
	public void setIncidentLocations(List<IncidentLocation> incidentLocations) {
		this.incidentLocations = incidentLocations == null ? new ArrayList<>() : incidentLocations;
	}

	/**
	 * Returns the incident location selected as the canine routing origin.
	 *
	 * @return selected location identifier, or blank for the shared manual base.
	 */
	public String getCanineRoutingBaseLocationId() {
		return canineRoutingBaseLocationId == null ? "" : canineRoutingBaseLocationId;
	}

	/**
	 * Selects an incident location as the canine routing origin.
	 *
	 * @param canineRoutingBaseLocationId location identifier, or blank for a manual base.
	 */
	public void setCanineRoutingBaseLocationId(String canineRoutingBaseLocationId) {
		this.canineRoutingBaseLocationId = canineRoutingBaseLocationId == null ? "" : canineRoutingBaseLocationId;
	}

	/**
	 * Determines canine workspace eligibility without regard to card status.
	 *
	 * @return true for SAR mode or when any card identifies a canine resource.
	 */
	public boolean isCanineMedicalWorkspaceEligible() {
		if (getIncidentMode() == IncidentMode.SAR) {
			return true;
		}
		return getTCards().stream().anyMatch(TCard::isCanineResourceCard);
	}

	/**
	 * Returns PDF layout preferences used by form renderers during export.
	 *
	 * @return PDF page size and margin settings (never {@code null}).
	 */
	public PdfLayoutSettings getPdfLayoutSettings() {
		if (pdfLayoutSettings == null) {
			pdfLayoutSettings = new PdfLayoutSettings();
		}
		return pdfLayoutSettings;
	}

	/**
	 * Sets PDF layout preferences used by form renderers during export.
	 *
	 * @param pdfLayoutSettings
	 *            PDF page size and margin settings; {@code null} resets to defaults.
	 */
	public void setPdfLayoutSettings(PdfLayoutSettings pdfLayoutSettings) {
		this.pdfLayoutSettings = pdfLayoutSettings == null ? new PdfLayoutSettings() : pdfLayoutSettings;
	}

	/**
	 * Returns the persisted assertion for one review area.
	 *
	 * @param area review area.
	 * @return mutable assertion, never null.
	 */
	public PlanReviewAssertion getReviewAssertion(PlanReviewArea area) {
		return switch (area) {
			case SAFETY -> safetyReview == null ? safetyReview = new PlanReviewAssertion() : safetyReview;
			case HUMAN_MEDICAL ->
				humanMedicalReview == null ? humanMedicalReview = new PlanReviewAssertion() : humanMedicalReview;
			case CANINE_MEDICAL ->
				canineMedicalReview == null ? canineMedicalReview = new PlanReviewAssertion() : canineMedicalReview;
			case COMMUNICATIONS ->
				communicationsReview == null ? communicationsReview = new PlanReviewAssertion() : communicationsReview;
		};
	}

	/** @return persisted safety review assertion. */
	public PlanReviewAssertion getSafetyReview() {
		return getReviewAssertion(PlanReviewArea.SAFETY);
	}

	/** @param safetyReview persisted safety review assertion. */
	public void setSafetyReview(PlanReviewAssertion safetyReview) {
		this.safetyReview = safetyReview;
	}

	/** @return persisted human medical review assertion. */
	public PlanReviewAssertion getHumanMedicalReview() {
		return getReviewAssertion(PlanReviewArea.HUMAN_MEDICAL);
	}

	/** @param humanMedicalReview persisted human medical review assertion. */
	public void setHumanMedicalReview(PlanReviewAssertion humanMedicalReview) {
		this.humanMedicalReview = humanMedicalReview;
	}

	/** @return persisted canine medical review assertion. */
	public PlanReviewAssertion getCanineMedicalReview() {
		return getReviewAssertion(PlanReviewArea.CANINE_MEDICAL);
	}

	/** @param canineMedicalReview persisted canine medical review assertion. */
	public void setCanineMedicalReview(PlanReviewAssertion canineMedicalReview) {
		this.canineMedicalReview = canineMedicalReview;
	}

	/** @return persisted communications review assertion. */
	public PlanReviewAssertion getCommunicationsReview() {
		return getReviewAssertion(PlanReviewArea.COMMUNICATIONS);
	}

	/** @param communicationsReview persisted communications review assertion. */
	public void setCommunicationsReview(PlanReviewAssertion communicationsReview) {
		this.communicationsReview = communicationsReview;
	}

	/**
	 * Replaces the persisted assertion for one review area.
	 *
	 * @param area review area.
	 * @param assertion assertion; null becomes not yet reviewed.
	 */
	public void setReviewAssertion(PlanReviewArea area, PlanReviewAssertion assertion) {
		PlanReviewAssertion value = assertion == null ? new PlanReviewAssertion() : assertion;
		switch (area) {
			case SAFETY -> safetyReview = value;
			case HUMAN_MEDICAL -> humanMedicalReview = value;
			case CANINE_MEDICAL -> canineMedicalReview = value;
			case COMMUNICATIONS -> communicationsReview = value;
			default -> throw new IllegalArgumentException("Unsupported review area: " + area);
		}
	}

	/**
	 * Records an explicit human assertion about the current substantive plan content.
	 *
	 * @param area review area.
	 * @param person person making the assertion.
	 * @param reviewedAt assertion time.
	 * @throws IllegalArgumentException when the person is blank.
	 */
	public void recordPlanReview(PlanReviewArea area, String person, LocalDateTime reviewedAt) {
		if (person == null || person.isBlank()) {
			throw new IllegalArgumentException("Reviewer name is required.");
		}
		PlanReviewAssertion assertion = getReviewAssertion(area);
		assertion.setReviewedBy(person.trim());
		assertion.setReviewedAt(reviewedAt == null ? UtcTimestamp.now().withSecond(0).withNano(0) : reviewedAt);
		assertion.setReviewedContentDigest(PlanReviewSupport.contentDigest(this, area));
	}

	/**
	 * Calculates current review state while retaining prior assertion metadata.
	 *
	 * @param area review area.
	 * @return current review status.
	 */
	@JsonIgnore
	public PlanReviewStatus getPlanReviewStatus(PlanReviewArea area) {
		PlanReviewAssertion assertion = getReviewAssertion(area);
		if (!assertion.isRecorded()) {
			return PlanReviewStatus.NOT_YET_REVIEWED;
		}
		return assertion.getReviewedContentDigest().equals(PlanReviewSupport.contentDigest(this, area))
				? PlanReviewStatus.REVIEWED_APPROVED
				: PlanReviewStatus.CHANGED_SINCE_REVIEW;
	}

	/**
	 * Returns every assignment row in the active operational period.
	 *
	 * @return mutable assignment objects in primary-then-additional form order.
	 */
	@JsonIgnore
	public List<ResourceAssignment> getAllAssignmentResources() {
		List<ResourceAssignment> assignments = new ArrayList<>();
		if (getForm204() != null) {
			assignments.addAll(getForm204().getResourcesAssigned());
		}
		for (Ics204Form form : getAdditionalForms204()) {
			if (form != null) {
				assignments.addAll(form.getResourcesAssigned());
			}
		}
		return assignments;
	}

	/**
	 * Finds assignments linked to one stable objective.
	 *
	 * @param objectiveId stable objective identifier.
	 * @return linked assignments in form order.
	 */
	@JsonIgnore
	public List<ResourceAssignment> getAssignmentsForObjective(String objectiveId) {
		if (objectiveId == null || objectiveId.isBlank()) {
			return List.of();
		}
		return getAllAssignmentResources().stream()
				.filter(assignment -> assignment.getObjectiveIds().contains(objectiveId)).toList();
	}

	/**
	 * Removes an objective only after linked assignments are explicitly unlinked or
	 * reassigned.
	 *
	 * @param objectiveId objective to remove.
	 * @param replacementObjectiveId replacement objective, or blank to unlink.
	 * @param unlinkLinkedAssignments true when the operator explicitly chose to unlink.
	 * @throws IllegalStateException when linked assignments have no explicit resolution.
	 */
	public void removeObjective(String objectiveId, String replacementObjectiveId, boolean unlinkLinkedAssignments) {
		List<ResourceAssignment> linked = getAssignmentsForObjective(objectiveId);
		String replacement = replacementObjectiveId == null ? "" : replacementObjectiveId;
		if (!linked.isEmpty() && replacement.isBlank() && !unlinkLinkedAssignments) {
			throw new IllegalStateException(
					"Objective is linked to assignments; choose a replacement or explicitly unlink them.");
		}
		if (!replacement.isBlank() && getForm202().getStructuredObjectives().stream()
				.noneMatch(objective -> replacement.equals(objective.getObjectiveId()))) {
			throw new IllegalArgumentException("Replacement objective does not exist.");
		}
		for (ResourceAssignment assignment : linked) {
			List<String> links = new ArrayList<>(assignment.getObjectiveIds());
			int position = links.indexOf(objectiveId);
			links.removeIf(objectiveId::equals);
			if (!replacement.isBlank() && !links.contains(replacement)) {
				links.add(Math.max(0, position), replacement);
			}
			assignment.setObjectiveIds(links);
		}
		List<IncidentObjective> retained = new ArrayList<>(getForm202().getStructuredObjectives());
		retained.removeIf(objective -> objectiveId.equals(objective.getObjectiveId()));
		getForm202().setStructuredObjectives(retained);
	}
}
