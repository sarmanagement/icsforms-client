/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.ActivityLogScope;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.model.IapPlanningStage;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.IncidentLocation;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.OpenActionItem;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.RadioCheckStatus;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.ResourceDirectorySource;
import org.sarmanagement.icsforms.model.SafetyAnalysisEntry;
import org.sarmanagement.icsforms.model.SafetyMessageBlock;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;
import org.sarmanagement.icsforms.model.VetFacility;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics203PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics204PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics205PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics205aPdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics206CaninePdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics206PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics207PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics208PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics214PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics215aPdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics233CgPdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.pdf.SarTaskAssignmentPdfRenderer;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the portable fictional Maple Ridge example and its reproducible model builder. */
class MapleRidgeExampleTest {
	private static final Path FIXTURE = Path.of("docs/examples/maple-ridge-dementia-op1.json");
	private static final String INCIDENT = "FICTIONAL EXERCISE - Maple Ridge missing adult with dementia";
	private static final String IPP = "Residence / IPP, 1 Trail Lane, fictional Maple Ridge, MA";
	private static final String STAGING = "Community park pavilion staging, 20 Park Road, fictional Maple Ridge, MA (0.8 km west of IPP)";
	private static final String ICP = "ICP, county community center, 50 Civic Road, fictional Maple Ridge, MA (1.5 km west of IPP)";
	private static final String BASE = "Base (supply and rehab) colocated with " + STAGING;
	private static final String CHANNEL = "MR SAR 1";
	private static final String AGENCY = "Fictional Maple Ridge Volunteer SAR";
	private static final String IC = "Avery Wren";
	private static final String OPS = "Morgan Vale";
	private static final String PLANNING = "Rowan Ellis";
	private static final String SHERIFF_AGENCY = "Fictional Pine County Sheriff's Office";
	private static final String SAFETY = "Jordan Reed";
	private static final String COML = "Taylor Brook";
	private static final String MEDL = "Quinn Alder";
	private static final String VETERINARIAN = "Zion Clover";
	private static final List<String> FORMS = List.of("ICS 202", "ICS 203", "ICS 204", "ICS 205", "ICS 205A",
			"ICS 206", "ICS 206CANINE", "ICS 207", "ICS 208", "ICS 214", "ICS 215A", "ICS 233-CG", "SAR Task Assignment");
	private static final List<String> FIELD_NAMES = List.of("Casey Finch", "Alex Fern", "Blair Moss", "Cameron Lark",
			"Dana Birch", "Elliot Pine", "Frankie Cove", "Gray Rowan", "Harper Glen", "Indigo Lake",
			"Jules Meadow", "Kai Willow", "Lane Cedar", "Micah Ash", "Noel Spruce", "Oakley Rain",
			"Parker Elm", "Remy Shore", "Sage Linden", "Toby Flint", "Val Aspen", "Winter Dell",
			"Wynn Hazel", "Yael Frost", "Zion Clover", "Robin Field", "Arden Fox", "Briar Snow",
			"Devon Heath", "Emery Reed", "Finley Stone", "Hayden Marsh", "Kit Laurel", "Linden Gale",
			"Marin Coast", "Nico Dawn");

