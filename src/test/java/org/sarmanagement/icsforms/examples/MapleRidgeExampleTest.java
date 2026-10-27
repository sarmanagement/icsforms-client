/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
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
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics203PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics204PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics205PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics205aPdfRenderer;
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
	private static final String CHANNEL = "MR SAR 1";
	private static final String AGENCY = "Fictional Maple Ridge Volunteer SAR";
	private static final String IC = "Avery Wren";
	private static final String OPS = "Morgan Vale";
	private static final String PLANNING = "Rowan Ellis";
	private static final String SHERIFF_AGENCY = "Fictional Pine County Sheriff's Office";
	private static final String SAFETY = "Jordan Reed";
	private static final String COML = "Taylor Brook";
	private static final String MEDL = "Quinn Alder";
	private static final List<String> FORMS = List.of("ICS 202", "ICS 203", "ICS 204", "ICS 205", "ICS 205A",
			"ICS 206", "ICS 207", "ICS 208", "ICS 214", "ICS 215A", "ICS 233-CG", "SAR Task Assignment");
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
			assertEquals(List.of(ICP, STAGING, "Base (supply and rehab) colocated with " + ICP, IPP),
					data.getIncidentLocations().stream().map(IncidentLocation::getName).toList());
			assertEquals(List.of("50 Civic Road, fictional Maple Ridge, MA", "20 Park Road, fictional Maple Ridge, MA",
					"50 Civic Road, fictional Maple Ridge, MA", "1 Trail Lane, fictional Maple Ridge, MA"),
					data.getIncidentLocations().stream().map(IncidentLocation::getAddress).toList());
			List<TCard> staff = data.getTCards().stream()
					.filter(c -> c.getResourceIdentifier().startsWith("MR-IMT-")).toList();
			assertEquals(6, staff.size());
			assertTrue(staff.stream().allMatch(c -> ICP.equals(c.getLocation())));
			assertTrue(staff.stream().allMatch(c -> "ICP".equals(c.getStatus())));
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
					"0600", "July 18", "0800", "27-31 C", "humid", "light wind", IPP, STAGING, ICP)) {
				assertTrue(data.getForm202().getSituationalAwareness().contains(detail), detail);
			}
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
			assertEquals(time(6, 35), trackers.getAssignmentStart());
			assertEquals(time(6, 55), trackers.getAssignmentEnd());
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
			assertEquals(time(7, 0), trackers.getDebriefPreparedDateTime());
			assertEquals(1, data.getClueLogEntries().size());
			ClueLogEntry clue = data.getClueLogEntries().get(0);
			assertEquals(trackers.getAssignmentId(), clue.getAssignmentId());
			assertEquals("T1", clue.getDetectingTask());
			assertEquals(trackers.getLeader(), clue.getDetectedBy());
			assertEquals(time(6, 45), clue.getDateTimeCollected());
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
			assertEquals(5, data.getActivityLogs().get(0).getActivityLog().size());
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
			assertEquals(STAGING, data.getMedicalPlan().getAidStations().get(0).getLocation());
			assertEquals(1, data.getMedicalPlan().getTransportServices().size());
			assertEquals(1, data.getMedicalPlan().getHospitals().size());
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
					new Ics206PdfRenderer(), new Ics207PdfRenderer(), new Ics208PdfRenderer(),
					new Ics214PdfRenderer(), new Ics215aPdfRenderer(), new Ics233CgPdfRenderer(),
					new SarTaskAssignmentPdfRenderer());
			for (String form : FORMS) {
				Path output = service.exportSelected(form, data, work.resolve("pdf"));
				try (var pdf = Loader.loadPDF(output.toFile())) {
					assertTrue(pdf.getNumberOfPages() > 0, form);
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
						assertTrue(text.contains("20 Park Road"), text);
						assertTrue(text.contains("ICP"), text);
					}
					if ("ICS 233-CG".equals(form)) {
						assertTrue(text.contains("tick"), form);
						assertTrue(text.contains("hydration"), form);
					}
				}
			}
			Path bundle = service.exportIapBundle(data, work.resolve("iap"), FORMS);
			try (var pdf = Loader.loadPDF(bundle.toFile())) {
				assertTrue(pdf.getNumberOfPages() >= FORMS.size());
				assertTrue(new PDFTextStripper().getText(pdf).contains("155.160"));
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
	 * Builds a fully fictional active summer operational period using only model APIs.
	 *
	 * @return portable example with deterministic identities and timestamps.
	 */
	private static AppData buildExample() {
		AppData data = new AppData();
		data.setIncidentId(id("incident"));
		data.setOriginNodeId(id("origin"));
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
		addLocation(data, "base", "Base", "Supply / recovery base colocated with " + ICP,
				"50 Civic Road, fictional Maple Ridge, MA");
		addLocation(data, "ipp", "Other", IPP, "1 Trail Lane, fictional Maple Ridge, MA");
		data.getForm201().setIncidentName(INCIDENT);
		data.getForm201().setIncidentNumber("EXERCISE-MR-2026-01");
		data.getForm201().setSituationSummary("FICTIONAL ILLUSTRATION ONLY; not an operational plan. "
				+ "Ellis Linden, fictional 74-year-old adult with dementia, wandered from a residence "
				+ "at " + IPP + "; last seen there at 0600 July 18. Staging: " + STAGING + ". Command staff: " + ICP + ". "
				+ "Wearing a blue shirt and tan trousers; may not respond to calls. "
				+ "Returned three-person visual tracker task T1 (0635-0655) reported a definitely human sign line north "
				+ "from IPP, with no clear prints or sole pattern; potential clue flagged on Ops instruction, "
				+ "subject association unverified and follow-up not yet undertaken at 0800. "
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
		data.getForm202().setSituationalAwareness("0800 July 18, 2026 exercise snapshot. Fictional Ellis Linden, "
				+ "74-year-old adult with dementia, last seen 0600 July 18 at " + IPP + ". "
				+ "Wearing a blue shirt and tan trousers; may hide or not answer. "
				+ "T1's three visual trackers returned after 0635-0655 work around IPP: a definitely human sign line "
				+ "led north, with no clear prints or sole pattern. This potential clue was flagged on Ops instruction; "
				+ "subject association remains unverified and follow-up has not yet been undertaken. "
				+ "Forecast 27-31 C, humid, light wind; shaded woodland, uneven trails and creek edges. "
				+ "Staging: " + STAGING + ". Separate command post: " + ICP + ". "
				+ "Supply / recovery base colocated at ICP for water resupply and recovery coordination. "
				+ "All teams report directly to Ops; no branches, divisions or groups. "
				+ "36 human field searchers INCLUDE four handlers; four dogs are equipment, not people. "
				+ "Six IMT at " + ICP + "; IC " + IC + " is Sheriff of " + SHERIFF_AGENCY + ". No aircraft. "
				+ "Seven teams active at 0800; returned trackers are available at staging for K3 at 1030. "
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
				card.setNotes("Fictional visual tracker returned from T1 (0635-0655), debriefed at 0700; "
						+ "G2 support available at staging for K3 handler Devon Heath at 1030; not on hasty route or G2 grid.");
			} else if (i == 15 || i >= 29 && i <= 31) {
				card.setNotes("Fictional staging reserve until G2 eight-person grid at 1030"
						+ (i == 15 ? "; G2 eighth member." : "; released from K3 support, replaced by MR-P13/MR-P14/MR-P15."));
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
	 * Adds primary ICS 204 rows, linked search tasks and the returned pre-period tracker task.
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
				+ "MR-P13/MR-P14/MR-P15 completed visual tracker task T1 around IPP 0635-0655, debriefed at 0700, "
				+ "and are available at staging for K3 at 1030 with handler MR-P29. "
				+ "Released K3 support MR-P30/MR-P31/MR-P32 and G2 member MR-P16 remain staging reserve until "
				+ "joining MR-P09 through MR-P12 for eight-person G2 grid at 1030. "
				+ "G1 reassembles eight for attractors at 1030 and grid at 1230. "
				+ "IPP: " + IPP + ". ICP: " + ICP + ". Staging is separate from both.");
		form.setSpecialInstructions("FICTIONAL EXERCISE ONLY. Calm approach to vulnerable adult; do not chase. "
				+ "Preserve clues, report location immediately, avoid contaminating scent articles. "
				+ "T1 reported a definitely human sign line north of IPP, with no clear prints or sole pattern; "
				+ "Ops instructed trackers to flag the potential clue and return. Subject association unverified; "
				+ "follow-up not yet undertaken at 0800. Preserve the flagged location pending Ops direction. "
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
	 * Records completed pre-period visual tracking and its still-pending potential clue.
	 *
	 * @param data incident document containing the later K3 support assignment.
	 */
	private static void addReturnedTrackerTask(AppData data) {
		addTask(data, "T1", "Visual trackers", "Ground", "Area", List.of(12, 13, 14),
				"Three-person visual tracker examination around residence/IPP to establish direction of travel. "
						+ "Protect sign and scent, avoid trampling, report potential clues to Ops; "
						+ "return to staging for debrief before further tasking. Completed pre-period task carried into this IAP.",
				1, time(6, 35), time(6, 55), "returned", -1);
		SarTaskAssignment task = data.getSarTaskAssignments().get(data.getSarTaskAssignments().size() - 1);
		task.setPreparedDateTime(time(6, 32));
		task.setDebriefingSupervisor(OPS);
		task.setDebriefingSupervisorResourceId(id("MR-IMT-Ops"));
		task.setDebriefNotes("Actual task time 0635-0655. At 0645, a definitely human sign line led north "
				+ "from the residence/IPP; no clear prints or sole pattern and no subject identification. "
				+ "Potential clue flagged on Ops instruction; no follow-up yet. All three returned to staging; "
				+ "debrief completed 0700 and available for later K3.");
		task.setReportedPod("Not estimated; directional sign examination only, not a systematic area search.");
		task.setAreasNotCovered("Only immediate IPP ground examined; northbound sign beyond the flagged location "
				+ "and the wider woodland were not followed or searched by T1.");
		task.setHazardsObserved("Uneven ground, ticks and creek-edge footing; buddy contact maintained, no injuries reported.");
		task.setDebriefPreparedByName(task.getLeader());
		task.setDebriefPreparedByPositionTitle("Visual tracker team leader");
		task.setDebriefPreparedDateTime(time(7, 0));
		task.setDebriefingCompleted(true);
		ClueLogEntry clue = new ClueLogEntry();
		clue.setEntryId(id("clue-T1-north-sign"));
		clue.setAssignmentId(task.getAssignmentId());
		clue.setDetectingTask("T1");
		clue.setDetectedBy(task.getLeader());
		clue.setDateTimeCollected(time(6, 45));
		clue.setLocation("North edge of " + IPP + "; flag T1-1 on exercise sketch MR-EX-01");
		clue.setDescription("A definitely human sign line leads north from IPP; no clear prints or sole pattern. "
				+ "Potential clue only; association with Ellis Linden is unverified.");
		clue.setImmediateAction("Potential clue flagged in place on Ops instruction; location reported to "
				+ OPS + "; no physical evidence collected.");
		clue.setFollowUp("Pending Ops assessment and direction; follow-up not yet undertaken at the 0800 snapshot.");
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
		task.setTransportationInstructions("Ground/foot only; sign out and back in at staging, not IPP. No aircraft.");
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
				"ICP water resupply by 0900; hourly hydration/electrolyte checks for all field teams. "
						+ "Mandatory shaded rest and heat-illness checks from 1300, plus rest as needed; "
						+ "observation buddies alternate 1300-1320/1320-1340 in adjacent shade, retaining buddy contact "
						+ "and uninterrupted observation; repeat at least hourly. "
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
				+ "; aid supplies at staging. Emergency traffic takes priority.");
		data.getForm208().setPreparedByName(SAFETY);
		data.getForm208().setPreparedByPositionTitle("Safety Officer");
		data.getForm208().setPreparedDateTime(time(6, 45));
	}

	/**
	 * Adds fictional ground-only aid, ambulance and hospital arrangements without real contacts.
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
		aid.setName("Exercise pavilion first-aid post (fictional)");
		aid.setLocation(STAGING);
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
		plan.setEmergencyProcedures("FICTIONAL EXERCISE ONLY; do not call example numbers. "
				+ "For a real emergency use the actual local emergency number. "
				+ "Exercise: announce emergency traffic on MR SAR 1, give task/location, injury and access; "
				+ "MEDL coordinates from ICP; staging aid post is a supplies/cache and rendezvous location, "
				+ "not an additional staffed team. Ops alerts MEDL, pauses nearby work, "
				+ "requests the simulated BLS service and arranges ground extraction and ambulance rendezvous at staging. "
				+ "No aircraft assigned. Do not move a patient unless needed for immediate safety.");
		plan.setAviationAssetsUsed(false);
		plan.setPreparedByMedicalUnitLeader(MEDL);
		plan.setPreparedAt(time(6, 40));
		plan.setReviewedBySafetyOfficer(SAFETY);
		plan.setReviewStatus("Reviewed - fictional exercise only");
	}

	/**
	 * Adds real ICS 233-CG action fields and an ICS 214 log appropriate to the 0800 snapshot.
	 *
	 * @param data incident document.
	 */
	private static void addActionsAndLog(AppData data) {
		data.setOpenActionTrackerEnabled(true);
		List<String> items = List.of("First tick check: ALL teams complete and report buddy tick checks by 1000; "
				+ "Safety verifies completion; MEDL tracks exposures.",
				"Hourly hydration/electrolyte checks for all field teams; replenish ICP drinking water by 0900 "
						+ "and arrange field/canine water delivery requests through simulated support service. Planning and Safety verify every team.",
				"MANDATORY shaded rest break and heat-illness check from 1300 for all field teams, "
						+ "supervised remotely by Safety/MEDL at ICP; observation buddies alternate rest "
						+ "1300-1320/1320-1340 in adjacent shade with continuous observation and buddy contact.",
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
		log.setLinkedIcs204FormId(data.getForm204().getFormId());
		log.setName("Maple Ridge Operations - fictional exercise");
		log.setIcsPosition("Operations Section Chief");
		log.setHomeAgency(AGENCY);
		log.setPreparedByName(OPS);
		log.setPreparedByPositionTitle("Operations Section Chief");
		log.setPreparedDateTime(time(8, 0));
		List<String> notes = List.of("0700 T1 debrief: three visual trackers MR-P13/MR-P14/MR-P15 returned after "
				+ "0635-0655 examination around IPP. At 0645 reported a definitely human sign line north, "
				+ "with no clear prints or sole pattern. Potential clue flagged on Ops instruction; "
				+ "subject association unverified, follow-up not yet undertaken. Trackers available at staging for K3 at 1030.",
				"0700 exercise briefing: objectives, dementia-aware approach, tick/fall/heat controls; "
				+ "42 people checked in: 36 field including four handlers, six IMT at " + ICP + "; four canine cards.",
				"0715 four-person G1-A on S1 (A), four-person G1-B on S2 (B), four-person G2-A on S3 (C), "
						+ "all routes concurrent through 1000; two two-person observation teams on R1/R2. "
						+ "MR-P13/MR-P14/MR-P15 available at staging for K3; MR-P16 and released K3 support "
						+ "MR-P30/MR-P31/MR-P32 remain staging reserve until G2 grid. Residence IPP protected: " + IPP + ".",
				"0730 K1/K2 deployed; K3/K4 available at " + STAGING
						+ " pending 1030 launch and Ops span/heat reassessment. Seven teams active. "
						+ "K3 will pair handler MR-P29 with three available G2 support, not its released support.",
				"0800 R1/R2 report established at north gate and east footbridge. T1 potential clue remains flagged, "
						+ "subject association unverified and follow-up pending; seven teams active, T1 returned. "
						+ "Eight-person G1 reassembles for attractor search 1030-1200 then grid 1230-1700; "
						+ "eight-person G2 grid 1030-1700 planned with G2-A, MR-P16 and three released K3 support. "
						+ "Planning acknowledges 0900 ICP water reminder.");
		for (int i = 0; i < notes.size(); i++) {
			ActivityLogEntry entry = new ActivityLogEntry();
			entry.setEntryId(id("activity-" + i));
			entry.setTimestamp(List.of(time(7, 0), time(7, 0), time(7, 15), time(7, 30), time(8, 0)).get(i));
			entry.setResourceIdentifier(i == 0 ? "Visual trackers" : i == 4 ? "Observation 1" : "Ops");
			entry.setAssignmentId(i == 0 ? id("assignment-T1") : i == 4 ? id("assignment-R1") : "");
			entry.setNotableActivity(notes.get(i));
			log.getActivityLog().add(entry);
		}
		data.getActivityLogs().add(log);
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
