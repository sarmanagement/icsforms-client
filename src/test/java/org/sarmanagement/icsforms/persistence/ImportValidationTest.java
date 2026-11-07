/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AircraftResource;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CanineResource;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.CrewResource;
import org.sarmanagement.icsforms.model.EquipmentResource;
import org.sarmanagement.icsforms.model.GenericResource;
import org.sarmanagement.icsforms.model.Ics201Form;
import org.sarmanagement.icsforms.model.Ics202ReportedResult;
import org.sarmanagement.icsforms.model.Ics203Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.Ics205Form;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.Ics215Form;
import org.sarmanagement.icsforms.model.IncidentLocation;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.IncidentResource;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.OpenActionItem;
import org.sarmanagement.icsforms.model.OrgChartEntry;
import org.sarmanagement.icsforms.model.PersonnelResource;
import org.sarmanagement.icsforms.model.PocAssignment;
import org.sarmanagement.icsforms.model.PodFactorRating;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SafetyAnalysisEntry;
import org.sarmanagement.icsforms.model.SafetyHazardReport;
import org.sarmanagement.icsforms.model.SafetyMessageBlock;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.VetDirectory;
import org.sarmanagement.icsforms.model.VetFacility;
import org.sarmanagement.icsforms.persistence.ImportValidationException.Problem;
import org.sarmanagement.icsforms.persistence.ImportValidationException.Severity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** Focused coverage for strict external JSON and tolerant stored-document compatibility. */
class ImportValidationTest {
	// IncidentResource and its concrete variants are not currently stored in AppData;
	// TCard is the persisted resource record and is validated independently.
	private static final Map<Class<?>, Set<String>> AUDITED_RECORD_IDENTITIES = Map.ofEntries(
			Map.entry(AppData.class, Set.of("incidentId")),
			Map.entry(ActivityLogEntry.class, Set.of("entryId")),
			Map.entry(ActivityLogEntry.EventTypeChange.class, Set.of("changeId")),
			Map.entry(ClueLogEntry.class, Set.of("entryId")),
			Map.entry(CommunicationEntry.class, Set.of("id")),
			Map.entry(Ics201Form.ActionEntry.class, Set.of("id")),
			Map.entry(Ics201Form.ResourceSummaryEntry.class, Set.of("id")),
			Map.entry(Ics202ReportedResult.class, Set.of("resultId")),
			Map.entry(Ics203Form.Branch.class, Set.of("id")),
			Map.entry(Ics204Form.class, Set.of("formId")),
			Map.entry(Ics205Form.class, Set.of("formId")),
			Map.entry(Ics214Form.class, Set.of("logId")),
			Map.entry(Ics215Form.class, Set.of("worksheetId")),
			Map.entry(Ics215Form.ResourceHeading.class, Set.of("id")),
			Map.entry(Ics215Form.WorkRow.class, Set.of("id")),
			Map.entry(IncidentLocation.class, Set.of("id")),
			Map.entry(IncidentObjective.class, Set.of("objectiveId")),
			Map.entry(IncidentResource.class, Set.of("resourceId")),
			Map.entry(PersonnelResource.class, Set.of("resourceId")),
			Map.entry(CanineResource.class, Set.of("resourceId")),
			Map.entry(CrewResource.class, Set.of("resourceId")),
			Map.entry(EquipmentResource.class, Set.of("resourceId")),
			Map.entry(AircraftResource.class, Set.of("resourceId")),
			Map.entry(GenericResource.class, Set.of("resourceId")),
			Map.entry(MedicalPlan.AidStation.class, Set.of("id")),
			Map.entry(MedicalPlan.Hospital.class, Set.of("id")),
			Map.entry(MedicalPlan.TransportService.class, Set.of("id")),
			Map.entry(OrgChartEntry.class, Set.of("id")),
			Map.entry(OpenActionItem.class, Set.of("id")),
			Map.entry(PocAssignment.class, Set.of("id")),
			Map.entry(PodFactorRating.class, Set.of("id")),
			Map.entry(RadioChannel.class, Set.of("channelId")),
			Map.entry(ResourceAssignment.class, Set.of("assignmentId")),
			Map.entry(SafetyAnalysisEntry.class, Set.of("entryId")),
			Map.entry(SafetyHazardReport.class, Set.of("id")),
			Map.entry(SafetyMessageBlock.class, Set.of("id")),
			Map.entry(SarTaskAssignment.class, Set.of("taskId")),
			Map.entry(SarTaskResource.class, Set.of("id")),
			Map.entry(AppData.OperationalPeriodRecord.class, Set.of("recordId")),
			Map.entry(VetDirectory.Source.class, Set.of("id")),
			Map.entry(TCard.class, Set.of("resourceId")),
			Map.entry(VetFacility.class, Set.of("id")));
	private static final Set<Class<?>> MODEL_ONLY_POLYMORPHIC_RESOURCE_TYPES = Set.of(IncidentResource.class,
			PersonnelResource.class, CanineResource.class, CrewResource.class, EquipmentResource.class,
			AircraftResource.class, GenericResource.class);

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