	@Test
	void portableExampleImportsWithLinkedOperationalContent() throws Exception {
		Path work = Files.createTempDirectory(Path.of("target"), "maple-import-");
		try (FileIncidentStore store = new FileIncidentStore(work)) {
			AppData data = store.load(store.importFile(FIXTURE).orElseThrow());
			assertTrue(store.getLastImportProblems().isEmpty(), () -> store.getLastImportProblems().toString());
			assertEquals(INCIDENT, data.getIncidentContext().getIncidentName());
			assertEquals(IncidentMode.SAR, data.getIncidentMode());
			assertEquals(IapPhase.DURING_OP, data.getIapPhase());
			assertEquals(IapPlanningStage.OPERATIONAL_PERIOD_ACTIVE, data.getIapPlanningStage());
			assertEquals(time(7, 0), data.getIncidentContext().getOperationalPeriodStart());
			assertEquals(time(19, 0), data.getIncidentContext().getOperationalPeriodEnd());
			assertTrue(new IncidentValidator().validate(data).isEmpty(),
					() -> new IncidentValidator().validate(data).toString());
			assertEquals(5, data.getForm202().getStructuredObjectives().size());
			List<String> objectives = data.getForm202().getObjectives();
			assertEquals(List.of("Mitigate all hazards to responders and the general public "
					+ "to an acceptable level, with particular attention to heat stress and tick exposure, "
					+ "throughout the entire duration of the incident.",
					"By 1000, complete hasty searches of the three most likely travel corridors from IPP to containment boundary at least once.",
					"By 0800, establish containment of both likely egress points and maintain it through 1900.",
					"By 1700, search the high-probability inner 25% zone to a coverage target of approximately 1.",
					"By 1200, search all three familiar attractors and associated hazard points within 1 km of IPP."),
					objectives);
			assertEquals(42, data.getTCards().stream().filter(c -> c.getCardType() == TCardType.PERSONNEL).count());
			assertEquals(36, data.getTCards().stream().filter(c -> c.getResourceIdentifier().startsWith("MR-P")).count());
			assertEquals(4, data.getTCards().stream().filter(TCard::isCanineResourceCard).count());
			assertEquals(46, data.getTCards().size());
			assertEquals(46, data.getTCards().stream().map(TCard::getResourceId).distinct().count());
			int explicitCanineMarkers = 0;
			for (var card : new ObjectMapper().readTree(FIXTURE.toFile()).path("tcards")) {
				if (card.path("canineResource").asBoolean()) {
					assertEquals("EQUIPMENT", card.path("cardType").asText());
					explicitCanineMarkers++;
				}
			}
			assertEquals(4, explicitCanineMarkers);
			assertEquals("", data.getOrganizationalChart().getLogisticsSectionChiefName());
			assertEquals(3, Set.of(IPP, STAGING, ICP).size());
			assertEquals(STAGING, data.getStagingLocation().getLabel());
			assertEquals(STAGING, data.getForm204().getStagingArea());
			assertEquals(List.of("ICP", "Staging Area", "Base", "Other"),
					data.getIncidentLocations().stream().map(IncidentLocation::getType).toList());
			assertEquals(List.of("icp", "staging", "base", "ipp").stream().map(s -> id("location-" + s)).toList(),
					data.getIncidentLocations().stream().map(IncidentLocation::getId).toList());
			assertEquals(List.of(ICP, STAGING, BASE, IPP),
					data.getIncidentLocations().stream().map(IncidentLocation::getName).toList());
			assertEquals(List.of("50 Civic Road, fictional Maple Ridge, MA", "20 Park Road, fictional Maple Ridge, MA",
					"20 Park Road, fictional Maple Ridge, MA", "1 Trail Lane, fictional Maple Ridge, MA"),
					data.getIncidentLocations().stream().map(IncidentLocation::getAddress).toList());
			assertEquals(data.getStagingLocation().getAddress(), data.getIncidentLocations().stream()
					.filter(location -> "Base".equals(location.getType())).findFirst().orElseThrow().getAddress());
			assertTrue(data.getSarTaskAssignments().stream().allMatch(task -> task.getTransportationInstructions()
					.equals("Walk from staging or drive a personal vehicle from staging. "
							+ "No ground support unit or aircraft is assigned.")));
			List<TCard> staff = data.getTCards().stream()
					.filter(c -> c.getResourceIdentifier().startsWith("MR-IMT-")).toList();
			assertEquals(6, staff.size());
			assertTrue(staff.stream().allMatch(c -> ICP.equals(c.getLocation())));
			assertTrue(staff.stream().allMatch(c -> "ICP".equals(c.getStatus())));
			Ics214Form icpLog = data.getActivityLogs().stream()
					.filter(log -> log.getLogScope() == ActivityLogScope.ICP)
					.findFirst().orElseThrow();
			assertEquals(OPS, icpLog.getName());
			assertEquals("Operations Section Chief", icpLog.getIcsPosition());
			assertEquals("", icpLog.getLinkedIcs204FormId());
			assertEquals("", icpLog.getLinkedSarTaskAssignmentId());
			Set<String> loggedResources = icpLog.getResourcesAssigned().stream()
					.map(SarTaskResource::getName).collect(Collectors.toSet());
			assertTrue(staff.stream().allMatch(card -> loggedResources.contains(card.getPersonName())));
			for (SarTaskAssignment task : data.getSarTaskAssignments()) {
				if (!"planned".equals(task.getTaskLifecycleStatus())) {
					assertTrue(task.getResourcesAssigned().stream()
							.allMatch(resource -> loggedResources.contains(resource.getName())),
							() -> "Missing resources for assigned task " + task.getAssignmentTeamNumber());
				}
			}
			Set<String> supportedStaffRefs = Set.of("org:ic:0", "org:operationsChief", "org:planningChief",
					"org:safetyOfficer", "org:commUnitLeader");
			assertTrue(staff.stream().allMatch(c -> c.getSourceRef().isBlank()
					|| supportedStaffRefs.contains(c.getSourceRef())));
			assertEquals(List.of("org:ic:0", "org:operationsChief", "org:planningChief",
					"org:safetyOfficer", "org:commUnitLeader", ""),
					staff.stream().map(TCard::getSourceRef).toList());
			assertTrue(staff.get(5).getNotes().contains("MEDL"));
			TCard sheriff = staff.stream().filter(c -> IC.equals(c.getPersonName())).findFirst().orElseThrow();
			assertEquals(SHERIFF_AGENCY, sheriff.getHomeAgency());
			assertTrue(sheriff.getNotes().contains("Sheriff"));
			assertTrue(data.getForm202().getSituationalAwareness().contains("Sheriff of " + SHERIFF_AGENCY));
			assertEquals(Set.of(AGENCY, "Fictional Cedar Valley SAR", "Fictional Lake Hollow SAR"),
					data.getTCards().stream().filter(c -> FIELD_NAMES.contains(c.getPersonName()))
							.map(TCard::getHomeAgency).collect(Collectors.toSet()));
			TCard casey = data.getTCards().stream().filter(c -> "Casey Finch".equals(c.getPersonName()))
					.findFirst().orElseThrow();
			assertTrue(casey.getResourceIdentifier().startsWith("MR-P"));
			assertTrue(casey.getSourceRef().startsWith("sar:"));
			assertFalse(Files.readString(FIXTURE).toLowerCase(java.util.Locale.ROOT).contains("cleared"));
			for (String detail : List.of("Ellis Linden", "74-year-old", "dementia", "blue shirt", "tan trousers",
					"0500", "July 18", "0815", "27-31 C", "humid", "light wind", IPP, STAGING, ICP)) {
				assertTrue(data.getForm202().getSituationalAwareness().contains(detail), detail);
			}
			assertFalse(data.getForm202().getSituationalAwareness().contains("four dogs are equipment, not people"));
			String fixtureText = Files.readString(FIXTURE);
			assertFalse(fixtureText.contains("coverage 1 is not POD 1"));
			assertFalse(fixtureText.contains("always less than 100%"));
			assertTrue(data.getForm202().getCommandEmphasis().contains("inner 25% zone"));
			assertFalse(data.getForm202().getCommandEmphasis().contains("Sheriff"));
			for (TCard card : data.getTCards()) {
				assertFalse(card.getResourceIdentifier().isBlank());
				assertFalse(card.getHomeAgency().isBlank());
				assertFalse(card.getHomeState().isBlank());
				assertFalse(card.getStatus().isBlank());
				assertEquals(time(6, 30), card.getCheckInDateTime());
				assertEquals(id("channel"), card.getRadioChannelId());
				if (card.getCardType() == TCardType.PERSONNEL) {
					assertFalse(card.getPersonName().isBlank());
					assertEquals(1, card.getNumberOfPersons());
				} else {
					assertEquals(TCardType.EQUIPMENT, card.getCardType());
					assertTrue(card.isCanineResourceCard());
					assertEquals(0, card.getNumberOfPersons());
					assertTrue(data.getTCards().stream().anyMatch(h -> h.getPersonName().equals(card.getHandlerName())));
				}
			}
			RadioChannel channel = data.getForm205().getChannels().get(0);
			assertEquals(1, data.getForm205().getChannels().size());
			assertEquals("155.160", channel.getRxFrequency());
			assertEquals(channel.getRxFrequency(), channel.getTxFrequency());
			assertEquals("FM", channel.getMode());
			assertEquals(RadioCheckStatus.SUCCESSFUL, channel.getRadioCheckStatus());
			var directory = ResourceDirectorySource.build(data);
			assertEquals(42, directory.size());
			assertTrue(directory.stream().filter(r -> staff.stream()
					.anyMatch(c -> !c.getSourceRef().isBlank() && c.getPersonName().equals(r.name())))
					.allMatch(r -> !r.assignedPosition().isBlank() && ICP.equals(r.assignment())));
			assertTrue(directory.stream().allMatch(r -> r.contactMethods().contains(CHANNEL)));
			assertTrue(directory.stream().filter(r -> FIELD_NAMES.contains(r.name()))
					.allMatch(r -> !r.assignment().isBlank() && !r.assignedPosition().isBlank()));
			assertTrue(data.getOrganizationalChart().getAdditionalPositions().isEmpty());
			assertEquals("", data.getForm204().getBranch());
			assertEquals("", data.getForm204().getDivision());
			assertEquals("", data.getForm204().getGroup());
			assertTrue(data.getAdditionalForms204().isEmpty());
			assertEquals(13, data.getForm204().getResourcesAssigned().size());
			assertEquals(13, data.getSarTaskAssignments().size());
			assertEquals(List.of(IC, OPS),
					data.getForm204().getCommunications().stream().map(CommunicationEntry::getName).toList());
			Set<String> objectiveIds = data.getForm202().getStructuredObjectives().stream()
					.map(IncidentObjective::getObjectiveId).collect(Collectors.toSet());
			Set<String> resourceIds = data.getTCards().stream().map(TCard::getResourceId).collect(Collectors.toSet());
			for (SarTaskAssignment task : data.getSarTaskAssignments()) {
				ResourceAssignment row = data.getForm204().getResourcesAssigned().stream()
						.filter(r -> r.getAssignmentId().equals(task.getAssignmentId())).findFirst().orElseThrow();
				assertEquals(row.getAssignmentTeamNumber(), task.getAssignmentTeamNumber());
				assertEquals(row.getResourceType(), task.getResourceType());
				assertEquals(row.getTaskType(), task.getTaskType());
				assertEquals(row.getAssignment(), task.getAssignment());
				assertTrue(objectiveIds.containsAll(row.getObjectiveIds()));
				assertFalse(row.getObjectiveIds().isEmpty());
				assertEquals(row.getNumberOfPersons(), task.getResourcesAssigned().stream()
						.filter(r -> r.getCardType() == TCardType.PERSONNEL).count());
				assertTrue(task.getResourcesAssigned().stream().allMatch(r -> resourceIds.contains(r.getResourceId())));
				assertEquals(2, task.getCommunications().size());
				CommunicationEntry icContact = task.getCommunications().get(0);
				assertEquals(IC, icContact.getName());
				assertEquals("IC", icContact.getFunction());
				assertEquals(sheriff.getPhoneNumber(), icContact.getPrimaryContact());
				assertEquals("", icContact.getRadioChannelId());
				CommunicationEntry opsContact = task.getCommunications().get(1);
				assertEquals(OPS, opsContact.getName());
				assertEquals("Ops", opsContact.getFunction());
				assertEquals(CHANNEL + " / 155.160 MHz", opsContact.getPrimaryContact());
				assertEquals(id("channel"), opsContact.getRadioChannelId());
				assertEquals(STAGING, task.getStagingArea());
				assertEquals(STAGING, row.getReportingLocation());
				assertEquals(row.getLeader(), task.getLeader());
				int expectedPeople = task.getAssignmentTeamNumber().equals("T1") ? 3
						: task.getResourceType().equals("Canine") || task.getTaskType().equals("Route") ? 4
						: task.getResourceIdentifier().startsWith("Observation") ? 2 : 8;
				assertEquals(expectedPeople, row.getNumberOfPersons());
				if (task.getResourceType().equals("Canine")) {
					SarTaskResource dog = task.getResourcesAssigned().stream()
							.filter(r -> r.getCardType() == TCardType.EQUIPMENT).findFirst().orElseThrow();
					assertEquals(task.getLeader(), data.getTCards().stream()
							.filter(c -> c.getResourceId().equals(dog.getResourceId())).findFirst().orElseThrow().getHandlerName());
					assertEquals(5, task.getResourcesAssigned().size());
					assertEquals("Leader/Handler", task.getLeaderRole());
					assertEquals("Leader/Handler", row.getLeaderRole());
					assertEquals(List.of("Leader/Handler", "Communications", "Navigation", "Ground Searcher",
							"Live Find K9"), task.getResourcesAssigned().stream().map(SarTaskResource::getFunction).toList());
				}
				if (!"returned".equals(task.getTaskLifecycleStatus())) {
					assertEquals("", task.getDebriefingSupervisor());
					assertEquals("", task.getDebriefingSupervisorResourceId());
					assertEquals("", task.getDebriefNotes());
					assertEquals("", task.getReportedPod());
					assertEquals("", task.getVehicleMiles());
					assertTrue(task.getQualitativePodFactors().stream()
							.allMatch(f -> f.getScore() == null && f.getDescription().isBlank()));
					assertEquals("", task.getAreasNotCovered());
					assertEquals("", task.getHazardsObserved());
					assertEquals("", task.getDebriefPreparedByName());
					assertEquals("", task.getDebriefPreparedByPositionTitle());
					assertNull(task.getDebriefPreparedDateTime());
					assertFalse(task.isDebriefingCompleted());
					assertTrue(task.getAssignmentStart().isBefore(task.getAssignmentEnd()));
				}
			}
			assertEquals(12, data.getSarTaskAssignments().stream().map(SarTaskAssignment::getResourceIdentifier).distinct().count());
			assertEquals(4, data.getSarTaskAssignments().stream().filter(t -> t.getResourceType().equals("Canine")).count());
			assertEquals(2, data.getSarTaskAssignments().stream()
					.filter(t -> t.getResourceIdentifier().startsWith("Observation")).count());
			assertEquals(Set.of("G1-A", "G1-B", "G2-A", "G1", "G2", "Visual trackers"), data.getSarTaskAssignments().stream()
					.filter(t -> t.getResourceType().equals("Ground") && !t.getResourceIdentifier().startsWith("Observation"))
					.map(SarTaskAssignment::getResourceIdentifier).collect(Collectors.toSet()));
			for (String team : List.of("G1", "G2")) {
				List<Set<String>> memberships = data.getSarTaskAssignments().stream()
						.filter(t -> team.equals(t.getResourceIdentifier()))
						.map(t -> t.getResourcesAssigned().stream().map(SarTaskResource::getResourceId).collect(Collectors.toSet()))
						.toList();
				assertEquals(team.equals("G1") ? 2 : 1, memberships.size());
				assertEquals(8, memberships.get(0).size());
				assertTrue(memberships.stream().allMatch(m -> m.equals(memberships.get(0))));
			}
			List<SarTaskAssignment> routes = data.getSarTaskAssignments().stream()
					.filter(t -> t.getTaskType().equals("Route")).toList();
			assertEquals(List.of("S1", "S2", "S3"),
					routes.stream().map(SarTaskAssignment::getAssignmentTeamNumber).toList());
			assertEquals(12, routes.stream().flatMap(t -> t.getResourcesAssigned().stream())
					.map(SarTaskResource::getResourceId).distinct().count());
			assertTrue(routes.stream().allMatch(t -> t.getResourcesAssigned().size() == 4
					&& time(7, 15).equals(t.getAssignmentStart()) && time(10, 0).equals(t.getAssignmentEnd())));
			for (int i = 0; i < routes.size(); i++) {
				assertEquals(IntStream.rangeClosed(i * 4 + 1, i * 4 + 4)
						.mapToObj(n -> id("MR-P%02d".formatted(n))).collect(Collectors.toSet()),
						routes.get(i).getResourcesAssigned().stream()
								.map(SarTaskResource::getResourceId).collect(Collectors.toSet()));
			}
			SarTaskAssignment ground2 = data.getSarTaskAssignments().stream()
					.filter(t -> t.getAssignmentTeamNumber().equals("G2-AREA")).findFirst().orElseThrow();
			assertEquals(Set.of(9, 10, 11, 12, 16, 30, 31, 32).stream()
					.map(n -> id("MR-P%02d".formatted(n))).collect(Collectors.toSet()),
					ground2.getResourcesAssigned().stream().map(SarTaskResource::getResourceId).collect(Collectors.toSet()));
			SarTaskAssignment canine3 = data.getSarTaskAssignments().stream()
					.filter(t -> t.getAssignmentTeamNumber().equals("K3")).findFirst().orElseThrow();
			assertEquals(Set.of(id("MR-P29"), id("MR-P13"), id("MR-P14"), id("MR-P15"), id("MR-K9-3")),
					canine3.getResourcesAssigned().stream().map(SarTaskResource::getResourceId).collect(Collectors.toSet()));
			for (int n : List.of(13, 14, 15)) {
				TCard support = data.getTCards().stream()
						.filter(c -> c.getResourceIdentifier().equals("MR-P%02d".formatted(n))).findFirst().orElseThrow();
				assertEquals("Available", support.getStatus());
				assertEquals(STAGING, support.getLocation());
				assertTrue(support.getNotes().contains("K3"));
				assertTrue(data.getSarTaskAssignments().stream()
						.filter(t -> t.getResourcesAssigned().stream().anyMatch(r -> r.getResourceId().equals(support.getResourceId())))
						.filter(t -> !"returned".equals(t.getTaskLifecycleStatus()))
						.allMatch(t -> !t.getAssignmentStart().isBefore(time(10, 30))));
			}
			SarTaskAssignment trackers = data.getSarTaskAssignments().stream()
					.filter(t -> t.getAssignmentTeamNumber().equals("T1")).findFirst().orElseThrow();
			assertEquals("returned", trackers.getTaskLifecycleStatus());
			assertEquals(id("task-T1"), trackers.getTaskId());
			assertEquals(id("assignment-T1"), trackers.getAssignmentId());
			assertEquals(time(7, 0), trackers.getAssignmentStart());
			assertEquals(time(8, 0), trackers.getAssignmentEnd());
			assertEquals(1, data.getSarTaskAssignments().stream()
					.filter(t -> "returned".equals(t.getTaskLifecycleStatus())).count());
			assertEquals(Set.of(id("MR-P13"), id("MR-P14"), id("MR-P15")),
					trackers.getResourcesAssigned().stream().map(SarTaskResource::getResourceId).collect(Collectors.toSet()));
			assertTrue(trackers.isDebriefingCompleted());
			assertEquals(OPS, trackers.getDebriefingSupervisor());
			assertEquals(id("MR-IMT-Ops"), trackers.getDebriefingSupervisorResourceId());
			assertTrue(trackers.getDebriefNotes().contains("definitely human"));
			assertTrue(trackers.getDebriefNotes().contains("north"));
			assertTrue(trackers.getDebriefNotes().contains("no clear prints or sole pattern"));
			assertFalse(trackers.getReportedPod().isBlank());
			assertFalse(trackers.getAreasNotCovered().isBlank());
			assertFalse(trackers.getHazardsObserved().isBlank());
			assertEquals(FIELD_NAMES.get(12), trackers.getDebriefPreparedByName());
			assertEquals("Visual tracker team leader", trackers.getDebriefPreparedByPositionTitle());
			assertEquals(time(8, 10), trackers.getDebriefPreparedDateTime());
			assertEquals(1, data.getClueLogEntries().size());
			ClueLogEntry clue = data.getClueLogEntries().get(0);
			assertEquals(trackers.getAssignmentId(), clue.getAssignmentId());
			assertEquals("T1", clue.getDetectingTask());
			assertEquals(trackers.getLeader(), clue.getDetectedBy());
			assertEquals(time(7, 30), clue.getDateTimeCollected());
			assertTrue(clue.getDescription().contains("definitely human"));
			assertTrue(clue.getDescription().contains("north"));
			assertTrue(clue.getDescription().contains("no clear prints or sole pattern"));
			assertTrue(clue.getImmediateAction().contains("flagged"));
			assertTrue(clue.getImmediateAction().contains("Ops"));
			assertTrue(clue.getFollowUp().contains("not yet undertaken"));
			assertEquals("Pending", clue.getFollowUpStatus());
			assertFalse(clue.isFollowUpBriefed());
			assertTrue(clue.getFollowUpAssignments().isEmpty());
			assertNull(clue.getFollowUpCompletedDate());
			assertTrue(data.getForm204().getSpecialInstructions().contains("T1"));
			assertTrue(data.getForm202().getSituationalAwareness().contains("potential clue"));
			for (int n : List.of(16, 30, 31, 32)) {
				TCard reserve = data.getTCards().stream()
						.filter(c -> c.getResourceIdentifier().equals("MR-P%02d".formatted(n))).findFirst().orElseThrow();
				assertEquals("Available", reserve.getStatus());
				assertEquals(STAGING, reserve.getLocation());
				assertTrue(reserve.getNotes().contains("reserve"));
			}
			assertEquals(7, data.getSarTaskAssignments().stream()
					.filter(t -> t.getTaskLifecycleStatus().equals("assigned - on task")).count());
			for (TCard person : data.getTCards().stream().filter(c -> FIELD_NAMES.contains(c.getPersonName())).toList()) {
				List<SarTaskAssignment> tasks = data.getSarTaskAssignments().stream()
						.filter(t -> t.getResourcesAssigned().stream().anyMatch(r -> r.getResourceId().equals(person.getResourceId())))
						.sorted(Comparator.comparing(SarTaskAssignment::getAssignmentStart)).toList();
				assertFalse(tasks.isEmpty(), person.getPersonName());
				for (int i = 1; i < tasks.size(); i++) {
					assertFalse(tasks.get(i).getAssignmentStart().isBefore(tasks.get(i - 1).getAssignmentEnd()),
							person.getPersonName());
				}
			}
			assertEquals(Set.of("Route", "Area", "Point"), data.getSarTaskAssignments().stream()
					.map(SarTaskAssignment::getTaskType).collect(Collectors.toSet()));
			assertEquals(Set.of("Ground", "Canine"), data.getSarTaskAssignments().stream()
					.map(SarTaskAssignment::getResourceType).collect(Collectors.toSet()));
			assertEquals(Set.of("assigned - on task", "planned", "returned"), data.getSarTaskAssignments().stream()
					.map(SarTaskAssignment::getTaskLifecycleStatus).collect(Collectors.toSet()));
			assertTrue(data.isOpenActionTrackerEnabled());
			assertEquals(List.of(time(10, 0), time(9, 0), time(13, 0), time(18, 30)),
					data.getOpenActionItems().stream().map(OpenActionItem::getTargetDate).toList());
			assertEquals(4, data.getOpenActionItems().size());
			assertTrue(data.getOpenActionItems().get(0).getItem().contains("ALL teams"));
			assertTrue(data.getOpenActionItems().get(1).getItem().contains("Hourly hydration/electrolyte"));
			assertTrue(data.getOpenActionItems().get(2).getItem().contains("MANDATORY"));
			assertTrue(data.getOpenActionItems().get(2).getPoc().contains(MEDL));
			assertTrue(data.getOpenActionItems().get(3).getPoc().contains(SAFETY));
			assertEquals(24, data.getActivityLogs().get(0).getActivityLog().size());
			assertOperationalTimeline(data, trackers);
			assertEquals(3, data.getForm215a().getEntries().size());
			assertEquals(3, data.getForm208().getHazardBlocks().size());
			for (int i = 0; i < data.getForm215a().getEntries().size(); i++) {
				SafetyAnalysisEntry analysis = data.getForm215a().getEntries().get(i);
				SafetyMessageBlock message = data.getForm208().getHazardBlocks().get(i);
				assertEquals(analysis.getHazardsRisks(), message.getHazard());
				assertEquals(analysis.getMitigations(), message.getPrecautions());
				assertEquals(List.of(analysis.getEntryId()), message.getSourceEntryIds());
			}
			assertFalse(data.getMedicalPlan().isAviationAssetsUsed());
			assertEquals(1, data.getMedicalPlan().getAidStations().size());
			assertEquals(BASE, data.getMedicalPlan().getAidStations().get(0).getLocation());
			assertEquals(BASE, data.getIncidentLocations().stream().filter(l -> "Base".equals(l.getType()))
					.findFirst().orElseThrow().getName());
			assertTrue(data.getMedicalPlan().getEmergencyProcedures().contains("unstaffed supplies/cache"));
			assertTrue(data.getMedicalPlan().getEmergencyProcedures().contains(BASE));
			assertEquals(1, data.getMedicalPlan().getTransportServices().size());
			assertEquals(2, data.getMedicalPlan().getHospitals().size());
			assertFalse(data.getMedicalPlan().getHospitals().get(0).getTraumaCenter());
			assertTrue(data.getMedicalPlan().getHospitals().get(1).getTraumaCenter());
			assertCanineMedicalPlan(data);
			assertTrue(data.getForm215a().getEntries().get(2).getMitigations().contains(BASE));
			assertTrue(data.getForm208().getGeneralRequirements().contains(BASE));
			assertTrue(data.getOpenActionItems().get(1).getItem().contains(BASE));
			assertTrue(data.getOpenActionItems().get(2).getItem().contains(BASE));
		} finally {
			deleteTree(work);
		}
	}

