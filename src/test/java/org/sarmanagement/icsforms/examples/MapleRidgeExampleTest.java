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
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.model.IapPlanningStage;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.OpenActionItem;
import org.sarmanagement.icsforms.model.OrgChartEntry;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
					"By 1200, check and document all three familiar attractors and associated hazard points within 1 km of IPP."),
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
			List<TCard> staff = data.getTCards().stream()
					.filter(c -> c.getResourceIdentifier().startsWith("MR-IMT-")).toList();
			assertEquals(6, staff.size());
			assertTrue(staff.stream().allMatch(c -> ICP.equals(c.getLocation())));
			Set<String> supportedStaffRefs = Set.of("org:ic:0", "org:operationsChief", "org:planningChief",
					"org:safetyOfficer", "org:commUnitLeader");
			assertTrue(staff.stream().allMatch(c -> c.getSourceRef().isBlank()
					|| supportedStaffRefs.contains(c.getSourceRef())));
			TCard sheriff = staff.stream().filter(c -> IC.equals(c.getPersonName())).findFirst().orElseThrow();
			assertEquals(SHERIFF_AGENCY, sheriff.getHomeAgency());
			assertTrue(sheriff.getNotes().contains("Sheriff"));
			assertTrue(data.getForm202().getCommandEmphasis().contains("Sheriff of " + SHERIFF_AGENCY));
			assertEquals(Set.of(AGENCY, "Fictional Cedar Valley SAR", "Fictional Lake Hollow SAR"),
					data.getTCards().stream().filter(c -> FIELD_NAMES.contains(c.getPersonName()))
							.map(TCard::getHomeAgency).collect(Collectors.toSet()));
			TCard casey = data.getTCards().stream().filter(c -> "Casey Finch".equals(c.getPersonName()))
					.findFirst().orElseThrow();
			assertTrue(casey.getResourceIdentifier().startsWith("MR-P"));
			assertTrue(casey.getSourceRef().startsWith("sar:"));
			assertFalse(Files.readString(FIXTURE).toLowerCase(java.util.Locale.ROOT).contains("cleared"));
			assertTrue(data.getForm202().getSituationalAwareness().contains("coverage 1 is not POD 1"));
			assertTrue(data.getForm202().getSituationalAwareness().contains("always less than 100%"));
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
			assertEquals(8, data.getOrganizationalChart().getAdditionalPositions().size());
			assertTrue(data.getOrganizationalChart().getAdditionalPositions().stream()
					.allMatch(p -> p.getSection() == OrgChartEntry.Section.OPERATIONS));
			assertEquals("", data.getForm204().getBranch());
			assertEquals("", data.getForm204().getDivision());
			assertEquals("", data.getForm204().getGroup());
			assertTrue(data.getAdditionalForms204().isEmpty());
			assertEquals(12, data.getForm204().getResourcesAssigned().size());
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
				assertEquals(id("channel"), task.getCommunications().get(0).getRadioChannelId());
				assertEquals(STAGING, task.getStagingArea());
				assertEquals(STAGING, row.getReportingLocation());
				assertEquals(row.getLeader(), task.getLeader());
				int expectedPeople = task.getResourceType().equals("Canine") ? 4
						: task.getResourceIdentifier().startsWith("Observation") ? 2 : 8;
				assertEquals(expectedPeople, row.getNumberOfPersons());
				if (task.getResourceType().equals("Canine")) {
					SarTaskResource dog = task.getResourcesAssigned().stream()
							.filter(r -> r.getCardType() == TCardType.EQUIPMENT).findFirst().orElseThrow();
					assertEquals(task.getLeader(), data.getTCards().stream()
							.filter(c -> c.getResourceId().equals(dog.getResourceId())).findFirst().orElseThrow().getHandlerName());
					assertEquals(5, task.getResourcesAssigned().size());
				}
			}
			assertEquals(8, data.getSarTaskAssignments().stream().map(SarTaskAssignment::getResourceIdentifier).distinct().count());
			assertEquals(4, data.getSarTaskAssignments().stream().filter(t -> t.getResourceType().equals("Canine")).count());
			assertEquals(2, data.getSarTaskAssignments().stream()
					.filter(t -> t.getResourceIdentifier().startsWith("Observation")).count());
			assertEquals(Set.of("G1", "G2"), data.getSarTaskAssignments().stream()
					.filter(t -> t.getResourceType().equals("Ground") && !t.getResourceIdentifier().startsWith("Observation"))
					.map(SarTaskAssignment::getResourceIdentifier).collect(Collectors.toSet()));
			for (String team : List.of("G1", "G2")) {
				List<Set<String>> memberships = data.getSarTaskAssignments().stream()
						.filter(t -> team.equals(t.getResourceIdentifier()))
						.map(t -> t.getResourcesAssigned().stream().map(SarTaskResource::getResourceId).collect(Collectors.toSet()))
						.toList();
				assertEquals(team.equals("G1") ? 4 : 2, memberships.size());
				assertEquals(8, memberships.get(0).size());
				assertTrue(memberships.stream().allMatch(m -> m.equals(memberships.get(0))));
			}
			assertEquals(6, data.getSarTaskAssignments().stream()
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
			assertEquals(Set.of("assigned - on task", "planned"), data.getSarTaskAssignments().stream()
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
			assertEquals(4, data.getActivityLogs().get(0).getActivityLog().size());
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
		data.getForm201().setIncidentName(INCIDENT);
		data.getForm201().setIncidentNumber("EXERCISE-MR-2026-01");
		data.getForm201().setSituationSummary("FICTIONAL ILLUSTRATION ONLY; not an operational plan. "
				+ "Ellis Linden, fictional 74-year-old adult with dementia, wandered from a residence "
				+ "at " + IPP + "; last seen there at 0600. Staging: " + STAGING + ". Command staff: " + ICP + ". "
				+ "Wearing a blue shirt and tan trousers; may not respond to calls. No verified clues at 0800. "
				+ "Summer forecast 27-31 C, humid; shaded woodland, uneven trails and ticks. No aircraft. "
				+ "Koster hub/spokes/rim: protect the residence/last-known-point hub; stage away from the IPP; "
				+ "search travel spokes promptly; "
				+ "observe likely rim exits, then search bounded areas and familiar attractors. "
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
	 * Adds six ICP personnel and a flat set of eight teams reporting to Operations.
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
		for (int i = 0; i < 8; i++) {
			OrgChartEntry entry = new OrgChartEntry();
			entry.setSection(OrgChartEntry.Section.OPERATIONS);
			entry.setTitle(i < 2 ? "G" + (i + 1) + " ground team leader"
					: i < 4 ? "R" + (i - 1) + " observation team leader" : "K" + (i - 3) + " handler");
			entry.setName(FIELD_NAMES.get(i < 2 ? i * 8 : i < 4 ? 16 + (i - 2) * 2 : 20 + (i - 4) * 4));
			entry.setRadio(CHANNEL);
			org.getAdditionalPositions().add(entry);
		}
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
				"By 1200, check and document all three familiar attractors and associated hazard points within 1 km of IPP.");
		List<IncidentObjective> structured = new ArrayList<>();
		for (int i = 0; i < objectives.size(); i++) {
			IncidentObjective objective = new IncidentObjective(objectives.get(i), i + 1);
			objective.setObjectiveId(id("objective-" + i));
			structured.add(objective);
		}
		data.getForm202().setStructuredObjectives(structured);
		data.getForm202().setCommandEmphasis("FICTIONAL EXERCISE ONLY. Life safety; protect the hub from scent contamination. "
				+ "All eight teams report directly to Ops; no branches, divisions or groups. "
				+ "36 human field searchers INCLUDE four handlers; four dogs are equipment, not people. "
				+ "Six IMT at " + ICP + "; IC " + IC + " is Sheriff of " + SHERIFF_AGENCY + ". No aircraft. "
				+ "Six teams initially active; Ops must reassess span before later launches and stagger/pause work "
				+ "if direct supervision becomes unsafe; eight teams is not assumed manageable.");
		data.getForm202().setSituationalAwareness("0800 exercise snapshot. Subject may hide or not answer. "
				+ "Koster hub/spokes/rim guides prioritization, not a guarantee of containment. "
				+ "Probability of detection is always less than 100%; coverage 1 is not POD 1. "
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
			card.setStatus(i >= 28 ? "Available" : "Assigned");
			card.setLocation(i < 8 ? "Spoke A" : i < 16 ? "Spoke C" : i < 20 ? "Rim R" + (1 + (i - 16) / 2)
					: i < 28 ? "K" + (1 + (i - 20) / 4) : STAGING);
			card.setHomeAgency(i < 8 ? "Fictional Cedar Valley SAR" : i < 16 ? "Fictional Lake Hollow SAR" : AGENCY);
			card.setNotes("Fictional field searcher" + (i >= 20 && (i - 20) % 4 == 0 ? "; canine handler" : "; buddy-system assignment"));
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
	 * Adds primary ICS 204 rows and linked route, area and follow-on point tasks.
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
				+ "IPP: " + IPP + ". ICP: " + ICP + ". Staging is separate from both.");
		form.setSpecialInstructions("FICTIONAL EXERCISE ONLY. Calm approach to vulnerable adult; do not chase. "
				+ "Preserve clues, report location immediately, avoid contaminating scent articles. "
				+ "Check in every 30 minutes. Buddy contact, tick checks, water and shaded rest per ICS 208. "
				+ "Six teams initially active; Ops reassesses span before later launches and staggers/pauses work "
				+ "rather than assuming eight active teams manageable. No simultaneous tasks for any member.");
		form.setCommunications(List.of(contact("Ops / all teams", "Command and tactical")));
		form.setPreparedByName(PLANNING);
		form.setPreparedByPositionTitle("Planning Section Chief");
		form.setPreparedDateTime(time(6, 50));
		for (int i = 0; i < 3; i++) {
			String route = List.of("A north footpath 1.2 km to north gate R1", "B east lane 1.0 km to footbridge R2",
					"C south creek trail 0.8 km to rim marker C").get(i);
			addTask(data, "S" + (i + 1), i < 2 ? "G1" : "G2", "Ground", "Route", i < 2 ? 0 : 8, 8,
					"Rapid hasty route search of spoke " + route + "; inspect adjacent hazards from safe ground. "
							+ "Report GPS track, clues and unsearched gaps to Ops at task end; debrief by radio, "
							+ "take water/rest before next task; do not contaminate the residence IPP.",
					1, i == 1 ? time(8, 45) : time(7, 15), i == 0 ? time(8, 30) : time(10, 0),
					i == 1 ? "planned" : "on task", -1);
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
							+ "1830 demob preparations do not authorize leaving before 1900.",
					2, time(7, 15), time(19, 0), "on task", -1);
		}
		for (int i = 0; i < 4; i++) {
			addTask(data, "K" + (i + 1), "Canine " + (i + 1), "Canine", "Area", 20 + i * 4, 4,
					"Search bounded high-probability inner-ring (25% zone) K" + (i + 1)
							+ " (15-hectare coverage target) beside spoke " + (char) ('A' + i % 3)
							+ "; coordinate nonoverlapping ground subsegments with G1/G2 toward coverage approximately 1 "
							+ "over the 60-hectare zone, not repeated sweeps of the same tracks. "
							+ "Prioritize downwind segments after checking wind; target completion by 1700. "
							+ "Handler and three support searchers work live-find area search "
							+ "in 20-minute bouts with at least 20-minute shaded cooling/water breaks. "
							+ "Reassess at each break; stop for heat distress, unsafe footing or loss of radio contact. "
							+ "Report actual GPS coverage and gaps; no assumed POD. Rest rotation at 1300.",
					3, time(i < 2 ? 7 : 10, 30), time(17, 0), i < 2 ? "on task" : "planned", i);
		}
		addTask(data, "P1", "G1", "Ground", "Point", 0, 8,
				"After S2 debrief and water/rest, check all three attractors within 1 km of residence/IPP by 1200: "
						+ "former bus stop, garden pond/shelter and creek crossing beside old meeting hall. "
						+ "Inspect hazard/water points from safe ground, no water entry. "
						+ "Ask permission before entry, use calm introductions, document each check and negative result; "
						+ "report gaps to Ops. Do not depart while S2 remains active.",
				4, time(10, 30), time(12, 0), "planned", -1);
		for (int i = 0; i < 2; i++) {
			addTask(data, "G" + (i + 1) + "-AREA", "G" + (i + 1), "Ground", "Area", i * 8, 8,
					"After prior tasks and water/rest, grid assigned subsegments of the high-probability inner 25% zone "
							+ "(K1/K2 for G1; K3/K4 for G2). Ops coordinates boundaries with canine teams. "
							+ "Target zone coverage approximately 1 by 1700; record actual GPS tracks, spacing and gaps, "
							+ "reassess detection estimates rather than assuming success. Take shaded rest/heat checks at 1300 "
							+ "and as needed; hourly hydration checks. No water entry.",
					3, i == 0 ? time(12, 30) : time(10, 30), time(17, 0), "planned", -1);
		}
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
		ResourceAssignment row = new ResourceAssignment();
		row.setAssignmentId(id("assignment-" + number));
		row.setAssignmentTeamNumber(number);
		row.setResourceIdentifier(team);
		row.setResourceType(resourceType);
		row.setTaskType(taskType);
		row.setLeader(FIELD_NAMES.get(first));
		row.setNumberOfPersons(count);
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
		task.setCommunications(List.of(contact("Ops", "Command and tactical")));
		task.setPreparedByName(PLANNING);
		task.setPreparedByPositionTitle("Planning Section Chief");
		task.setPreparedDateTime(time(6, 50));
		task.setDebriefingSupervisor(OPS);
		task.setDebriefingSupervisorResourceId(id("MR-IMT-Ops"));
		for (int i = first; i < first + count; i++) {
			TCard card = data.getTCards().get(i);
			if (card.getSourceRef().isBlank()) {
				card.setSourceRef("sar:" + row.getAssignmentId() + (i == first ? ":leader" : ":resource:" + card.getResourceId()));
			}
			task.getResourcesAssigned().add(taskResource(card, i == first ? "Leader" : "Searcher / flanker"));
		}
		if (dogIndex >= 0) {
			TCard dog = data.getTCards().stream()
					.filter(c -> c.getResourceIdentifier().equals("MR-K9-" + (dogIndex + 1))).findFirst().orElseThrow();
			task.getResourcesAssigned().add(taskResource(dog, "Live-find canine"));
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
	 * Creates a communication row that resolves the common ICS 205 channel by identity.
	 *
	 * @param name contact name.
	 * @param function communication purpose.
	 * @return linked communication entry.
	 */
	private static CommunicationEntry contact(String name, String function) {
		CommunicationEntry entry = new CommunicationEntry();
		entry.setName(name);
		entry.setFunction(function);
		entry.setPrimaryContact(CHANNEL + " / 155.160 MHz simplex");
		entry.setRadioChannelId(id("channel"));
		return entry;
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
				"Use repellent per label, long trousers and buddy tick checks at 1000 and demob; "
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
				"End-shift demob: Safety begins tick/hydration checks at 1830 and verifies completion before release; "
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
		List<String> notes = List.of("0700 exercise briefing: objectives, dementia-aware approach, tick/fall/heat controls; "
				+ "42 people checked in: 36 field including four handlers, six IMT at " + ICP + "; four canine cards.",
				"0715 G1 on S1 (A), G2 on S3 (C), two two-person observation teams on R1/R2; "
						+ "G1 S2 (B) planned 0845-1000. Residence IPP protected: " + IPP + ".",
				"0730 K1/K2 deployed; K3/K4 available at " + STAGING
						+ " pending 1030 launch and Ops span/heat reassessment. Six teams active.",
				"0800 R1/R2 report established at north gate and east footbridge; no verified clues. "
						+ "G1 point task 1030-1200 then area 1230-1700; G2 area 1030-1700 planned. "
						+ "Planning acknowledges 0900 ICP water reminder.");
		for (int i = 0; i < notes.size(); i++) {
			ActivityLogEntry entry = new ActivityLogEntry();
			entry.setEntryId(id("activity-" + i));
			entry.setTimestamp(List.of(time(7, 0), time(7, 15), time(7, 30), time(8, 0)).get(i));
			entry.setResourceIdentifier(i == 3 ? "Observation 1" : "Ops");
			entry.setAssignmentId(i == 3 ? id("assignment-R1") : "");
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