	/**
	 * Historical external documents without a schema remain supported with a diagnostic warning.
	 */
	@Test
	void missingSchemaVersionRemainsImportableWithWarning() throws Exception {
		Path source = write("{\"incidentContext\":{\"incidentName\":\"Legacy named incident\"},"
				+ "\"form201\":{\"situationSummary\":\"Legacy content\"}}");

		FileIncidentStore.ImportResult imported = FileIncidentStore.readImport(source);

		assertEquals("Legacy named incident", imported.data().getIncidentContext().getIncidentName());
		assertTrue(imported.problems().stream().anyMatch(problem -> problem.path().equals("$.schemaVersion")
				&& problem.severity() == Severity.WARNING));
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
	void rejectsMalformedIncidentIdentityBeforeCreatingAnIncident() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			Path source = write("{\"schemaVersion\":" + AppData.CURRENT_SCHEMA_VERSION
					+ ",\"incidentId\":\"not-a-uuid\",\"form201\":{\"situationSummary\":\"Retained\"}}");
			ImportValidationException failure = assertThrows(ImportValidationException.class,
					() -> store.importFile(source));
			assertTrue(failure.getProblems().stream().anyMatch(problem -> problem.path().equals("$.incidentId")
					&& problem.severity() == Severity.ERROR));
			assertTrue(store.list().isEmpty());
			assertTrue(Files.readString(source).contains("not-a-uuid"));
		}
	}

	@Test
	void rejectsExplicitEmptyRecordIdentitiesBeforeCreatingAnIncident() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			for (String content : List.of(
					"\"tcards\":[{\"resourceId\":\"\",\"personName\":\"Responder\"}]",
					"\"openActionItems\":[{\"id\":null,\"item\":\"Coordinate staging\"}]",
					"\"activityLogs\":[{\"logId\":\"\",\"name\":\"Command\"}]")) {
				Path source = write("{\"schemaVersion\":" + AppData.CURRENT_SCHEMA_VERSION + "," + content + "}");
				ImportValidationException failure = assertThrows(ImportValidationException.class,
						() -> store.importFile(source), content);
				assertTrue(failure.getProblems().stream().anyMatch(problem -> problem.path().contains("Id")
						|| problem.path().endsWith(".id")));
				assertTrue(store.list().isEmpty());
			}
		}
	}

	@Test
	void rejectsMalformedAndDuplicateIdsForEveryAuditedRecordTypeWithoutChangingTheStore() throws Exception {
			ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
			assertEquals(AUDITED_RECORD_IDENTITIES, IncidentDocumentReader.RECORD_IDENTITIES);
			Set<Class<?>> expectedRecordTypes = new HashSet<>(AUDITED_RECORD_IDENTITIES.keySet());
			expectedRecordTypes.removeAll(MODEL_ONLY_POLYMORPHIC_RESOURCE_TYPES);
			try (FileIncidentStore store = new FileIncidentStore(directory)) {
				IncidentId existingId = store.create(new AppData());
				for (boolean duplicate : List.of(false, true)) {
					ObjectNode document = mapper.valueToTree(new AppData());
					document.put("schemaVersion", AppData.CURRENT_SCHEMA_VERSION);
					document.with("incidentContext").put("incidentName", "Imported incident");
					document.with("form201").put("situationSummary", "Imported content");
					Set<Class<?>> coveredTypes = new HashSet<>();
					Set<String> identityPaths = new HashSet<>();
					Set<String> duplicatePaths = new HashSet<>();
					addIdentityFixtures(document, mapper.constructType(AppData.class), "$", duplicate, !duplicate,
							coveredTypes, identityPaths, duplicatePaths, mapper);

					assertEquals(expectedRecordTypes, coveredTypes);
					Path source = write(document.toString());
					ImportValidationException failure = assertThrows(ImportValidationException.class,
							() -> store.importFile(source));
					if (duplicate) {
						assertTrue(failure.getProblems().stream().filter(problem ->
								problem.message().contains("duplicates another record"))
								.map(Problem::path).collect(java.util.stream.Collectors.toSet())
								.containsAll(duplicatePaths), duplicatePaths.toString());
					} else {
						Set<String> errorPaths = failure.getProblems().stream()
								.filter(problem -> problem.severity() == Severity.ERROR).map(Problem::path)
								.collect(java.util.stream.Collectors.toSet());
						assertTrue(errorPaths.containsAll(identityPaths), identityPaths.toString());
					}
					assertEquals(List.of(existingId), store.list().stream().map(IncidentSummary::incidentId).toList());
					assertTrue(Files.readString(source).equals(document.toString()));
				}
		}
	}

	@Test
	void recordIdentityUniquenessIsLimitedToEachContainingCollection() throws Exception {
		String sharedFormId = "00000000-0000-4000-8000-000000000101";
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			String document = """
					{"schemaVersion":%d,"incidentContext":{"incidentName":"Historical reuse"},
					"form201":{"situationSummary":"Search underway"},
					"form204":{"formId":"%s"},
					"additionalForms204":[{"formId":"%s"}],
					"operationalPeriodHistory":[{"recordId":"00000000-0000-4000-8000-000000000102",
						"forms204":[{"formId":"%s"}]}]}
					""".formatted(AppData.CURRENT_SCHEMA_VERSION, sharedFormId, sharedFormId, sharedFormId);
			IncidentId id = store.importFile(write(document)).orElseThrow();
			AppData imported = store.load(id);
			assertEquals(sharedFormId, imported.getForm204().getFormId());
			assertEquals(sharedFormId, imported.getAdditionalForms204().get(0).getFormId());
			assertEquals(sharedFormId,
					imported.getOperationalPeriodHistory().get(0).getForms204().get(0).getFormId());

			String duplicate = """
					{"schemaVersion":%d,"incidentContext":{"incidentName":"Duplicate forms"},
					"form201":{"situationSummary":"Search underway"},
					"additionalForms204":[{"formId":"%s"},{"formId":"%s"}]}
					""".formatted(AppData.CURRENT_SCHEMA_VERSION, sharedFormId, sharedFormId);
			Path source = write(duplicate);
			ImportValidationException failure = assertThrows(ImportValidationException.class,
					() -> store.importFile(source));
			assertTrue(failure.getProblems().stream().anyMatch(problem ->
					problem.path().equals("$.additionalForms204[1].formId")
							&& problem.message().contains("duplicates another record")));
			assertEquals(1, store.list().size());
		}
	}

	/**
	 * Populates all persistent record collections and assigns invalid or duplicate identity values.
	 *
	 * @param node JSON node being traversed
	 * @param type declared Jackson type of the node
	 * @param path JSON path for the node
	 * @param duplicate whether collection records should share an identity
	 * @param malformed whether identities should use invalid UUID values
	 * @param coveredTypes model record types reached
	 * @param identityPaths paths of fields assigned invalid identities
	 * @param duplicatePaths paths expected to report duplicate collection identities
	 * @param mapper mapper used for type introspection
	 */
	private void addIdentityFixtures(JsonNode node, JavaType type, String path, boolean duplicate, boolean malformed,
			Set<Class<?>> coveredTypes, Set<String> identityPaths, Set<String> duplicatePaths, ObjectMapper mapper) {
		if (type.isCollectionLikeType() && node.isArray()) {
			ArrayNode array = (ArrayNode) node;
			JavaType elementType = type.getContentType();
			Set<String> identityFields = AUDITED_RECORD_IDENTITIES.get(elementType.getRawClass());
			if (array.isEmpty() && identityFields != null) {
				int size = duplicate ? 2 : 1;
				for (int index = 0; index < size; index++) {
					array.add(mapper.createObjectNode());
					if (duplicate && index == 1) {
						for (String field : identityFields) {
							duplicatePaths.add(path + "[" + index + "]." + field);
						}
					}
				}
			}
			for (int index = 0; index < array.size(); index++) {
				addIdentityFixtures(array.get(index), elementType, path + "[" + index + "]", duplicate, malformed,
						coveredTypes, identityPaths, duplicatePaths, mapper);
			}
			return;
		}
		if (type.isMapLikeType() && node.isObject()) {
			node.fields().forEachRemaining(entry -> addIdentityFixtures(entry.getValue(), type.getContentType(),
					path + "." + entry.getKey(), duplicate, malformed, coveredTypes, identityPaths, duplicatePaths,
					mapper));
			return;
		}
		if (!node.isObject() || !type.getRawClass().getPackageName().startsWith("org.sarmanagement")) {
			return;
		}
		ObjectNode object = (ObjectNode) node;
		Set<String> identityFields = AUDITED_RECORD_IDENTITIES.get(type.getRawClass());
		if (identityFields != null) {
			coveredTypes.add(type.getRawClass());
			for (String field : identityFields) {
				String identityPath = path + "." + field;
				object.put(field, malformed ? "not-a-uuid" : "00000000-0000-4000-8000-000000000001");
				if (malformed) {
					identityPaths.add(identityPath);
				}
			}
		}
		for (BeanPropertyDefinition property : mapper.getDeserializationConfig().introspect(type).findProperties()) {
			JavaType childType = property.getPrimaryType();
			JsonNode child = object.get(property.getName());
			if ((child == null || child.isNull()) && childType.isCollectionLikeType()
					&& AUDITED_RECORD_IDENTITIES.containsKey(childType.getContentType().getRawClass())) {
				child = mapper.createArrayNode();
				object.set(property.getName(), child);
			} else if ((child == null || child.isNull())
					&& AUDITED_RECORD_IDENTITIES.containsKey(childType.getRawClass())) {
				child = mapper.createObjectNode();
				object.set(property.getName(), child);
			}
			if (child != null && property.couldDeserialize()) {
				addIdentityFixtures(child, childType, path + "." + property.getName(), duplicate,
						malformed, coveredTypes, identityPaths, duplicatePaths, mapper);
			}
		}
	}

		@Test
		void rejectsMalformedAndDuplicateRecordUuidsWithoutChangingTheStore() throws Exception {
			try (FileIncidentStore store = new FileIncidentStore(directory)) {
				org.sarmanagement.icsforms.model.AppData existing = new AppData();
				existing.getIncidentContext().setIncidentName("Existing incident");
				IncidentId existingId = store.create(existing);
				String actionId = "9baf0d0a-1767-46be-a23a-c14320b635df";
				List<String> invalid = List.of(
						"{\"id\":\"not-a-uuid\",\"actions\":\"Search\"}",
						"{\"id\":\"" + actionId + "\",\"actions\":\"Search\"},"
								+ "{\"id\":\"" + actionId + "\",\"actions\":\"Rescue\"}");
				for (String actions : invalid) {
					String json = "{\"schemaVersion\":" + AppData.CURRENT_SCHEMA_VERSION
							+ ",\"incidentContext\":{\"incidentName\":\"Imported incident\"},"
							+ "\"form201\":{\"currentActions\":[" + actions + "]}}";
					Path source = write(json);
					ImportValidationException failure = assertThrows(ImportValidationException.class,
							() -> store.importFile(source));
					assertTrue(failure.getProblems().stream().anyMatch(problem ->
							problem.severity() == Severity.ERROR && problem.path().contains("currentActions")));
					assertEquals(json, Files.readString(source));
					assertEquals(List.of(existingId), store.list().stream().map(IncidentSummary::incidentId).toList());
					assertEquals("Existing incident", store.load(existingId).getIncidentContext().getIncidentName());
				}
			}
		}

		@Test
		void importDoesNotTreatLegacyAssignmentTextAsPersistentRecordIdentity() throws Exception {
			Path source = write("{\"schemaVersion\":" + AppData.CURRENT_SCHEMA_VERSION
					+ ",\"incidentContext\":{\"incidentName\":\"Imported incident\"},"
					+ "\"activityLogs\":[{\"activityLog\":[{\"resourceIdentifier\":\"Legacy unit\","
					+ "\"assignmentId\":\"legacy-assignment-label\",\"notableActivity\":\"Search began\"}]}]}");

			AppData imported = FileIncidentStore.readImport(source).data();

			assertEquals("legacy-assignment-label",
					imported.getActivityLogs().get(0).getActivityLog().get(0).getAssignmentId());
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