	@Test
	void fixtureIsTheReproducibleStoreExport() throws Exception {
		Path work = Files.createTempDirectory(Path.of("target"), "maple-build-");
		try (FileIncidentStore store = new FileIncidentStore(work.resolve("store"))) {
			Path destination = Boolean.getBoolean("mapleRidge.regenerate") ? FIXTURE : work.resolve("example.json");
			store.exportFile(store.create(buildExample()), destination);
			ObjectMapper mapper = new ObjectMapper();
			assertEquals(mapper.readTree(FIXTURE.toFile()), mapper.readTree(destination.toFile()));
		} finally {
			deleteTree(work);
		}
	}

	@Test
	void importedExampleRendersEveryIncludedMainFormAndIap() throws Exception {
		Path work = Files.createTempDirectory(Path.of("target"), "maple-pdf-");
		try (FileIncidentStore store = new FileIncidentStore(work.resolve("store"))) {
			AppData data = store.load(store.importFile(FIXTURE).orElseThrow());
			PdfExportService service = new PdfExportService(new Ics202PdfRenderer(), new Ics203PdfRenderer(),
					new Ics204PdfRenderer(), new Ics205PdfRenderer(), new Ics205aPdfRenderer(),
					new Ics206PdfRenderer(), new Ics206CaninePdfRenderer(), new Ics207PdfRenderer(), new Ics208PdfRenderer(),
					new Ics214PdfRenderer(), new Ics215aPdfRenderer(), new Ics233CgPdfRenderer(),
					new SarTaskAssignmentPdfRenderer());
			int includedPages = 1;
			for (String form : FORMS) {
				Path output = service.exportSelected(form, data, work.resolve("pdf"));
				try (var pdf = Loader.loadPDF(output.toFile())) {
					assertTrue(pdf.getNumberOfPages() > 0, form);
					includedPages += pdf.getNumberOfPages();
					String text = new PDFTextStripper().getText(pdf);
					assertTrue(text.contains("Maple Ridge"), form);
					if ("ICS 205".equals(form) || "ICS 205A".equals(form)) {
						assertTrue(text.contains(CHANNEL), form);
					}
					if ("ICS 202".equals(form)) {
						assertTrue(text.contains("07:00"), text);
						assertTrue(text.contains("19:00"), text);
						assertTrue(text.contains("By 1700"), text);
						assertTrue(text.replaceAll("\\s+", " ").contains("coverage target of approximately 1"), text);
						assertTrue(text.contains("50 Civic Road"), text);
					}
					if ("ICS 203".equals(form) || "ICS 207".equals(form)) {
						assertTrue(text.contains(IC), text);
					}
					if ("ICS 206".equals(form)) {
						String normalized = text.replaceAll("\\s+", " ");
						assertTrue(normalized.contains("20 Park Road"), text);
						assertTrue(normalized.contains(BASE), text);
						assertTrue(normalized.contains("unstaffed supplies/cache"), text);
						assertTrue(normalized.contains("Fictional Pine County Regional Trauma Center"), text);
					}
					if ("ICS 206CANINE".equals(form)) {
						String normalized = text.replaceAll("\\s+", " ");
						assertEquals(Ics206CaninePdfRenderer.pageCount(data), pdf.getNumberOfPages());
						assertTrue(normalized.contains(VETERINARIAN), text);
						assertTrue(normalized.contains("Fictional Cedar Vale Emergency Veterinary Hospital"), text);
						assertTrue(normalized.contains("Fictional Oak Hollow Veterinary Emergency Center"), text);
						assertTrue(normalized.contains("25-mile estimate from staging; not verified"), text);
						assertTrue(normalized.contains("45-mile estimate from staging; not verified"), text);
					}
					if ("ICS 233-CG".equals(form)) {
						assertTrue(text.contains("tick"), form);
						assertTrue(text.contains("hydration"), form);
					}
					if (List.of("ICS 215A", "ICS 208", "ICS 233-CG").contains(form)) {
						String normalized = text.replaceAll("\\s+", " ");
						assertTrue(normalized.contains("Base (supply and rehab)"), text);
						assertTrue(normalized.contains("heat rehab"), text);
						assertTrue(normalized.contains("qualified relief"), text);
					}
					if ("SAR Task Assignment".equals(form)) {
						assertTaskPdfPages(pdf, data);
					}
				}
			}
			Path bundle = service.exportIapBundle(data, work.resolve("iap"), FORMS);
			try (var pdf = Loader.loadPDF(bundle.toFile())) {
				assertEquals(includedPages, pdf.getNumberOfPages());
				String bundleText = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
				assertTrue(bundleText.contains("155.160"));
				assertTrue(bundleText.contains("Fictional Cedar Vale Emergency Veterinary Hospital"), bundleText);
				assertTrue(bundleText.contains("Fictional Oak Hollow Veterinary Emergency Center"), bundleText);
				int caninePage = Integer.parseInt(data.getCanineMedicalPlan().getIapPage()) + 1;
				PDFTextStripper caninePages = new PDFTextStripper();
				caninePages.setStartPage(caninePage);
				caninePages.setEndPage(caninePage + Ics206CaninePdfRenderer.pageCount(data) - 1);
				String canineText = caninePages.getText(pdf).replaceAll("\\s+", " ");
				assertTrue(canineText.contains(VETERINARIAN), canineText);
				assertTrue(canineText.contains("Fictional Cedar Vale Emergency Veterinary Hospital"), canineText);
				assertTrue(canineText.contains("Fictional Oak Hollow Veterinary Emergency Center"), canineText);
				int firstTaskIapPage = 1 + Ics202PdfRenderer.pageCount(data) + Ics203PdfRenderer.pageCount(data)
						+ Ics204PdfRenderer.pageCount(data);
				assertEquals(String.valueOf(firstTaskIapPage), data.getSarTaskAssignments().get(0).getIapPage());
				PDFTextStripper firstTaskPage = new PDFTextStripper();
				firstTaskPage.setStartPage(firstTaskIapPage + 1);
				firstTaskPage.setEndPage(firstTaskIapPage + 1);
				String text = firstTaskPage.getText(pdf).replaceAll("\\s+", " ");
				assertTrue(text.contains("SAR TASK ASSIGNMENT"), text);
				assertTrue(text.contains("IAP Page " + firstTaskIapPage), text);
			}
		} finally {
			deleteTree(work);
		}
	}

