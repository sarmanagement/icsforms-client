/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.ImportValidationException.Problem;
import org.sarmanagement.icsforms.persistence.ImportValidationException.Severity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** Focused coverage for strict external JSON and tolerant stored-document compatibility. */
class ImportValidationTest {
	@TempDir
	Path directory;

	@Test
	void malformedRootsSyntaxAndSchemaNeverCreateIncidents() throws Exception {
		List<String> invalid = List.of("", "null", "[]", "\"text\"", "42", "{", "{} {}",
				"{\"schemaVersion\":5,\"schemaVersion\":6}",
				"{\"schemaVersion\":\"5\"}", "{\"schemaVersion\":0}", "{\"schemaVersion\":-1}",
				"{\"schemaVersion\":5.5}", "{\"schemaVersion\":null}", "{\"schemaVersion\":true}",
				"{\"schemaVersion\":99999999999999}", "{\"schemaVersion\":" + (AppData.CURRENT_SCHEMA_VERSION + 1) + "}");
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			for (String json : invalid) {
				Path source = write(json);
				ImportValidationException failure = assertThrows(ImportValidationException.class,
						() -> store.importFile(source), json);
				assertEquals(source, failure.getSourceFile());
				assertTrue(failure.getProblems().stream().anyMatch(problem -> problem.severity() == Severity.ERROR));
				assertEquals(json, Files.readString(source));
				assertTrue(store.list().isEmpty());
				assertThrows(UnsupportedOperationException.class, () -> failure.getProblems().clear());
			}
		}
	}

	@Test
	void diagnosticExceptionKeepsAnImmutableSnapshotAndOriginalCause() {
		Path source = directory.resolve("input.json");
		Problem problem = new Problem(Severity.ERROR, "$.form201", "Invalid form.", "Use an object.");
		List<Problem> mutable = new ArrayList<>(List.of(problem));
		IllegalArgumentException cause = new IllegalArgumentException("Original");
		ImportValidationException failure = new ImportValidationException(source, mutable, cause);
		mutable.clear();
		assertEquals(List.of(problem), failure.getProblems());
		assertEquals(source, failure.getSourceFile());
		assertSame(cause, failure.getCause());
		assertEquals(Severity.ERROR, problem.severity());
		assertEquals("$.form201", problem.path());
		assertEquals("Invalid form.", problem.message());
		assertEquals("Use an object.", problem.suggestedFix());
		assertThrows(UnsupportedOperationException.class, () -> failure.getProblems().add(problem));
	}

	@Test
	void missingFilePreservesIoCauseAndParseFailureIncludesLocation() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			ImportValidationException missing = assertThrows(ImportValidationException.class,
					() -> store.importFile(directory.resolve("missing.json")));
			assertInstanceOf(java.io.IOException.class, missing.getCause());
			Path source = write("{\n  \"incidentContext\":\n}");
			ImportValidationException broken = assertThrows(ImportValidationException.class,
					() -> store.importFile(source));
			assertInstanceOf(JsonProcessingException.class, broken.getCause());
			assertTrue(broken.getProblems().stream().anyMatch(problem -> problem.message().contains("line 3")
					&& problem.message().contains("column")));
		}
	}

	@Test
	void unknownOnlyFailureKeepsJacksonCauseAndFutureSchemaUsesRequiredMessage() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			ImportValidationException unknown = assertThrows(ImportValidationException.class,
					() -> store.importFile(write("{\"incidentContext\":{\"incidentName\":\"Named\"},\"typo\":true}")));
			assertInstanceOf(UnrecognizedPropertyException.class, unknown.getCause());
			assertTrue(unknown.getProblems().stream().anyMatch(problem -> problem.path().equals("$.typo")));
			int newer = AppData.CURRENT_SCHEMA_VERSION + 1;
			ImportValidationException future = assertThrows(ImportValidationException.class,
					() -> store.importFile(write("{\"schemaVersion\":" + newer + "}")));
			assertTrue(future.getProblems().stream().anyMatch(problem -> problem.message().equals("File schema "
					+ newer + " is newer than supported schema " + AppData.CURRENT_SCHEMA_VERSION
					+ "; update icsforms-client")));
		}
	}

	@Test
	void planningStageSuggestionsUseJsonLiteralsInsteadOfDisplayLabels() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			ImportValidationException failure = assertThrows(ImportValidationException.class,
					() -> store.importFile(write("{\"schemaVersion\":15,\"incidentContext\":{\"incidentName\":\"Named\"},"
							+ "\"iapPlanningStage\":\"OBJECTIVES_RECORDED\"}")));
			Problem problem = failure.getProblems().stream()
					.filter(item -> item.path().equals("$.iapPlanningStage")).findFirst().orElseThrow();
			assertTrue(problem.suggestedFix().contains("OPERATIONAL_PERIOD_ACTIVE"));
			assertTrue(problem.suggestedFix().contains("OBJECTIVES_DEVELOPMENT"));
			assertFalse(problem.suggestedFix().contains("Objectives development"));
			assertFalse(problem.suggestedFix().contains("Operational period active"));
		}
	}

	@Test
	void aggregatesNestedUnknownFieldsTypesAndEnumsWithIndexedPaths() throws Exception {
		String json = """
				{
				"schemaVersion": 5,
				"incidentMode": "NOT_A_MODE",
				"iapPhase": "NOT_A_PHASE",
				"revision": "wrong",
				"incidentContext": {"incidentName": "Real incident", "operationalPeriodStart": "not-a-date",
					"misspelledName": "private text"},
				"form202": {"objectivez": ["private text"]},
				"sarTaskAssignments": [{"typo": true, "resourcesAssigned": [{"mistake": 3}]}],
				"tcards": [{"cardType": "NOT_A_CARD"}],
				"unknownRoot": "private text"
				}
				""";
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			ImportValidationException failure = assertThrows(ImportValidationException.class,
					() -> store.importFile(write(json)));
			List<String> paths = failure.getProblems().stream().map(Problem::path).toList();
			for (String path : List.of("$.incidentMode", "$.iapPhase", "$.revision",
					"$.incidentContext.operationalPeriodStart", "$.incidentContext.misspelledName",
					"$.form202.objectivez", "$.sarTaskAssignments[0].typo",
					"$.sarTaskAssignments[0].resourcesAssigned[0].mistake", "$.tcards[0].cardType", "$.unknownRoot")) {
				assertTrue(paths.contains(path), path);
			}
			assertInstanceOf(JsonProcessingException.class, failure.getCause());
			assertTrue(failure.getProblems().stream().anyMatch(problem -> problem.path().equals("$.incidentMode")
					&& problem.suggestedFix().contains("SAR") && problem.suggestedFix().contains("Valid values")));
			assertTrue(failure.getProblems().stream().noneMatch(problem -> problem.message().contains("private text")));
			assertTrue(store.list().isEmpty());
		}
	}

	@Test
	void rejectsSilentScalarCoercionAndWrongContainerShapes() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			for (String fragment : List.of("\"revision\":1.2", "\"revision\":null", "\"incidentMode\":0",
					"\"useSystemTimeZone\":\"true\"", "\"form201\":[]", "\"tcards\":{}",
					"\"incidentContext\":{\"incidentName\":123}")) {
				Path source = write("{\"schemaVersion\":5,\"form202\":{\"commandEmphasis\":\"Content\"},"
						+ fragment + "}");
				assertThrows(ImportValidationException.class, () -> store.importFile(source), fragment);
				assertTrue(store.list().isEmpty());
			}
		}
	}

	@Test
	void rejectsNullModelRecordsAtTheirExactIndexedPathsBeforeCreation() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			for (String field : List.of("sarTaskAssignments", "tcards", "activityLogs", "clueLogEntries",
					"openActionItems", "additionalForms204", "operationalPeriodHistory", "incidentLocations")) {
				Path source = write("{\"schemaVersion\":15,\"incidentContext\":{\"incidentName\":\"Named\"},\""
						+ field + "\":[null]}");
				ImportValidationException failure = assertThrows(ImportValidationException.class, () -> store.importFile(source));
				assertInstanceOf(MismatchedInputException.class, failure.getCause());
				assertTrue(failure.getProblems().stream().anyMatch(problem -> problem.path().equals("$." + field + "[0]")
						&& problem.severity() == Severity.ERROR), failure.getProblems().toString());
				assertTrue(store.list().isEmpty());
			}
			Path source = write("{\"schemaVersion\":15,\"incidentContext\":{\"incidentName\":\"Named\"},"
					+ "\"sarTaskAssignments\":[{\"resourcesAssigned\":[null]}],"
					+ "\"form201\":{\"currentActions\":[null]},\"activityLogs\":[{\"activityLog\":[null]}]}");
			ImportValidationException failure = assertThrows(ImportValidationException.class, () -> store.importFile(source));
			List<String> paths = failure.getProblems().stream().map(Problem::path).toList();
			assertTrue(paths.contains("$.sarTaskAssignments[0].resourcesAssigned[0]"));
			assertTrue(paths.contains("$.form201.currentActions[0]"));
			assertTrue(paths.contains("$.activityLogs[0].activityLog[0]"));
			assertTrue(store.list().isEmpty());
			Path genericCards = write("{\"schemaVersion\":15,\"incidentMode\":\"GENERIC\","
					+ "\"incidentContext\":{\"incidentName\":\"Named\"},\"tcards\":[null]}");
			ImportValidationException genericFailure = assertThrows(ImportValidationException.class,
					() -> store.importFile(genericCards));
			assertInstanceOf(MismatchedInputException.class, genericFailure.getCause());
			assertTrue(genericFailure.getProblems().stream().anyMatch(problem -> problem.path().equals("$.tcards[0]")));
			assertTrue(store.list().isEmpty());
		}
	}

	@Test
	void rejectsEffectivelyEmptyDefaultAndMetadataOnlyDocuments() throws Exception {
		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
		ObjectNode defaults = mapper.valueToTree(new AppData());
		defaults.put("incidentId", IncidentId.newId().value());
		defaults.put("revision", 14);
		defaults.put("createdAt", "2026-01-01T00:00:00Z");
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			for (String json : List.of("{}", "{\"schemaVersion\":5}",
					"{\"incidentContext\":{\"incidentName\":\"   \"},\"tcards\":[],\"sarTaskAssignments\":[]}",
					"{\"tcards\":[{}],\"sarTaskAssignments\":[{}],\"form202\":{\"objectives\":[\"  \"]}}",
					"{\"schemaVersion\":15,\"openActionTrackerEnabled\":true}",
					"{\"schemaVersion\":15,\"incidentMode\":\"GENERIC\"}",
					"{\"schemaVersion\":15,\"incidentMode\":\"GENERIC\",\"openActionTrackerEnabled\":true}",
					"{\"form205\":{\"channels\":[{}]}}",
					"{\"activityLogs\":[{\"activityLog\":[{}]}]}",
					"{\"clueLogEntries\":[{}]}",
					"{\"form215a\":{\"entries\":[{}]}}",
					"{\"incidentLocations\":[{}]}",
					"{\"vetDirectory\":{\"facilities\":[{}]}}",
					defaults.toString())) {
				ImportValidationException failure = assertThrows(ImportValidationException.class,
						() -> store.importFile(write(json)));
				assertTrue(failure.getProblems().stream().anyMatch(problem -> problem.message()
						.equals("File does not appear to be an icsforms incident export")),
						failure.getProblems().toString());
			}
			assertTrue(store.list().isEmpty());
		}
	}

	@Test
	void rejectsNormalizedDefaultDocumentExportedByActualStore() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId original = store.create(new AppData());
			Path exported = directory.resolve("empty-export.json");
			store.exportFile(original, exported);
			String originalJson = Files.readString(exported);
			ImportValidationException failure = assertThrows(ImportValidationException.class,
					() -> store.importFile(exported));
			assertTrue(failure.getProblems().stream().anyMatch(problem -> problem.message()
					.equals("File does not appear to be an icsforms incident export")));
			assertEquals(originalJson, Files.readString(exported));
			assertEquals(1, store.list().size());
			assertEquals(original, store.list().get(0).incidentId());
		}
	}

	@Test
	void acceptsContentFromEveryMajorWorkspaceAreaWithoutAName() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			for (String content : List.of("\"form201\":{\"situationSummary\":\"Search started\"}",
					"\"form202\":{\"objectives\":[\"Locate missing person\"]}",
					"\"form204\":{\"sharedWorkAssignment\":\"Search trail\"}",
					"\"form205\":{\"channels\":[{\"programmingMetadata\":{\"vendorField\":\"Retained\"}}]}",
					"\"sarTaskAssignments\":[{\"assignment\":\"Search trail\"}]",
					"\"tcards\":[{\"personName\":\"Fictional Responder\"}]",
					"\"activityLogs\":[{\"name\":\"Fictional Team\"}]",
					"\"clueLogEntries\":[{\"description\":\"Fictional clue\"}]")) {
				IncidentId id = store.importFile(write("{" + content + "}")).orElseThrow();
				assertNotNull(store.load(id));
				assertEquals(List.of("$.schemaVersion", "$.incidentContext.incidentName"),
						store.getLastImportProblems().stream().map(Problem::path).toList());
				assertTrue(store.getLastImportProblems().stream().allMatch(problem -> problem.severity() == Severity.WARNING));
				assertThrows(UnsupportedOperationException.class, () -> store.getLastImportProblems().clear());
			}
		}
	}

	@Test
	void acceptsMapOnlyOrganizationalAndSiteSafetyContent() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId organization = store.importFile(write("{\"schemaVersion\":15,\"organizationalChart\":"
					+ "{\"assignmentList\":{\"positions\":{\"DEPUTY_INCIDENT_COMMANDER\":\"Alex\"}}}}")).orElseThrow();
			assertTrue(store.load(organization).getOrganizationalChart().getAssignmentList().getPositions()
					.containsValue("Alex"));
			assertEquals(List.of("$.incidentContext.incidentName"),
					store.getLastImportProblems().stream().map(Problem::path).toList());
			IncidentId safety = store.importFile(write("{\"schemaVersion\":15,\"form208\":{\"siteSafetyPlan\":"
					+ "{\"textSections\":{\"Emergency\":\"Use fictional evacuation route\"}}}}")).orElseThrow();
			assertEquals("Use fictional evacuation route",
					store.load(safety).getForm208().getSiteSafetyPlan().getTextSections().get("Emergency"));
		}
	}

	@Test
	void fallbackBindingReportsMapKeyPathAndValidEnumLiterals() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			Path source = write("{\"schemaVersion\":15,\"organizationalChart\":{\"assignmentList\":"
					+ "{\"positions\":{\"INVALID_POSITION\":\"Alex\"}}}}");
			ImportValidationException failure = assertThrows(ImportValidationException.class,
					() -> store.importFile(source));
			assertInstanceOf(InvalidFormatException.class, failure.getCause());
			Problem problem = failure.getProblems().stream()
					.filter(item -> item.severity() == Severity.ERROR).findFirst().orElseThrow();
			assertEquals("$.organizationalChart.assignmentList.positions", problem.path());
			assertTrue(problem.suggestedFix().contains("DEPUTY_INCIDENT_COMMANDER"));
			assertTrue(problem.suggestedFix().contains("Valid values:"));
			assertFalse(problem.message().contains("Alex"));
			assertTrue(store.list().isEmpty());
		}
	}

	@Test
	void acceptsLegacyAliasesAndRoundTripsNestedContentWithoutWarnings() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			Path source = write("""
					{"schemaVersion":5,"incidentContext":{"incidentName":"Fictional search"},
					"organizationalChart":{"safetyOfficerContact":"555-0100"},
					"sarTaskAssignments":[{"specialInstructions":"Carry water","assignment":"Trail"}],
					"tcards":[{"cardType":"AIRCRAFT","resourceIdentifier":"Fictional aircraft"},
					{"cardType":"DOZER","resourceIdentifier":"Fictional equipment"}]}
					""");
			IncidentId original = store.importFile(source).orElseThrow();
			assertTrue(store.getLastImportProblems().isEmpty());
			assertEquals("Carry water", store.load(original).getSarTaskAssignments().get(0).getSpecialEquipment());
			assertEquals("555-0100", store.load(original).getOrganizationalChart().getSafetyOfficerPhone());
			assertEquals("FIXED_WING", store.load(original).getTCards().get(0).getCardType().name());
			assertEquals("EQUIPMENT", store.load(original).getTCards().get(1).getCardType().name());
			Path exported = directory.resolve("export.json");
			store.exportFile(original, exported);
			IncidentId copy = store.importFile(exported).orElseThrow();
			assertNotEquals(original, copy);
			assertEquals("Carry water", store.load(copy).getSarTaskAssignments().get(0).getSpecialEquipment());
			assertEquals(2, store.load(copy).getTCards().size());
			assertTrue(store.getLastImportProblems().isEmpty());
			assertTrue(store.importFile(null).isEmpty());
			assertTrue(store.getLastImportProblems().isEmpty());
		}
	}

	@Test
	void boundsProblemCountAndLogsOnlySafeMetadata() throws Exception {
		ObjectNode json = new ObjectMapper().createObjectNode();
		json.put("schemaVersion", 5);
		for (int i = 0; i < 150; i++) {
			json.put("unknown" + i, "PRIVATE_DOCUMENT_CONTENT");
		}
		CaptureHandler capture = new CaptureHandler();
		Logger logger = Logger.getLogger(IncidentDocumentReader.class.getName());
		logger.addHandler(capture);
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			Path source = write(json.toString());
			ImportValidationException failure = assertThrows(ImportValidationException.class, () -> store.importFile(source));
			assertEquals(100, failure.getProblems().size());
			String messages = String.join("\n", capture.messages);
			assertTrue(messages.contains(source.toString()));
			assertTrue(messages.contains("schema=5"));
			assertTrue(messages.contains("outcome=attempt"));
			assertTrue(messages.contains("outcome=rejected"));
			assertTrue(messages.contains("count=100"));
			assertTrue(messages.contains("$.unknown0"));
			assertFalse(messages.contains("PRIVATE_DOCUMENT_CONTENT"));
			assertTrue(capture.records.stream().allMatch(record -> record.getThrown() == null));
			int count = capture.messages.size();
			store.importFile(write("{\"incidentContext\":{\"incidentName\":\"PRIVATE_DOCUMENT_CONTENT\"}}"));
			String success = String.join("\n", capture.messages.subList(count, capture.messages.size()));
			assertTrue(success.contains("outcome=validated"));
			assertTrue(success.contains("outcome=import-persisted"));
			assertTrue(success.contains("count=1"));
			assertFalse(success.contains("PRIVATE_DOCUMENT_CONTENT"));
		} finally {
			logger.removeHandler(capture);
		}
	}

	@Test
	void failedStorageNeverLogsImportSuccess() throws Exception {
		CaptureHandler capture = new CaptureHandler();
		Logger logger = Logger.getLogger(IncidentDocumentReader.class.getName());
		logger.addHandler(capture);
		try (FileIncidentStore store = new FailingCreateStore(directory)) {
			Path source = write("{\"schemaVersion\":5,\"incidentContext\":{\"incidentName\":\"PRIVATE_CONTENT\"}}");
			assertThrows(IllegalStateException.class, () -> store.importFile(source));
			String logs = String.join("\n", capture.messages);
			assertTrue(logs.contains("outcome=validated"));
			assertTrue(logs.contains("outcome=import-failed"));
			assertFalse(logs.contains("outcome=import-persisted"));
			assertFalse(logs.contains("outcome=success"));
			assertFalse(logs.contains("PRIVATE_CONTENT"));
			assertTrue(store.getLastImportProblems().isEmpty());
		} finally {
			logger.removeHandler(capture);
		}
	}

	@Test
	void storedUnknownFieldsWarnAndLegacyMigrationDoesNotUseStrictImport() throws Exception {
		CaptureHandler capture = new CaptureHandler();
		Logger logger = Logger.getLogger(IncidentDocumentReader.class.getName());
		logger.addHandler(capture);
		String legacy = "{\"schemaVersion\":5,\"futureField\":\"PRIVATE_DOCUMENT_CONTENT\","
				+ "\"form202\":{\"futureNested\":true}}";
		Files.writeString(directory.resolve("incident.json"), legacy);
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			assertEquals(1, store.list().size());
			assertEquals(AppData.CURRENT_SCHEMA_VERSION, store.load(store.list().get(0).incidentId()).getSchemaVersion());
			assertEquals(legacy, Files.readString(directory.resolve("incident.json")));
			assertTrue(Files.exists(directory.resolve("incident.json.migrated")));
			String logs = String.join("\n", capture.messages);
			assertTrue(logs.contains("outcome=stored-warning"));
			assertTrue(logs.contains("$.futureField"));
			assertTrue(logs.contains("$.form202.futureNested"));
			assertFalse(logs.contains("PRIVATE_DOCUMENT_CONTENT"));
			assertFalse(logs.contains("outcome=attempt"));
		} finally {
			logger.removeHandler(capture);
		}
	}

	@Test
	void legacyMigrationRecoversBackupWithoutStrictValidation() throws Exception {
		Files.writeString(directory.resolve("incident.json"), "{broken");
		Files.writeString(directory.resolve("incident.json.bak"), "{\"schemaVersion\":5,\"unknownLegacy\":true}");
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			assertEquals(1, store.list().size());
			assertTrue(Files.exists(directory.resolve("incident.json.migrated")));
			assertTrue(store.getLastImportProblems().isEmpty());
			assertEquals("{broken", Files.readString(directory.resolve("incident.json")));
		}
	}

	@Test
	void standaloneReaderValidatesWithoutCreatingStoreArtifacts() throws Exception {
		Path source = write("{\"schemaVersion\":5,\"form201\":{\"situationSummary\":\"Retained\"}}");
		AppData data = FileIncidentStore.readImportFile(source);
		assertEquals("Retained", data.getForm201().getSituationSummary());
		FileIncidentStore.ImportResult result = FileIncidentStore.readImport(source);
		assertEquals("Retained", result.data().getForm201().getSituationSummary());
		assertEquals(List.of("$.incidentContext.incidentName"), result.problems().stream().map(Problem::path).toList());
		assertThrows(UnsupportedOperationException.class, () -> result.problems().clear());
		assertFalse(Files.exists(directory.resolve("incidents")));
		assertThrows(ImportValidationException.class, () -> FileIncidentStore.readImportFile(write("{}")));
	}

	/**
	 * Writes an external document under the isolated test directory.
	 *
	 * @param json source JSON
	 * @return source path
	 * @throws Exception when writing fails
	 */
	private Path write(String json) throws Exception {
		return Files.writeString(directory.resolve("input.json"), json);
	}

	/** Simulates storage failure after a document has passed import validation. */
	private static class FailingCreateStore extends FileIncidentStore {
		/**
		 * Opens an isolated failing store.
		 *
		 * @param root store directory
		 */
		FailingCreateStore(Path root) {
			super(root);
		}

		/**
		 * Rejects persistence to exercise post-validation failure reporting.
		 *
		 * @param data validated document
		 * @return never returns an identifier
		 * @throws IllegalStateException always, to simulate unavailable storage
		 */
		@Override
		public IncidentId create(AppData data) {
			throw new IllegalStateException("Simulated storage failure");
		}
	}

	/** Collects log metadata without forwarding it to another destination. */
	private static class CaptureHandler extends Handler {
		private final List<String> messages = new ArrayList<>();
		private final List<LogRecord> records = new ArrayList<>();

		/**
		 * Captures an emitted record.
		 *
		 * @param record emitted log record
		 */
		@Override
		public void publish(LogRecord record) {
			messages.add(record.getMessage());
			records.add(record);
		}

		/** Flushes the in-memory collector; no operation is required. */
		@Override
		public void flush() {
		}

		/** Closes the in-memory collector; no operation is required. */
		@Override
		public void close() {
		}
	}
}
