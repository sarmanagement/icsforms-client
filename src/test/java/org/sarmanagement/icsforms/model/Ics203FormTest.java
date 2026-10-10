/*
 * Copyright (C) 2026 SAR Management
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See <https://www.gnu.org/licenses/>.
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for supplemental ICS 203 storage and assignment detection. */
class Ics203FormTest {
	private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@Test
	void assignmentListSurvivesFileStoreCreateSaveExportAndImport() throws Exception {
		Path directory = Files.createDirectories(Path.of("target"));
		Path workspace = Files.createTempDirectory(directory, "ics203-store-");
		try (FileIncidentStore store = new FileIncidentStore(workspace.resolve("store"))) {
			AppData data = new AppData();
			OrganizationalChart chart = data.getOrganizationalChart();
			chart.setIncidentCommanders(List.of("Commander / Agency (T)"));
			chart.setSafetyOfficerName("Shared Safety");
			Ics203Form form = chart.getAssignmentList();
			for (Ics203Form.Position position : Ics203Form.Position.values()) {
				form.setPosition(position, "Assigned " + position.name());
			}
			OrgChartEntry agency = new OrgChartEntry();
			agency.setTitle("Partner Agency");
			agency.setName("Representative");
			form.setAgencyRepresentatives(List.of(agency));
			Ics203Form.Branch branch = new Ics203Form.Branch();
			branch.setName("North Branch");
			branch.setDirector("Director");
			branch.setDeputy("Deputy (T)");
			OrgChartEntry division = new OrgChartEntry();
			division.setTitle("Division A");
			division.setName("Supervisor");
			branch.setDivisions(List.of(division));
			form.setBranches(List.of(branch));
			form.setIncludeInIap(false);
			form.setIapPage("7");
			form.setPreparedByName("Planner");
			form.setPreparedByPositionTitle("Resources Unit Leader");
			form.setPreparedBySignature("Signed");
			form.setPreparedDateTime(LocalDateTime.of(2026, 10, 10, 14, 30));
			IncidentId originalId = store.create(data);
			AppData loaded = store.load(originalId);
			assertEquals(mapper.valueToTree(chart), mapper.valueToTree(loaded.getOrganizationalChart()));
			assertEquals(AppData.CURRENT_SCHEMA_VERSION, loaded.getSchemaVersion());
			loaded.getOrganizationalChart().getAssignmentList().getBranches().get(0).setDeputy("Updated Deputy");
			loaded.getOrganizationalChart().getAssignmentList().setPreparedBySignature("Updated Signature");
			store.save(originalId, loaded);
			AppData saved = store.load(originalId);
			assertEquals(1, saved.getRevision());
			assertEquals("Updated Deputy", saved.getOrganizationalChart().getAssignmentList().getBranches().get(0).getDeputy());
			Path exported = workspace.resolve("exported.json");
			store.exportFile(originalId, exported);
			IncidentId importedId = store.importFile(exported).orElseThrow();
			assertNotEquals(originalId, importedId);
			AppData imported = store.load(importedId);
			assertEquals(mapper.valueToTree(saved.getOrganizationalChart()),
					mapper.valueToTree(imported.getOrganizationalChart()));
			assertEquals("Updated Signature", imported.getOrganizationalChart().getAssignmentList().getPreparedBySignature());
			assertTrue(imported.getOrganizationalChart().hasAssignments());
			try (FileIncidentStore reopened = new FileIncidentStore(workspace.resolve("store"))) {
				assertEquals(mapper.valueToTree(saved.getOrganizationalChart()),
						mapper.valueToTree(reopened.load(importedId).getOrganizationalChart()));
			}
		} finally {
			try (var paths = Files.walk(workspace)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
					Files.deleteIfExists(path);
				}
			}
		}
	}

	@Test
	void oldAndNullJsonRetainEmptyDefaultAssignmentList() throws Exception {
		for (String json : List.of("{\"safetyOfficerName\":\"Safety\"}",
				"{\"assignmentList\":null,\"safetyOfficerName\":\"Safety\"}")) {
			OrganizationalChart chart = mapper.readValue(json, OrganizationalChart.class);
			assertNotNull(chart.getAssignmentList());
			assertTrue(chart.getAssignmentList().isIncludeInIap());
			assertFalse(chart.getAssignmentList().hasAssignments());
			assertEquals("Safety", chart.getSafetyOfficerName());
		}
	}

	@Test
	void settersAndJsonAreNullSafe() throws Exception {
		Ics203Form form = mapper.readValue("""
				{"positions":null,"agencyRepresentatives":null,"branches":null,
				"preparedByName":null,"preparedByPositionTitle":null,"preparedBySignature":null,"iapPage":null}
				""", Ics203Form.class);
		assertTrue(form.getPositions().isEmpty());
		assertTrue(form.getAgencyRepresentatives().isEmpty());
		assertTrue(form.getBranches().isEmpty());
		assertEquals("", form.getPreparedByName());
		assertEquals("", form.getPreparedByPositionTitle());
		assertEquals("", form.getPreparedBySignature());
		assertEquals("", form.getIapPage());
		Map<Ics203Form.Position, String> positions = new HashMap<>();
		positions.put(null, "Ignored");
		positions.put(Ics203Form.Position.MEDICAL_UNIT_LEADER, null);
		form.setPositions(positions);
		assertEquals("", form.getPosition(Ics203Form.Position.MEDICAL_UNIT_LEADER));
		assertEquals("", form.getPosition(null));
		Ics203Form.Branch branch = new Ics203Form.Branch();
		branch.setName(null);
		branch.setDirector(null);
		branch.setDeputy(null);
		branch.setDivisions(null);
		assertEquals("", branch.getName());
		assertEquals("", branch.getDirector());
		assertEquals("", branch.getDeputy());
		assertTrue(branch.getDivisions().isEmpty());
		OrganizationalChart chart = new OrganizationalChart();
		chart.setAssignmentList(null);
		assertNotNull(chart.getAssignmentList());
	}

	@Test
	void allSupplementalStructuresRoundTripWithoutChangingSharedChart() throws Exception {
		OrganizationalChart chart = new OrganizationalChart();
		chart.setSafetyOfficerName("Shared Safety");
		chart.setIncidentCommanders(List.of("Commander / Agency (T)"));
		Ics203Form form = chart.getAssignmentList();
		for (Ics203Form.Position position : Ics203Form.Position.values()) {
			form.setPosition(position, position.name());
		}
		OrgChartEntry agency = new OrgChartEntry();
		agency.setTitle("Agency");
		agency.setName("Representative");
		form.setAgencyRepresentatives(List.of(agency));
		Ics203Form.Branch branch = new Ics203Form.Branch();
		branch.setName("Branch 1");
		branch.setDirector("Director");
		branch.setDeputy("Deputy (T)");
		OrgChartEntry division = new OrgChartEntry();
		division.setTitle("Division A");
		division.setName("Supervisor");
		branch.setDivisions(List.of(division));
		form.setBranches(List.of(branch));
		form.setIncludeInIap(false);
		form.setIapPage("3");
		form.setPreparedByName("Preparer");
		form.setPreparedByPositionTitle("Planning Chief");
		form.setPreparedBySignature("Signed");
		form.setPreparedDateTime(LocalDateTime.of(2026, 10, 10, 14, 30));
		String json = mapper.writeValueAsString(chart);
		OrganizationalChart copy = mapper.readValue(json, OrganizationalChart.class);
		assertEquals(mapper.valueToTree(chart), mapper.valueToTree(copy));
		assertFalse(json.contains("hasAssignments"));
		assertFalse(json.contains("\"assignments\""));
		assertEquals("Supervisor", copy.getAssignmentList().getBranches().get(0).getDivisions().get(0).getName());
		assertEquals("Shared Safety", copy.getSafetyOfficerName());
		assertTrue(copy.hasAssignments());
	}

	@Test
	void everyExistingNameCountsButContactsDoNot() throws Exception {
		for (var method : OrganizationalChart.class.getMethods()) {
			if (method.getName().startsWith("set") && method.getName().endsWith("Name")
					&& Arrays.equals(method.getParameterTypes(), new Class<?>[]{String.class})) {
				OrganizationalChart chart = new OrganizationalChart();
				method.invoke(chart, " ");
				assertFalse(chart.hasAssignments(), method.getName());
				method.invoke(chart, "Assigned");
				assertTrue(chart.hasAssignments(), method.getName());
			}
		}
		OrganizationalChart chart = new OrganizationalChart();
		chart.setIncidentCommanderPhone("123");
		chart.setSafetyOfficerRadio("Channel 1");
		OrgChartEntry entry = new OrgChartEntry();
		entry.setPhone("123");
		entry.setRadio("Channel 2");
		chart.setIncidentCommanderEntries(Arrays.asList(null, entry));
		chart.setAdditionalPositions(List.of(entry));
		chart.setIncidentCommanders(Arrays.asList(null, " "));
		assertFalse(chart.hasAssignments());
		OrgChartEntry commander = new OrgChartEntry();
		commander.setTitle("Incident Commander");
		commander.setRadio("Channel");
		chart.setIncidentCommanderEntries(List.of(commander));
		assertFalse(chart.hasAssignments());
		commander.setName("Commander");
		assertTrue(chart.hasAssignments());
		commander.setName("");
		entry.setTitle("Organizational label");
		assertTrue(chart.hasAssignments());
		entry.setTitle("");
		chart.setIncidentCommanders(List.of("Commander"));
		assertTrue(chart.hasAssignments());
	}

	@Test
	void supplementalLabelsCountButMetadataAndContactsDoNot() {
		Ics203Form form = new Ics203Form();
		form.setIncludeInIap(true);
		form.setPreparedByName("Preparer");
		form.setPreparedByPositionTitle("Title");
		form.setPreparedBySignature("Signature");
		form.setPreparedDateTime(LocalDateTime.now());
		form.setIapPage("3");
		assertFalse(form.hasAssignments());
		for (Ics203Form.Position position : Ics203Form.Position.values()) {
			form.setPosition(position, " ");
			assertFalse(form.hasAssignments());
			form.setPosition(position, "Assigned");
			assertTrue(form.hasAssignments());
			form.setPosition(position, null);
		}
		OrgChartEntry entry = new OrgChartEntry();
		entry.setRadio("Channel");
		form.setAgencyRepresentatives(Arrays.asList(null, entry));
		assertFalse(form.hasAssignments());
		entry.setTitle("Agency");
		assertTrue(form.hasAssignments());
		entry.setTitle("");
		entry.setName("Representative");
		assertTrue(form.hasAssignments());
		form.setAgencyRepresentatives(null);
		Ics203Form.Branch branch = new Ics203Form.Branch();
		form.setBranches(Arrays.asList(null, branch));
		assertFalse(form.hasAssignments());
		branch.setName("Branch");
		assertTrue(form.hasAssignments());
		branch.setName("");
		branch.setDirector("Director");
		assertTrue(form.hasAssignments());
		branch.setDirector("");
		branch.setDeputy("Deputy");
		assertTrue(form.hasAssignments());
		branch.setDeputy("");
		branch.setDivisions(List.of(entry));
		assertTrue(form.hasAssignments());
		entry.setName("");
		entry.setTitle("Division A");
		assertTrue(form.hasAssignments());
		OrganizationalChart chart = new OrganizationalChart();
		chart.setAssignmentList(form);
		assertTrue(chart.hasAssignments());
	}
}