	/**
	 * Checks actual linked lifecycle events without implying that planned work has started.
	 *
	 * @param data imported exercise snapshot.
	 * @param tracker completed tracker task.
	 */
	private static void assertOperationalTimeline(AppData data, SarTaskAssignment tracker) {
		Ics214Form log = data.getActivityLogs().get(0);
		List<ActivityLogEntry> entries = log.getActivityLog();
		assertEquals(time(8, 15), log.getPreparedDateTime());
		assertEquals(entries.size(), entries.stream().map(ActivityLogEntry::getEntryId).distinct().count());
		assertEquals(entries.stream().map(ActivityLogEntry::getTimestamp).sorted().toList(),
				entries.stream().map(ActivityLogEntry::getTimestamp).toList());
		assertTrue(entries.stream().allMatch(e -> !e.getTimestamp().isAfter(time(8, 15))));
		assertEquals(time(6, 30), entries.get(0).getTimestamp());
		assertTrue(entries.get(0).getNotableActivity().contains("command established"));
		assertTrue(entries.get(0).getNotableActivity().contains("six IMT"));
		Set<String> eventTypes = data.getActivityEventTypes().stream().map(ActivityEventType::getId)
				.collect(Collectors.toSet());
		assertTrue(entries.stream().allMatch(e -> eventTypes.contains(e.getEventTypeId())));
		for (SarTaskAssignment task : data.getSarTaskAssignments()) {
			List<ActivityLogEntry> linked = entries.stream()
					.filter(e -> task.getAssignmentId().equals(e.getAssignmentId())).toList();
			assertTrue(linked.stream().allMatch(e -> task.getResourceIdentifier().equals(e.getResourceIdentifier())));
			if ("planned".equals(task.getTaskLifecycleStatus())) {
				assertTrue(linked.isEmpty(), task.getAssignmentTeamNumber());
			} else if ("assigned - on task".equals(task.getTaskLifecycleStatus())) {
				assertEquals(List.of(ActivityEventType.ID_RESOURCE_DEPARTED_STAGING, ActivityEventType.ID_RESOURCE_ON_TASK),
						linked.stream().map(ActivityLogEntry::getEventTypeId).toList(), task.getAssignmentTeamNumber());
				assertEquals(List.of(task.getAssignmentStart().minusMinutes(5), task.getAssignmentStart()),
						linked.stream().map(ActivityLogEntry::getTimestamp).toList(), task.getAssignmentTeamNumber());
				assertTrue(linked.get(0).getNotableActivity().contains(STAGING));
			}
		}
		List<ActivityLogEntry> trackerEntries = entries.stream()
				.filter(e -> tracker.getAssignmentId().equals(e.getAssignmentId())).toList();
		assertEquals(List.of(ActivityEventType.ID_RESOURCE_DEPARTED_STAGING, ActivityEventType.ID_RESOURCE_ON_TASK,
				ActivityEventType.ID_CLUE_DETECTED, ActivityEventType.ID_TASK_COMPLETED,
				ActivityEventType.ID_RESOURCE_RETURNED_STAGING, ActivityEventType.ID_FREE_TEXT),
				trackerEntries.stream().map(ActivityLogEntry::getEventTypeId).toList());
		assertEquals(List.of(time(7, 0), time(7, 5), time(7, 30), time(7, 50), time(8, 0), time(8, 10)),
				trackerEntries.stream().map(ActivityLogEntry::getTimestamp).toList());
		assertEquals(tracker.getAssignmentStart(), trackerEntries.get(0).getTimestamp());
		assertEquals(tracker.getAssignmentEnd(), trackerEntries.get(4).getTimestamp());
		assertEquals(tracker.getDebriefPreparedDateTime(), trackerEntries.get(5).getTimestamp());
		assertEquals(data.getClueLogEntries().get(0).getDateTimeCollected(), trackerEntries.get(2).getTimestamp());
		for (String detail : List.of("departed staging at 07:00", "on task at IPP at 07:05", "At 07:30",
				"completed at 07:50", "returned at 08:00", "Debrief completed 08:10")) {
			assertTrue(tracker.getDebriefNotes().contains(detail), detail);
		}
		assertTrue(trackerEntries.get(3).getNotableActivity().contains("returning"));
		assertTrue(trackerEntries.get(5).getNotableActivity().contains(tracker.getDebriefNotes()));
	}

	/**
	 * Checks complete assignment text and confines actual debrief content to the returned task's page.
	 *
	 * @param pdf rendered two-page-per-task fixture PDF.
	 * @param data source incident containing task narratives and linked clues.
	 * @throws java.io.IOException if the PDF text cannot be extracted.
	 */
	private static void assertTaskPdfPages(PDDocument pdf, AppData data) throws java.io.IOException {
		assertEquals(data.getSarTaskAssignments().size() * 2, pdf.getNumberOfPages());
		for (int i = 0; i < data.getSarTaskAssignments().size(); i++) {
			SarTaskAssignment task = data.getSarTaskAssignments().get(i);
			PDFTextStripper page = new PDFTextStripper();
			page.setStartPage(i * 2 + 1);
			page.setEndPage(i * 2 + 1);
			String front = page.getText(pdf).replaceAll("\\s+", " ");
			assertTrue(front.contains(task.getAssignment().replaceAll("\\s+", " ")),
					() -> task.getAssignmentTeamNumber() + " full assignment missing: " + front);
			assertTrue(front.contains(task.getTransportationInstructions().replaceAll("\\s+", " ")), front);
			page.setStartPage(i * 2 + 2);
			page.setEndPage(i * 2 + 2);
			String debrief = page.getText(pdf).replaceAll("\\s+", " ");
			if ("returned".equals(task.getTaskLifecycleStatus())) {
				for (String content : List.of(task.getDebriefNotes(), task.getReportedPod(), task.getAreasNotCovered(),
						task.getHazardsObserved(), task.getDebriefingSupervisor(), task.getDebriefPreparedByName(),
						task.getDebriefPreparedByPositionTitle())) {
					assertFalse(content.isBlank());
					assertTrue(debrief.contains(content.replaceAll("\\s+", " ")), debrief);
				}
				for (String clock : List.of("07:00", "07:05", "07:30", "07:50", "08:00", "08:10")) {
					assertTrue(debrief.contains(clock), debrief);
				}
				ClueLogEntry clue = data.getClueLogEntries().get(0);
				assertTrue(debrief.contains(clue.getDescription()), debrief);
				assertTrue(debrief.contains(clue.getImmediateAction()), debrief);
				assertTrue(debrief.contains(clue.getFollowUp()), debrief);
			} else {
				assertFalse(debrief.matches(".*\\b\\d{2}:\\d{2}\\b.*"), debrief);
				for (String actual : List.of("Clues Detected:", "Qualitative POD Factors", "Canine Type:",
						"Canine Assignment Conditions:", "REPORTED POD:", "Partly cloudy", "Light and variable",
						"definitely human", OPS, PLANNING)) {
					assertFalse(debrief.contains(actual), debrief);
				}
				for (String condition : List.of(task.getCanineSearchType(), task.getCanineCloudCover(),
						task.getCanineWindSpeed())) {
					if (!condition.isBlank()) {
						assertFalse(debrief.contains(condition), debrief);
					}
				}
			}
		}
	}

	/**
	 * Checks veterinary personnel reuse, intentionally absent services and hypothetical facility snapshots.
	 *
	 * @param data imported exercise snapshot.
	 */
	private static void assertCanineMedicalPlan(AppData data) {
		var plan = data.getCanineMedicalPlan();
		assertTrue(plan.isActive());
		assertTrue(data.getForm202().getIncidentActionPlanAttachments().contains("ICS 206CANINE"));
		assertEquals("", plan.getAidStationInformation());
		assertEquals("", plan.getTransportServices());
		assertTrue(plan.getTransportServiceEntries().isEmpty());
		assertFalse(plan.isAviationAssetsUsed());
		assertEquals(List.of(id("MR-P25")), plan.getVeterinaryPersonnelResourceIds());
		assertEquals(VETERINARIAN, plan.getOnsiteVeterinarian());
		assertTrue(plan.getVeterinarianDvm().contains("DVM"));
		TCard veterinarian = data.getTCards().stream().filter(c -> id("MR-P25").equals(c.getResourceId()))
				.findFirst().orElseThrow();
		assertEquals(VETERINARIAN, veterinarian.getPersonName());
		assertEquals("Assigned", veterinarian.getStatus());
		assertTrue(veterinarian.getNotes().contains("Doctor of Veterinary Medicine (DVM)"));
		assertTrue(veterinarian.getNotes().contains("not a dedicated medical post"));
		assertTrue(veterinarian.getSourceRef().startsWith("sar:" + id("assignment-K2")));
		SarTaskAssignment task = data.getSarTaskAssignments().stream()
				.filter(t -> "K2".equals(t.getAssignmentTeamNumber())).findFirst().orElseThrow();
		assertEquals(VETERINARIAN, task.getLeader());
		assertEquals("Leader/Handler", task.getLeaderRole());
		assertTrue(task.getResourcesAssigned().stream().anyMatch(r -> veterinarian.getResourceId().equals(r.getResourceId())));
		assertTrue(plan.getVeterinaryCapabilityNotes().contains("Ops must release or retask K2"));
		for (String detail : List.of("first aid only within training", "contact Ops", "Ops contacts " + VETERINARIAN,
				"releases or retasks", "arranges veterinary care and transport")) {
			assertTrue(plan.getEmergencyProcedures().contains(detail), detail);
		}
		assertEquals(2, plan.getSelectedFacilities().size());
		for (int i = 0; i < plan.getSelectedFacilities().size(); i++) {
			VetFacility facility = plan.getSelectedFacilities().get(i);
			assertEquals(id("vet-facility-" + (i + 1)), facility.getId());
			assertEquals(Boolean.TRUE, facility.getAvailable24Hours());
			assertTrue(facility.getName().startsWith("Fictional"));
			assertEquals((i == 0 ? 25 : 45) * 1609.344, facility.getDistanceMeters(), 0.001);
			assertEquals(data.getStagingLocation().getGeneration(), facility.getStagingGeneration());
			assertTrue(facility.hasCurrentDistance(data.getStagingLocation().getGeneration()));
			assertTrue(facility.getDistanceMeters() >= 20 * 1609.344 && facility.getDistanceMeters() <= 50 * 1609.344);
			assertTrue(facility.getDistanceProvenance().contains("not a real route measurement"));
			assertTrue(facility.getAvailabilityNotes().contains("Hypothetical 24-hour"));
			assertEquals(List.of("202-555-01" + (i == 0 ? "21" : "22") + " (fictional, non-operational)"),
					facility.getContactNumbers());
			assertNull(facility.getVerifiedAt());
			assertNull(facility.getCalculatedAt());
		}
	}

	/**
	 * Builds a fully fictional active summer operational period using only model APIs.
	 *
	 * @return portable example with deterministic identities and timestamps.
	 */
	private static AppData buildExample() {
		AppData data = new AppData();
		data.setIncidentId(id("incident"));
		data.setOriginNodeId(id("origin"));
		data.getForm215().setWorksheetId(id("ics215-current"));
		data.getNextPeriodForm215().setWorksheetId(id("ics215-next"));
		data.setCreatedAt(Instant.parse("2026-07-18T10:30:00Z"));
		data.setUseSystemTimeZone(false);
		data.setConfiguredTimeZoneId("America/New_York");
		data.setIncidentMode(IncidentMode.SAR);
		data.setIapPhase(IapPhase.DURING_OP);
		data.setIapPlanningStage(IapPlanningStage.OPERATIONAL_PERIOD_ACTIVE);
		data.setIncidentContext(new IncidentContext(INCIDENT, time(7, 0), time(19, 0), PLANNING,
				"Planning Section Chief - fictional exercise"));
		data.getIncidentContext().setTaskMap("MR-EX-01: fictional residence IPP, separate staging and ICP, spokes A/B/C, rim R1/R2, zones K1-K4");
		data.getStagingLocation().setLabel(STAGING);
		data.getStagingLocation().setAddress("20 Park Road, fictional Maple Ridge, MA");
		addLocation(data, "icp", "ICP", ICP, "50 Civic Road, fictional Maple Ridge, MA");
		addLocation(data, "staging", "Staging Area", STAGING, "20 Park Road, fictional Maple Ridge, MA");
		addLocation(data, "base", "Base", BASE,
				"20 Park Road, fictional Maple Ridge, MA");
		addLocation(data, "ipp", "Other", IPP, "1 Trail Lane, fictional Maple Ridge, MA");
		data.getForm201().setIncidentName(INCIDENT);
		data.getForm201().setIncidentNumber("EXERCISE-MR-2026-01");
		data.getForm201().setSituationSummary("FICTIONAL ILLUSTRATION ONLY; not an operational plan. "
				+ "Ellis Linden, fictional 74-year-old adult with dementia, wandered from a residence "
				+ "at " + IPP + "; last seen there at 0500 July 18. Staging: " + STAGING + ". Command staff: " + ICP + ". "
				+ "Wearing a blue shirt and tan trousers; may not respond to calls. "
				+ "Returned three-person visual tracker task T1 (0700-0800), debriefed at 0810, reported a definitely human sign line north "
				+ "from IPP, with no clear prints or sole pattern; potential clue flagged on Ops instruction, "
				+ "subject association unverified and follow-up not yet undertaken at the 0815 snapshot. "
				+ "Summer forecast 27-31 C, humid, light wind; shaded woodland, uneven trails, creek edges and ticks. No aircraft. "
				+ "Koster hub/spokes/rim: protect the residence/last-known-point hub; stage away from the IPP; "
				+ "search travel spokes promptly; "
				+ "observe likely rim exits, then search bounded areas and familiar attractors. "
				+ "G1 splits into four-person G1-A/G1-B on routes A/B; four-person G2-A takes route C, all 0715-1000. "
				+ "Returned trackers MR-P13/MR-P14/MR-P15 await K3 at staging; one G2 member and three released K3 support remain reserve "
				+ "until the eight-person G2 grid reassembles at 1030. G1 reassembles eight for attractors then grid. "
				+ "All names, agencies, geography, contacts, capabilities and approvals are invented.");
		addOrganization(data);
		addCommunications(data);
		addObjectives(data);
		addCards(data);
		addAssignments(data);
		addSafety(data);
		addMedical(data);
		addActionsAndLog(data);
		data.normalizeIdentity();
		return data;
	}

	/**
	 * Adds a shared, deterministically identified operational location.
	 *
	 * @param data example document.
	 * @param key stable fixture identity.
	 * @param type supported location category.
	 * @param name displayed location and use.
	 * @param address fictional street address.
	 */
	private static void addLocation(AppData data, String key, String type, String name, String address) {
		IncidentLocation location = new IncidentLocation();
		location.setId(id("location-" + key));
		location.setType(type);
		location.setName(name);
		location.setAddress(address);
		data.getIncidentLocations().add(location);
	}

	/**
	 * Adds six ICP personnel; assignment leaders are represented by their linked tasks only.
	 *
	 * @param data example document.
	 */
	private static void addOrganization(AppData data) {
		var org = data.getOrganizationalChart();
		org.setIncidentCommanders(List.of(IC));
		org.setIncidentCommanderRadio(CHANNEL);
		org.setOperationsSectionChiefName(OPS);
		org.setOperationsSectionChiefRadio(CHANNEL);
		org.setPlanningSectionChiefName(PLANNING);
		org.setPlanningSectionChiefRadio(CHANNEL);
		org.setSafetyOfficerName(SAFETY);
		org.setSafetyOfficerRadio(CHANNEL);
		org.setCommunicationsUnitLeaderName(COML);
		org.setCommunicationsUnitLeaderRadio(CHANNEL);
		data.getMedicalUnitLeaderAssignment().setName(MEDL);
		data.getMedicalUnitLeaderAssignment().setRadioChannel(CHANNEL);
		data.getMedicalUnitLeaderAssignment().setPhoneNumber("202-555-0107");
		org.getAssignmentList().setPreparedByName(PLANNING);
		org.getAssignmentList().setPreparedByPositionTitle("Planning Section Chief");
		org.getAssignmentList().setPreparedDateTime(time(6, 45));
	}

	/**
	 * Defines one illustrative simplex analog VHF channel; this is not frequency authorization.
	 *
	 * @param data example document.
	 */
	private static void addCommunications(AppData data) {
		RadioChannel channel = new RadioChannel();
		channel.setChannelId(id("channel"));
		channel.setZoneGroup("EXERCISE");
		channel.setChannelNumber("1");
		channel.setChannelName(CHANNEL);
		channel.setFunction("Command / tactical / medical");
		channel.setAssignment("All Maple Ridge exercise personnel");
		channel.setRxFrequency("155.160");
		channel.setTxFrequency("155.160");
		channel.setRxBandwidth("12.5 kHz");
		channel.setTxBandwidth("12.5 kHz");
		channel.setRxToneNac("CSQ");
		channel.setTxToneNac("None");
		channel.setMode("FM");
		channel.setSystem("VHF simplex analog FM");
		channel.setTxPolicy(RadioChannel.TX_POLICY_TRANSMIT);
		channel.setCoverageGuidance("Illustrative local simplex only; terrain may block coverage.");
		channel.setRelayGuidance("If no contact, ground/canine teams move safely with buddies to last working point; "
				+ "observation teams retain posts and use the other radio or relay through another team.");
		channel.setRadioCheckStatus(RadioCheckStatus.SUCCESSFUL);
		channel.setRadioCheckedBy(COML);
		channel.setRadioCheckedAt(time(6, 50));
		channel.setRadioCheckNotes("Simulated exercise check at ICP, staging, IPP and rim; not a real coverage survey.");
		channel.setRemarks("Fictional example, not permission to transmit. Verify license, bandwidth and local plan.");
		data.getForm205().setFormId(id("205"));
		data.getForm205().setChannels(List.of(channel));
		data.getForm205().setPreparedByName(COML);
		data.getForm205().setPreparedDateTime(time(6, 45));
		data.getForm205().setDatePrepared(time(6, 45));
		data.getForm205().setSpecialInstructions("Plain language. Position/status checks every 30 minutes. "
				+ "Emergency traffic has priority; report location, injury, access and assistance needed.");
	}

	/**
	 * Adds measurable safety, spoke, containment, area-search and attractor objectives.
	 *
	 * @param data example document.
	 */
	private static void addObjectives(AppData data) {
		List<String> objectives = List.of(
				"Mitigate all hazards to responders and the general public to an acceptable level, "
						+ "with particular attention to heat stress and tick exposure, "
						+ "throughout the entire duration of the incident.",
				"By 1000, complete hasty searches of the three most likely travel corridors from IPP to containment boundary at least once.",
				"By 0800, establish containment of both likely egress points and maintain it through 1900.",
				"By 1700, search the high-probability inner 25% zone to a coverage target of approximately 1.",
				"By 1200, search all three familiar attractors and associated hazard points within 1 km of IPP.");
		List<IncidentObjective> structured = new ArrayList<>();
		for (int i = 0; i < objectives.size(); i++) {
			IncidentObjective objective = new IncidentObjective(objectives.get(i), i + 1);
			objective.setObjectiveId(id("objective-" + i));
			structured.add(objective);
		}
		data.getForm202().setStructuredObjectives(structured);
		data.getForm202().setCommandEmphasis("FICTIONAL EXERCISE ONLY. Prioritize responder/public safety and protect IPP scent. "
				+ "Hasty-search likely travel corridors and familiar attractors; maintain containment. "
				+ "Search the high-probability inner 25% zone to coverage approximately 1; reassess heat and tick hazards.");
		data.getForm202().setSituationalAwareness("0815 July 18, 2026 exercise snapshot. Fictional Ellis Linden, "
				+ "74-year-old adult with dementia, last seen 0500 July 18 at " + IPP + ". "
				+ "Wearing a blue shirt and tan trousers; may hide or not answer. "
				+ "T1's three visual trackers returned at 0800 after 0700-0800 work around IPP, debriefed at 0810: a definitely human sign line "
				+ "led north, with no clear prints or sole pattern. This potential clue was flagged on Ops instruction; "
				+ "subject association remains unverified and follow-up has not yet been undertaken. "
				+ "Forecast 27-31 C, humid, light wind; shaded woodland, uneven trails and creek edges. "
				+ "Staging: " + STAGING + ". Separate command post: " + ICP + ". "
				+ BASE + " provides water resupply, an unstaffed human aid-supply cache and heat rehab coordination. "
				+ "All teams report directly to Ops; no branches, divisions or groups. "
				+ "36 human field searchers INCLUDE four handlers. "
				+ "Six IMT at " + ICP + "; IC " + IC + " is Sheriff of " + SHERIFF_AGENCY + ". No aircraft. "
				+ "Seven teams active at 0815; returned trackers are available at staging for K3 at 1030. "
				+ "Ops must reassess span before later launches and stagger/pause work "
				+ "if direct supervision becomes unsafe; eight teams is not assumed manageable. "
				+ "Koster hub/spokes/rim guides prioritization, not a guarantee of containment. "
				+ "No negative search guarantees absence. Summer heat and ticks require continuous reassessment.");
		data.getForm202().setPreparedByName(PLANNING);
		data.getForm202().setPreparedByPositionTitle("Planning Section Chief");
		data.getForm202().setSiteSafetyPlanRequired(false);
		data.getForm202().setIncidentActionPlanAttachments(FORMS.stream().filter(f -> !"ICS 202".equals(f)).toList());
		data.getForm202().recordObjectiveApproval(IC, "FICTIONAL EXERCISE approval only", time(6, 55));
	}

	/**
	 * Adds 36 field personnel, six ICP staff and four handler-linked canine equipment cards.
	 * MEDL is a manual staff card because directory source references do not support that role.
	 *
	 * @param data example document.
	 */
	private static void addCards(AppData data) {
		for (int i = 0; i < FIELD_NAMES.size(); i++) {
			TCard card = card("MR-P%02d".formatted(i + 1), FIELD_NAMES.get(i), TCardType.PERSONNEL);
			card.setNumberOfPersons(1);
			card.setStatus(i >= 28 || i >= 12 && i < 16 ? "Available" : "Assigned");
			card.setLocation(i < 4 ? "Spoke A" : i < 8 ? "Spoke B" : i < 12 ? "Spoke C" : i < 16 ? STAGING : i < 20 ? "Rim R" + (1 + (i - 16) / 2)
					: i < 28 ? "K" + (1 + (i - 20) / 4) : STAGING);
			card.setHomeAgency(i < 8 ? "Fictional Cedar Valley SAR" : i < 16 ? "Fictional Lake Hollow SAR" : AGENCY);
			card.setNotes("Fictional field searcher" + (i >= 20 && (i - 20) % 4 == 0 ? "; canine handler" : "; buddy-system assignment"));
			if (i >= 12 && i < 15) {
				card.setNotes("Fictional visual tracker returned from T1 (0700-0800), debriefed at 0810; "
						+ "G2 support available at staging for K3 handler Devon Heath at 1030; not on hasty route or G2 grid.");
			} else if (i == 15 || i >= 29 && i <= 31) {
				card.setNotes("Fictional staging reserve until G2 eight-person grid at 1030"
						+ (i == 15 ? "; G2 eighth member." : "; released from K3 support, replaced by MR-P13/MR-P14/MR-P15."));
			} else if (i == 24) {
				card.setNotes("Fictional canine handler and Doctor of Veterinary Medicine (DVM); "
						+ "onsite veterinary personnel MR-P25, tasked on K2 search, not a dedicated medical post. "
						+ "Ops must release or retask this handler before veterinary assistance; credentials are exercise assumptions only.");
			}
			data.getTCards().add(card);
		}
		List<String> overhead = List.of(IC, OPS, PLANNING, SAFETY, COML, MEDL);
		List<String> roles = List.of("IC", "Ops", "Planning", "Safety", "COML", "MEDL");
		List<String> sourceRefs = List.of("org:ic:0", "org:operationsChief", "org:planningChief",
				"org:safetyOfficer", "org:commUnitLeader", "");
		for (int i = 0; i < overhead.size(); i++) {
			TCard card = card("MR-IMT-" + roles.get(i), overhead.get(i), TCardType.PERSONNEL);
			card.setSourceRef(sourceRefs.get(i));
			card.setNumberOfPersons(1);
			card.setStatus("ICP");
			card.setLocation(ICP);
			card.setPhoneNumber("202-555-010" + (i == 5 ? 7 : i + 1));
			card.setNotes("Fictional ICP staff, separate from 36 field searchers; " + roles.get(i)
					+ (i == 0 ? "; Sheriff of " + SHERIFF_AGENCY : ""));
			if (i == 0) {
				card.setHomeAgency(SHERIFF_AGENCY);
			}
			data.getTCards().add(card);
		}
		for (int i = 0; i < 4; i++) {
			TCard dog = card("MR-K9-" + (i + 1), "", TCardType.EQUIPMENT);
			dog.setCanineResource(true);
			dog.setHandlerName(FIELD_NAMES.get(20 + i * 4));
			dog.setNumberOfPersons(0);
			dog.setStatus(i < 2 ? "Assigned" : "Available");
			dog.setLocation(i < 2 ? "K" + (i + 1) : STAGING);
			dog.setNotes("Fictional live-find area-search dog " + List.of("Bramble", "Cedar", "Pebble", "Thistle").get(i)
					+ "; handler MR-P%02d; no implied certification.".formatted(21 + i * 4));
			data.getTCards().add(dog);
		}
	}

	/**
	 * Creates a fully identified checked-in fictional resource.
	 *
	 * @param identifier displayed resource identifier.
	 * @param person person name, empty only for equipment.
	 * @param type card category.
	 * @return resource card.
	 */
	private static TCard card(String identifier, String person, TCardType type) {
		TCard card = new TCard();
		card.setResourceId(id(identifier));
		card.setResourceIdentifier(identifier);
		card.setPersonName(person);
		card.setCardType(type);
		card.setHomeAgency(AGENCY);
		card.setHomeState("MA");
		card.setStatus("Assigned");
		card.setCheckInDateTime(time(6, 30));
		card.setRadioChannel(CHANNEL);
		card.setRadioChannelId(id("channel"));
		return card;
	}

	/**
	 * Adds primary ICS 204 rows, linked search tasks and the returned tracker task.
	 *
	 * @param data example document.
	 */
	private static void addAssignments(AppData data) {
		var form = data.getForm204();
		form.setFormId(id("204"));
		form.setStagingArea(STAGING);
		form.setOperationsSectionChiefName(OPS);
		form.setOperationsSectionChiefContact(CHANNEL);
		form.setSharedWorkAssignment("Koster hub/spokes/rim. All teams report directly to Ops; protect the hub. "
				+ "No aircraft. Human field strength 36 including four canine handlers; dogs do not add personnel. "
				+ "G1-A/G1-B search routes A/B concurrently; G2-A searches C, four people each, 0715-1000. "
				+ "MR-P13/MR-P14/MR-P15 completed visual tracker task T1 around IPP 0700-0800, debriefed at 0810, "
				+ "and are available at staging for K3 at 1030 with handler MR-P29. "
				+ "Released K3 support MR-P30/MR-P31/MR-P32 and G2 member MR-P16 remain staging reserve until "
				+ "joining MR-P09 through MR-P12 for eight-person G2 grid at 1030. "
				+ "G1 reassembles eight for attractors at 1030 and grid at 1230. "
				+ "IPP: " + IPP + ". ICP: " + ICP + ". Staging is separate from both.");
		form.setSpecialInstructions("FICTIONAL EXERCISE ONLY. Calm approach to vulnerable adult; do not chase. "
				+ "Preserve clues, report location immediately, avoid contaminating scent articles. "
				+ "T1 reported a definitely human sign line north of IPP, with no clear prints or sole pattern; "
				+ "Ops instructed trackers to flag the potential clue and return. Subject association unverified; "
				+ "follow-up not yet undertaken at 0815. Preserve the flagged location pending Ops direction. "
				+ "Check in every 30 minutes. Buddy contact, tick checks, water and shaded rest per ICS 208. "
				+ "Seven teams initially active; Ops reassesses span before later launches and staggers/pauses work "
				+ "rather than assuming eight active teams manageable. No simultaneous tasks for any member.");
		form.setCommunications(contacts(data));
		form.setPreparedByName(PLANNING);
		form.setPreparedByPositionTitle("Planning Section Chief");
		form.setPreparedDateTime(time(6, 50));
		for (int i = 0; i < 3; i++) {
			String route = List.of("A north footpath 1.2 km to north gate R1", "B east lane 1.0 km to footbridge R2",
					"C south creek trail 0.8 km to rim marker C").get(i);
			addTask(data, "S" + (i + 1), List.of("G1-A", "G1-B", "G2-A").get(i), "Ground", "Route", i * 4, 4,
					"Rapid hasty route search of spoke " + route + "; inspect adjacent hazards from safe ground. "
							+ "Report GPS track, clues and unsearched gaps to Ops at task end; debrief by radio, "
							+ "take water/rest before next task; do not contaminate the residence IPP.",
					1, time(7, 15), time(10, 0), "on task", -1);
		}
		for (int i = 0; i < 2; i++) {
			int first = 16 + i * 2;
			addTask(data, "R" + (i + 1), "Observation " + (i + 1), "Ground", "Point", first, 2,
					"Establish observation at " + (i == 0 ? "R1 north gate" : "R2 east footbridge")
							+ " by 0800, maintain through 1900; report sightings. Not an impermeable perimeter. "
							+ FIELD_NAMES.get(first) + " observes while " + FIELD_NAMES.get(first + 1)
							+ " rests in adjacent shaded shelter 1300-1320; reverse roles 1320-1340. "
							+ "Repeat staggered water/rest at least hourly and as needed, retaining buddy voice/visual contact "
							+ "and uninterrupted observation. Both stay on this task; no borrowed search-team relief. "
							+ "If safe rest cannot be maintained, notify Ops for reassessment; do not silently abandon post. "
							+ "1830  preparations do not authorize leaving before 1900.",
					2, time(7, 15), time(19, 0), "on task", -1);
		}
		for (int i = 0; i < 4; i++) {
			List<Integer> members = i == 2 ? List.of(28, 12, 13, 14)
					: IntStream.range(20 + i * 4, 24 + i * 4).boxed().toList();
			addTask(data, "K" + (i + 1), "Canine " + (i + 1), "Canine", "Area", members,
					"Search bounded high-probability inner-ring (25% zone) K" + (i + 1)
							+ " (15-hectare coverage target) beside spoke " + (char) ('A' + i % 3)
							+ "; coordinate nonoverlapping ground subsegments with G1/G2 toward coverage approximately 1 "
							+ "over the 60-hectare zone, not repeated sweeps of the same tracks. "
							+ "Prioritize downwind segments after checking wind; target completion by 1700. "
							+ "Handler and three support searchers work live-find area search "
							+ "in 20-minute bouts with at least 20-minute shaded cooling/water breaks. "
							+ "Reassess at each break; stop for heat distress, unsafe footing or loss of radio contact. "
							+ "Report actual GPS coverage and gaps; no assumed POD. Rest rotation at 1300."
							+ (i == 2 ? " At 1030 handler MR-P29 takes available G2 support MR-P13/MR-P14/MR-P15; "
									+ "original K3 support MR-P30/MR-P31/MR-P32 are released to staging reserve until G2 grid." : ""),
					3, time(i < 2 ? 7 : 10, 30), time(17, 0), i < 2 ? "on task" : "planned", i);
		}
		addTask(data, "P1", "G1", "Ground", "Point", 0, 8,
				"After both S1/S2 debriefs and water/rest, reassemble all eight G1 members and search all three attractors within 1 km of residence/IPP by 1200: "
						+ "former bus stop, garden pond/shelter and creek crossing beside old meeting hall. "
						+ "Inspect hazard/water points from safe ground, no water entry. "
						+ "Ask permission before entry, use calm introductions, document each check and negative result; "
						+ "report gaps to Ops. Do not depart while either S1 or S2 remains active.",
				4, time(10, 30), time(12, 0), "planned", -1);
		for (int i = 0; i < 2; i++) {
			List<Integer> members = i == 0 ? IntStream.range(0, 8).boxed().toList()
					: List.of(8, 9, 10, 11, 15, 29, 30, 31);
			addTask(data, "G" + (i + 1) + "-AREA", "G" + (i + 1), "Ground", "Area", members,
					"After prior tasks and water/rest, grid assigned subsegments of the high-probability inner 25% zone "
							+ (i == 0 ? "with all eight original G1 members. "
									: "with MR-P09 through MR-P12, reserve MR-P16 and released K3 support MR-P30/MR-P31/MR-P32; "
											+ "MR-P13/MR-P14/MR-P15 remain with K3, not G2 grid. ")
							+ "(K1/K2 for G1; K3/K4 for G2). Ops coordinates boundaries with canine teams. "
							+ "Target zone coverage approximately 1 by 1700; record actual GPS tracks, spacing and gaps, "
							+ "reassess detection estimates rather than assuming success. Take shaded rest/heat checks at 1300 "
							+ "and as needed; hourly hydration checks. No water entry.",
					3, i == 0 ? time(12, 30) : time(10, 30), time(17, 0), "planned", -1);
		}
		addReturnedTrackerTask(data);
	}

	/**
	 * Records completed visual tracking and its still-pending potential clue.
	 *
	 * @param data incident document containing the later K3 support assignment.
	 */
	private static void addReturnedTrackerTask(AppData data) {
		addTask(data, "T1", "Visual trackers", "Ground", "Area", List.of(12, 13, 14),
				"Three-person visual tracker examination around residence/IPP to establish direction of travel. "
						+ "Protect sign and scent, avoid trampling, report potential clues to Ops; "
						+ "return to staging for debrief before further tasking. Completed task retained in this IAP.",
				1, time(7, 0), time(8, 0), "returned", -1);
		SarTaskAssignment task = data.getSarTaskAssignments().get(data.getSarTaskAssignments().size() - 1);
		task.setPreparedDateTime(time(6, 32));
		task.setDebriefingSupervisor(OPS);
		task.setDebriefingSupervisorResourceId(id("MR-IMT-Ops"));
		task.setDebriefNotes("Actual assignment 07:00-08:00: departed staging at 07:00, on task at IPP at 07:05. "
				+ "At 07:30, a definitely human sign line led north "
				+ "from the residence/IPP; no clear prints or sole pattern and no subject identification. "
				+ "Potential clue flagged on Ops instruction; no follow-up yet. Task completed at 07:50, returning to staging; "
				+ "all three returned at 08:00. Debrief completed 08:10 and available for later K3 at 1030.");
		task.setReportedPod("Not estimated; directional sign examination only, not a systematic area search.");
		task.setAreasNotCovered("Only immediate IPP ground examined; northbound sign beyond the flagged location "
				+ "and the wider woodland were not followed or searched by T1.");
		task.setHazardsObserved("Uneven ground, ticks and creek-edge footing; buddy contact maintained, no injuries reported.");
		task.setDebriefPreparedByName(task.getLeader());
		task.setDebriefPreparedByPositionTitle("Visual tracker team leader");
		task.setDebriefPreparedDateTime(time(8, 10));
		task.setDebriefingCompleted(true);
		ClueLogEntry clue = new ClueLogEntry();
		clue.setEntryId(id("clue-T1-north-sign"));
		clue.setAssignmentId(task.getAssignmentId());
		clue.setDetectingTask("T1");
		clue.setDetectedBy(task.getLeader());
		clue.setDateTimeCollected(time(7, 30));
		clue.setLocation("North edge of " + IPP + "; flag T1-1 on exercise sketch MR-EX-01");
		clue.setDescription("A definitely human sign line leads north from IPP; no clear prints or sole pattern. "
				+ "Potential clue only; association with Ellis Linden is unverified.");
		clue.setImmediateAction("Potential clue flagged in place on Ops instruction; location reported to "
				+ OPS + "; no physical evidence collected.");
		clue.setFollowUp("Pending Ops assessment and direction; follow-up not yet undertaken at the 0815 snapshot.");
		clue.setFollowUpStatus("Pending");
		data.getClueLogEntries().add(clue);
	}

	/**
	 * Adds a linked assignment and copies its identified personnel/equipment into a SAR task.
	 *
	 * @param data incident document.
	 * @param number assignment/team number.
	 * @param team team display name.
	 * @param resourceType Ground or Canine.
	 * @param taskType Route, Area or Point.
	 * @param first first human card index.
	 * @param count number of human members.
	 * @param assignment detailed work and completion target.
	 * @param objective zero-based linked objective.
	 * @param start planned start.
	 * @param end planned end.
	 * @param status recorded task lifecycle.
	 * @param dogIndex canine index, or minus one for ground tasks.
	 */
	private static void addTask(AppData data, String number, String team, String resourceType, String taskType,
			int first, int count, String assignment, int objective, LocalDateTime start, LocalDateTime end,
			String status, int dogIndex) {
		addTask(data, number, team, resourceType, taskType, IntStream.range(first, first + count).boxed().toList(),
				assignment, objective, start, end, status, dogIndex);
	}

	/**
	 * Adds a linked assignment with an explicit, possibly reassembled membership.
	 *
	 * @param data incident document.
	 * @param number stable assignment/team number.
	 * @param team displayed team name.
	 * @param resourceType Ground or Canine.
	 * @param taskType Route, Area or Point.
	 * @param members human card indices, leader first.
	 * @param assignment work instructions.
	 * @param objective zero-based objective index.
	 * @param start task start.
	 * @param end task end.
	 * @param status lifecycle status.
	 * @param dogIndex canine index, or minus one for ground tasks.
	 */
	private static void addTask(AppData data, String number, String team, String resourceType, String taskType,
			List<Integer> members, String assignment, int objective, LocalDateTime start, LocalDateTime end,
			String status, int dogIndex) {
		int first = members.get(0);
		ResourceAssignment row = new ResourceAssignment();
		row.setAssignmentId(id("assignment-" + number));
		row.setAssignmentTeamNumber(number);
		row.setResourceIdentifier(team);
		row.setResourceType(resourceType);
		row.setTaskType(taskType);
		row.setLeader(FIELD_NAMES.get(first));
		row.setLeaderRole(dogIndex >= 0 ? "Leader/Handler" : "Leader");
		row.setNumberOfPersons(members.size());
		row.setContact(CHANNEL);
		row.setReportingLocation(STAGING);
		row.setAssignment(assignment);
		row.setObjectiveIds(List.of(id("objective-0"), id("objective-" + objective)));
		row.setSpecialEquipment(dogIndex < 0 ? "Radio, map, GPS, first-aid kit" : "Live-find canine, radio, GPS, canine water");
		row.setSupplies("Water, sun protection, repellent per label, tick-removal kit");
		row.setRemarks("FICTIONAL EXERCISE; directly supervised by Ops; " + status);
		data.getForm204().getResourcesAssigned().add(row);
		SarTaskAssignment task = new SarTaskAssignment();
		task.setTaskId(id("task-" + number));
		task.setAssignmentId(row.getAssignmentId());
		task.setAssignmentTeamNumber(number);
		task.setResourceIdentifier(team);
		task.setResourceType(resourceType);
		task.setTaskType(taskType);
		task.setIncidentName(INCIDENT);
		task.setLeader(row.getLeader());
		task.setLeaderRole(row.getLeaderRole());
		task.setAssignment(assignment);
		task.setContact(CHANNEL);
		task.setStagingArea(STAGING);
		task.setTaskMap(data.getIncidentContext().getTaskMap());
		task.setOperationsSectionChiefName(OPS);
		task.setOperationsSectionChiefContact(CHANNEL);
		task.setAssignmentStart(start);
		task.setAssignmentEnd(end);
		task.setTaskLifecycleStatus(status);
		task.setSpecialEquipment(row.getSpecialEquipment());
		task.setTransportationInstructions(
				"Walk from staging or drive a personal vehicle from staging. No ground support unit or aircraft is assigned.");
		task.setCommunications(contacts(data));
		task.setPreparedByName(PLANNING);
		task.setPreparedByPositionTitle("Planning Section Chief");
		task.setPreparedDateTime(time(6, 50));
		for (int i : members) {
			TCard card = data.getTCards().get(i);
			if (card.getSourceRef().isBlank()) {
				card.setSourceRef("sar:" + row.getAssignmentId() + (i == first ? ":leader" : ":resource:" + card.getResourceId()));
			}
			String function = i == first ? task.getLeaderRole()
					: dogIndex >= 0 && i == members.get(1) ? "Communications"
							: dogIndex >= 0 && i == members.get(2) ? "Navigation" : "Ground Searcher";
			task.getResourcesAssigned().add(taskResource(card, function));
		}
		if (dogIndex >= 0) {
			TCard dog = data.getTCards().stream()
					.filter(c -> c.getResourceIdentifier().equals("MR-K9-" + (dogIndex + 1))).findFirst().orElseThrow();
			task.getResourcesAssigned().add(taskResource(dog, "Live Find K9"));
			task.setCanineSearchType("Area / live find");
			task.setCanineDayNight("Day");
			task.setCanineCloudCover("Partly cloudy (fictional forecast)");
			task.setCanineWindSpeed("Light and variable; reassess on site");
		}
		data.getSarTaskAssignments().add(task);
	}

	/**
	 * Converts a checked-in card into a linked SAR task resource.
	 *
	 * @param card source card.
	 * @param function task function.
	 * @return linked task resource.
	 */
	private static SarTaskResource taskResource(TCard card, String function) {
		SarTaskResource resource = new SarTaskResource();
		resource.setResourceId(card.getResourceId());
		resource.setCardType(card.getCardType());
		resource.setName(card.getCardType() == TCardType.PERSONNEL ? card.getPersonName() : card.getResourceIdentifier());
		resource.setHomeAgency(card.getHomeAgency());
		resource.setFunction(function);
		return resource;
	}

	/**
	 * Creates named IC phone and Operations radio contacts without linking the phone to a channel.
	 *
	 * @param data incident containing the IC personnel card.
	 * @return IC phone followed by the linked Operations radio contact.
	 */
	private static List<CommunicationEntry> contacts(AppData data) {
		CommunicationEntry ic = new CommunicationEntry();
		ic.setName(IC);
		ic.setFunction("IC");
		ic.setPrimaryContact(data.getTCards().stream().filter(c -> IC.equals(c.getPersonName()))
				.findFirst().orElseThrow().getPhoneNumber());
		CommunicationEntry ops = new CommunicationEntry();
		ops.setName(OPS);
		ops.setFunction("Ops");
		ops.setPrimaryContact(CHANNEL + " / 155.160 MHz");
		ops.setRadioChannelId(id("channel"));
		return List.of(ic, ops);
	}

	/**
	 * Adds mutually consistent tick, fall and heat mitigations to ICS 215A and ICS 208.
	 *
	 * @param data incident document.
	 */
	private static void addSafety(AppData data) {
		List<String> hazards = List.of("Ticks / tick-borne illness", "Falls / uneven terrain / creek edges",
				"Summer heat / dehydration / canine heat stress");
		List<String> mitigations = List.of(
				"Use repellent per label, long trousers and buddy tick checks at 1000 and ; "
						+ "promptly remove attached ticks with proper tool and report exposures to MEDL.",
				"Maintain buddy contact, deliberate footing and suitable boots; avoid unstable banks and water entry. "
						+ "Stop and report unsafe crossings; use an alternate route authorized by Ops.",
				"Water resupply at " + BASE + " by 0900; hourly hydration/electrolyte checks for all field teams. "
						+ "Mandatory heat rehab with shaded rest and heat-illness checks from 1300 at Base, plus rest as needed; "
						+ "Ops staggers search-team returns to Base. Containment posts remain staffed: "
						+ "observation buddies alternate 1300-1320/1320-1340 in adjacent shade, retaining buddy contact "
						+ "and uninterrupted observation; repeat at least hourly. Ops must arrange qualified relief "
						+ "before either post pair travels to Base; never send both observers away without relief. "
						+ "Canine 20-minute work/cooling cycles; handlers monitor dogs continuously. "
						+ "Stop work for heat illness signs; notify MEDL, cool safely and request assistance.");
		for (int i = 0; i < hazards.size(); i++) {
			SafetyAnalysisEntry entry = new SafetyAnalysisEntry();
			entry.setEntryId(id("hazard-" + i));
			entry.setIncidentArea("All Maple Ridge field assignments");
			entry.setHazardsRisks(hazards.get(i));
			entry.setMitigations(mitigations.get(i));
			entry.setEquipmentPpe(i == 0 ? "Repellent, tick-removal kit" : i == 1 ? "Boots, map, radio" : "Water, shade, canine cooling supplies");
			data.getForm215a().getEntries().add(entry);
			SafetyMessageBlock block = new SafetyMessageBlock();
			block.setHazard(hazards.get(i));
			block.setIncidentArea(entry.getIncidentArea());
			block.setPrecautions(mitigations.get(i));
			block.setSourceEntryIds(List.of(entry.getEntryId()));
			data.getForm208().getHazardBlocks().add(block);
		}
		data.getForm215a().setIncidentNumber("EXERCISE-MR-2026-01");
		data.getForm215a().setSafetyOfficerName(SAFETY);
		data.getForm215a().setOperationsSectionChiefName(OPS);
		data.getForm215a().setPreparedDateTime(time(6, 40));
		data.getForm215a().setReviewedDateTime(time(6, 50));
		data.getForm215a().setIncludeInIap(true);
		data.getForm208().setIncludeInIap(true);
		data.getForm208().setGeneralRequirements("FICTIONAL EXERCISE ONLY. All personnel briefed at 0700. "
				+ "Stop work for unsafe conditions; immediately notify Ops and Safety. "
				+ "30-minute radio/accountability checks. MEDL coordinates from " + ICP
				+ "; human aid supplies and heat rehab at " + BASE + ". "
				+ "The aid-supply cache is unstaffed; no dedicated medical post. Emergency traffic takes priority.");
		data.getForm208().setPreparedByName(SAFETY);
		data.getForm208().setPreparedByPositionTitle("Safety Officer");
		data.getForm208().setPreparedDateTime(time(6, 45));
	}

	/**
	 * Adds fictional ground-only human and canine medical arrangements without real contacts.
	 *
	 * @param data incident document.
	 */
	private static void addMedical(AppData data) {
		MedicalPlan plan = data.getMedicalPlan();
		plan.setActive(true);
		plan.setSimpleMode(false);
		plan.setDesignatedMedicalUnitRole("Medical Unit Leader");
		plan.setDesignatedMedicalUnitName(MEDL);
		plan.setDesignatedMedicalUnitContact(CHANNEL + "; 202-555-0107 (fictional)");
		MedicalPlan.AidStation aid = new MedicalPlan.AidStation();
		aid.setName("Exercise Base human first-aid supplies/cache (fictional, unstaffed)");
		aid.setLocation(BASE);
		aid.setContactNumbers("202-555-0107 (fictional)");
		aid.setFrequency(CHANNEL + " / 155.160 simplex");
		aid.setParamedicsOnSite(false);
		plan.getAidStations().add(aid);
		MedicalPlan.TransportService ambulance = new MedicalPlan.TransportService();
		ambulance.setService("Fictional Maple Ridge Ambulance");
		ambulance.setLocation("Exercise station, 2 Illustration Lane (fictional)");
		ambulance.setContactNumbers("202-555-0110 (fictional, non-operational)");
		ambulance.setFrequency("Request through MEDL/Ops on " + CHANNEL);
		ambulance.setAirOrGround("Ground");
		ambulance.setLevelOfService("BLS - simulated requested service; not a confirmed operational resource");
		plan.getTransportServices().add(ambulance);
		MedicalPlan.Hospital hospital = new MedicalPlan.Hospital();
		hospital.setName("Fictional Maple Ridge Community Hospital");
		hospital.setAddress("100 Illustration Avenue, fictional Maple Ridge, MA");
		hospital.setContactNumbers("202-555-0111 (fictional, non-operational)");
		hospital.setGroundTravelTime("20 minutes - illustrative assumption; verify route and receiving capability");
		hospital.setTraumaCenter(false);
		hospital.setBurnCenter(false);
		hospital.setHelipad(false);
		plan.getHospitals().add(hospital);
		MedicalPlan.Hospital trauma = new MedicalPlan.Hospital();
		trauma.setName("Fictional Pine County Regional Trauma Center");
		trauma.setAddress("200 Example Boulevard, fictional Pine County, MA");
		trauma.setContactNumbers("202-555-0112 (fictional, non-operational)");
		trauma.setGroundTravelTime("45 minutes - hypothetical exercise assumption; verify route and receiving capability");
		trauma.setTraumaCenter(true);
		trauma.setBurnCenter(false);
		trauma.setHelipad(false);
		plan.getHospitals().add(trauma);
		plan.setEmergencyProcedures("FICTIONAL EXERCISE ONLY; do not call example numbers. "
				+ "For a real emergency use the actual local emergency number. "
				+ "Exercise: announce emergency traffic on MR SAR 1, give task/location, injury and access; "
				+ "MEDL coordinates from ICP; the human aid station at " + BASE
				+ " is an unstaffed supplies/cache, not an additional staffed medical team. "
				+ "Ops alerts MEDL, pauses nearby work, "
				+ "requests the simulated BLS service and arranges ground extraction and ambulance rendezvous at staging. "
				+ "No aircraft assigned. Do not move a patient unless needed for immediate safety.");
		plan.setAviationAssetsUsed(false);
		plan.setPreparedByMedicalUnitLeader(MEDL);
		plan.setPreparedAt(time(6, 40));
		plan.setReviewedBySafetyOfficer(SAFETY);
		plan.setReviewStatus("Reviewed - fictional exercise only");
		addCanineMedical(data);
	}

	/**
	 * Links the existing K2 handler as a fictional DVM and selects hypothetical veterinary facilities.
	 *
	 * @param data incident containing the existing personnel and canine resources.
	 */
	private static void addCanineMedical(AppData data) {
		var plan = data.getCanineMedicalPlan();
		plan.setActive(true);
		plan.setOnsiteVeterinarian(VETERINARIAN);
		plan.setVeterinarianDvm(VETERINARIAN + ", DVM (fictional exercise credential)");
		plan.setVeterinaryPersonnelResourceIds(List.of(id("MR-P25")));
		plan.setVeterinaryCapabilityNotes("FICTIONAL EXERCISE ONLY. " + VETERINARIAN
				+ " is the existing MR-P25 handler on K2, also an onsite DVM; not an extra person "
				+ "or dedicated veterinary post. Ops must release or retask K2 before veterinary assistance. "
				+ "No canine aid station or canine transport service is assigned. "
				+ "Facility availability and 25/45-mile distances are hypothetical, not verified services or measured routes.");
		plan.setEmergencyProcedures("FICTIONAL EXERCISE ONLY; do not call example numbers. "
				+ "Provide canine first aid only within training and contact Ops on " + CHANNEL
				+ ". Ops contacts " + VETERINARIAN + ", the onsite DVM tasked on K2, "
				+ "releases or retasks the handler as necessary, then arranges veterinary care and transport. "
				+ "No dedicated canine transport service or aid station; no aircraft assigned.");
		for (int i = 0; i < 2; i++) {
			int miles = i == 0 ? 25 : 45;
			VetFacility facility = new VetFacility();
			facility.setId(id("vet-facility-" + (i + 1)));
			facility.setName(i == 0 ? "Fictional Cedar Vale Emergency Veterinary Hospital"
					: "Fictional Oak Hollow Veterinary Emergency Center");
			facility.setTown(i == 0 ? "Fictional Cedar Vale" : "Fictional Oak Hollow");
			facility.setState("MA");
			facility.setAddress((i == 0 ? "25" : "45") + " Imaginary Veterinary Lane (fictional)");
			facility.setContactNumbers(List.of("202-555-01" + (i == 0 ? "21" : "22") + " (fictional, non-operational)"));
			facility.setAvailable24Hours(true);
			facility.setAvailabilityNotes("Fictional " + miles + "-mile estimate from staging; not verified. "
					+ "Hypothetical 24-hour exercise facility; availability unverified, not a real service.");
			facility.setCapabilities("Fictional emergency veterinary receiving facility; verify care and transport with Ops.");
			facility.setDistance(miles * 1609.344, Double.NaN, "Hypothetical exercise distance",
					"Invented " + miles + "-mile assumption from staging; not a real route measurement.",
					null, data.getStagingLocation().getGeneration());
			facility.setSourceName("Maple Ridge fictional exercise");
			plan.getSelectedFacilities().add(facility);
		}
		plan.setAviationAssetsUsed(false);
		plan.setPreparedByMedicalUnitLeader(MEDL);
		plan.setPreparedAt(time(6, 40));
		plan.setReviewedBySafetyOfficer(SAFETY);
		plan.setReviewStatus("Reviewed - fictional exercise only");
	}

	/**
	 * Adds real ICS 233-CG action fields and an ICS 214 log appropriate to the 0815 snapshot.
	 *
	 * @param data incident document.
	 */
	private static void addActionsAndLog(AppData data) {
		data.setOpenActionTrackerEnabled(true);
		data.setActivityEventTypes(new ArrayList<>(ActivityEventType.defaultTypes()));
		List<String> items = List.of("First tick check: ALL teams complete and report buddy tick checks by 1000; "
				+ "Safety verifies completion; MEDL tracks exposures.",
				"Hourly hydration/electrolyte checks for all field teams; replenish drinking water at " + BASE + " by 0900 "
						+ "and arrange field/canine water delivery requests through simulated support service. Planning and Safety verify every team.",
				"MANDATORY heat rehab with shaded rest and heat-illness checks from 1300 at " + BASE
						+ "; Ops staggers search-team returns, coordinated by Safety/MEDL at ICP. "
						+ "Observation buddies alternate rest 1300-1320/1320-1340 in adjacent shade "
						+ "with continuous observation and buddy contact; Ops arranges qualified relief before "
						+ "post pairs travel to Base. Never leave a containment post unstaffed for rehab.",
				"End-shift : Safety begins tick/hydration checks at 1830 and verifies completion before release; "
						+ "account for all 42 people and four canines, complete task debriefs, equipment return "
						+ "and safe-travel checks. Maintain paired containment posts through 1900; "
						+ "verify continuing containment/relief needs with Ops before releasing anyone.");
		List<String> pocs = List.of(SAFETY + " (Safety)", PLANNING + " (Planning) / " + SAFETY + " (Safety)",
				SAFETY + " (Safety) / " + MEDL + " (MEDL)", SAFETY + " (Safety)");
		List<LocalDateTime> due = List.of(time(10, 0), time(9, 0), time(13, 0), time(18, 30));
		for (int i = 0; i < items.size(); i++) {
			OpenActionItem action = new OpenActionItem();
			action.setNumber(i + 1);
			action.setItem(items.get(i));
			action.setPoc(pocs.get(i));
			action.setBriefedToPoc(true);
			action.setStartDate(time(7, 0));
			action.setTargetDate(due.get(i));
			action.setStatus("Open");
			data.getOpenActionItems().add(action);
		}
		Ics214Form log = new Ics214Form();
		log.setName(OPS);
		log.setIcsPosition("Operations Section Chief");
		log.setHomeAgency(AGENCY);
		log.setPreparedByName(OPS);
		log.setPreparedByPositionTitle("Operations Section Chief");
		log.setPreparedDateTime(time(8, 15));
		data.getTCards().stream().filter(card -> "ICP".equals(card.getStatus()))
				.forEach(card -> log.getResourcesAssigned().add(taskResource(card, "ICP overhead")));
		for (SarTaskAssignment task : data.getSarTaskAssignments()) {
			if (!"planned".equals(task.getTaskLifecycleStatus())) {
				for (SarTaskResource assigned : task.getResourcesAssigned()) {
					SarTaskResource resource = new SarTaskResource();
					resource.setResourceId(assigned.getResourceId());
					resource.setCardType(assigned.getCardType());
					resource.setName(assigned.getName());
					resource.setHomeAgency(assigned.getHomeAgency());
					resource.setFunction("Team " + task.getAssignmentTeamNumber() + " — " + assigned.getFunction());
					log.getResourcesAssigned().add(resource);
				}
			}
		}
		addActivity(log, "command", ActivityEventType.ID_FREE_TEXT, time(6, 30), null,
				"0630 command established at " + ICP + "; all 42 people checked in: 36 field including four handlers "
						+ "and six IMT (IC, Ops, Planning, Safety, COML, MEDL); four dogs checked in as equipment.");
		addActivity(log, "briefing", ActivityEventType.ID_FREE_TEXT, time(7, 0), null,
				"0700 exercise briefing: objectives, dementia-aware approach, tick/fall/heat controls; "
						+ "human aid cache and heat rehab at " + BASE + ". All field teams report directly to Ops.");
		for (SarTaskAssignment task : data.getSarTaskAssignments()) {
			if (!"assigned - on task".equals(task.getTaskLifecycleStatus())) {
				continue;
			}
			LocalDateTime departure = task.getAssignmentStart().minusMinutes(5);
			String departureTime = task.getResourceType().equals("Canine") ? "0725" : "0710";
			String onTaskTime = task.getResourceType().equals("Canine") ? "0730" : "0715";
			addActivity(log, task.getAssignmentTeamNumber() + "-depart", ActivityEventType.ID_RESOURCE_DEPARTED_STAGING,
					departure, task, departureTime + " " + task.getAssignmentTeamNumber() + " departed " + STAGING
							+ " with all assigned personnel" + (task.getResourceType().equals("Canine") ? " and dog." : "."));
			addActivity(log, task.getAssignmentTeamNumber() + "-on-task", ActivityEventType.ID_RESOURCE_ON_TASK,
					task.getAssignmentStart(), task, onTaskTime + " " + task.getAssignmentTeamNumber()
							+ " on task; all assigned resources accounted for. "
							+ (task.getTaskType().equals("Point") ? "Containment observation established; maintain through 1900."
									: "Search commenced; report actual coverage and gaps to Ops."));
		}
		SarTaskAssignment tracker = data.getSarTaskAssignments().stream()
				.filter(t -> "T1".equals(t.getAssignmentTeamNumber())).findFirst().orElseThrow();
		addActivity(log, "T1-depart", ActivityEventType.ID_RESOURCE_DEPARTED_STAGING, time(7, 0), tracker,
				"0700 T1 visual trackers MR-P13/MR-P14/MR-P15 departed staging for the residence/IPP.");
		addActivity(log, "T1-on-task", ActivityEventType.ID_RESOURCE_ON_TASK, time(7, 5), tracker,
				"0705 T1 on task around residence/IPP; all three trackers accounted for.");
		addActivity(log, "T1-clue", ActivityEventType.ID_CLUE_DETECTED, time(7, 30), tracker,
				"0730 T1 reported a definitely human sign line north, with no clear prints or sole pattern. "
						+ "Potential clue flagged on Ops instruction; subject association unverified, follow-up pending.");
		addActivity(log, "T1-complete", ActivityEventType.ID_TASK_COMPLETED, time(7, 50), tracker,
				"0750 T1 task completed; all three trackers returning to staging for debrief.");
		addActivity(log, "T1-return", ActivityEventType.ID_RESOURCE_RETURNED_STAGING, time(8, 0), tracker,
				"0800 T1 MR-P13/MR-P14/MR-P15 returned to staging; all three accounted for, awaiting debrief.");
		addActivity(log, "T1-debrief", ActivityEventType.ID_FREE_TEXT, time(8, 10), tracker,
				"0810 T1 debrief completed with Ops " + OPS + ". " + tracker.getDebriefNotes());
		addActivity(log, "containment", ActivityEventType.ID_FREE_TEXT, time(8, 0), null,
				"0800 R1/R2 confirm continuing observation at north gate and east footbridge; both established at 0715. "
						+ "Seven teams remain on task; T1 returned. Planned tasks have not departed staging.");
		addActivity(log, "snapshot", ActivityEventType.ID_FREE_TEXT, time(8, 15), null,
				"0815 snapshot: T1 debriefed and available at staging for K3 at 1030; clue follow-up not yet undertaken. "
						+ "K3/K4 remain planned pending Ops span/heat reassessment. MR-P16 and released K3 support "
						+ "MR-P30/MR-P31/MR-P32 remain reserve for G2 grid at 1030. "
						+ "G1 attractors 1030-1200 and grid 1230-1700 remain planned. "
						+ "Planning acknowledges 0900 water resupply at " + BASE + ".");
		log.getActivityLog().sort(Comparator.comparing(ActivityLogEntry::getTimestamp));
		data.getActivityLogs().add(log);
	}

	/**
	 * Adds a deterministically identified activity with supported event type and optional task context.
	 *
	 * @param log destination activity log.
	 * @param key stable entry identity.
	 * @param eventType supported activity event type.
	 * @param timestamp actual recorded event time.
	 * @param task linked task, or null for a command-level note.
	 * @param note human-readable event narrative.
	 */
	private static void addActivity(Ics214Form log, String key, String eventType, LocalDateTime timestamp,
			SarTaskAssignment task, String note) {
		ActivityLogEntry entry = new ActivityLogEntry();
		entry.setEntryId(id("activity-" + key));
		entry.setTimestamp(timestamp);
		entry.setEventTypeId(eventType);
		entry.setResourceIdentifier(task == null ? "Ops" : task.getResourceIdentifier());
		entry.setAssignmentId(task == null ? "" : task.getAssignmentId());
		entry.setNotableActivity(note);
		log.getActivityLog().add(entry);
	}

	/**
	 * Converts a displayed summer exercise time into the application's UTC storage convention.
	 *
	 * @param hour local hour.
	 * @param minute local minute.
	 * @return UTC date/time representing the requested America/New_York wall time.
	 */
	private static LocalDateTime time(int hour, int minute) {
		return LocalDateTime.of(2026, 7, 18, hour, minute).atZone(ZoneId.of("America/New_York"))
				.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
	}

	/**
	 * Derives a deterministic UUID for reproducible example exports.
	 *
	 * @param label unique fixture-local identity label.
	 * @return stable UUID string.
	 */
	private static String id(String label) {
		return UUID.nameUUIDFromBytes(("maple-ridge-example:" + label).getBytes(StandardCharsets.UTF_8)).toString();
	}

	/**
	 * Removes test artifacts confined to their project-local work directory.
	 *
	 * @param directory work directory.
	 * @throws java.io.IOException if an artifact cannot be removed.
	 */
	private static void deleteTree(Path directory) throws java.io.IOException {
		try (var paths = Files.walk(directory)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		}
	}
}
