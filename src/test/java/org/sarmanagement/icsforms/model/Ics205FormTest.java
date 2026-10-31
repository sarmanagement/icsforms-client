/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.LocalRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers communications inventory, lossless JSON persistence, and period rollover. */
class Ics205FormTest {
	private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@TempDir
	Path directory;

	@Test
	void legacyAndExplicitNullDocumentsDefaultToEnabledEmptyUnsignedPlans() throws Exception {
		for (String json : List.of("{}", "{\"schemaVersion\":11}",
				"{\"schemaVersion\":11,\"form205\":null}")) {
			AppData data = mapper.readValue(json, AppData.class);
			assertTrue(data.isIcs205Enabled());
			assertTrue(data.getForm205().getChannels().isEmpty());
			assertEquals("", data.getForm205().getPreparedBySignature());
			assertNull(data.getForm205().getDatePrepared());
			assertNull(data.getForm205().getPreparedDateTime());
			assertNotNull(UUID.fromString(data.getForm205().getFormId()));
		}
		assertEquals(16, AppData.CURRENT_SCHEMA_VERSION);
		assertEquals(AppData.CURRENT_SCHEMA_VERSION, new AppData().getSchemaVersion());
		AppData disabled = mapper.readValue("{\"ics205Enabled\":false}", AppData.class);
		assertFalse(disabled.isIcs205Enabled());
	}

	@Test
	void allRadioSettingsRoundTripWithoutRoundingTrimmingOrCoercion() throws Exception {
		AppData source = populatedDocument();
		RadioChannel channel = source.getForm205().getChannels().get(0);
		channel.setZoneGroup(" Zone 01 ");
		channel.setChannelNumber("0007");
		channel.setFunction("Tactical");
		channel.setChannelName("SAR \u03b1");
		channel.setAssignment("Division A");
		channel.setRxFrequency(" 155.123456789000 ");
		channel.setRxBandwidth("12.500000");
		channel.setRxToneNac("D023N / $293");
		channel.setTxFrequency("unverified 159.00000000");
		channel.setTxBandwidth("6.250000");
		channel.setTxToneNac("100.0000");
		channel.setMode("unrecognized-mode");
		channel.setDigitalProtocol("P25 Phase II?");
		channel.setSystem("County trunked system");
		channel.setRemarks("Line one\nLine two, \"raw\"");
		channel.setIncludeOnForm(false);
		channel.setTxPolicy("FUTURE_POLICY");
		channel.getProgrammingMetadata().put("Unknown Vendor Setting", " 0003.0000 ");
		source.setIcs205Enabled(false);

		AppData restored = mapper.readValue(mapper.writeValueAsBytes(source), AppData.class);

		assertEquals(mapper.valueToTree(source.getForm205()), mapper.valueToTree(restored.getForm205()));
		assertEquals(channel.getChannelId(), restored.getForm205().getChannels().get(0).getChannelId());
		assertFalse(restored.getForm205().getChannels().get(0).isIncludeOnForm());
		assertFalse(restored.isIcs205Enabled());
		assertEquals(" 155.123456789000 ", restored.getForm205().getChannels().get(0).getRxFrequency());
		assertEquals(" 0003.0000 ",
				restored.getForm205().getChannels().get(0).getProgrammingMetadata().get("Unknown Vendor Setting"));
	}

	@Test
	void radioDefaultsAndNullSettersDoNotInferTransmitOrSignatures() throws Exception {
		RadioChannel channel = new RadioChannel();
		assertTrue(channel.isIncludeOnForm());
		assertEquals(RadioChannel.TX_POLICY_UNKNOWN, channel.getTxPolicy());
		assertNotNull(UUID.fromString(channel.getChannelId()));
		channel = mapper.readValue("""
				{"zoneGroup":null,"channelNumber":null,"function":null,"channelName":null,
				"assignment":null,"rxFrequency":null,"rxBandwidth":null,"rxToneNac":null,
				"txFrequency":null,"txBandwidth":null,"txToneNac":null,"mode":null,
				"digitalProtocol":null,"system":null,"remarks":null,"txPolicy":null,
				"programmingMetadata":null,"channelId":null}
				""", RadioChannel.class);
		assertTrue(List.of(channel.getZoneGroup(), channel.getChannelNumber(), channel.getFunction(),
				channel.getChannelName(), channel.getAssignment(), channel.getRxFrequency(),
				channel.getRxBandwidth(), channel.getRxToneNac(), channel.getTxFrequency(),
				channel.getTxBandwidth(), channel.getTxToneNac(), channel.getMode(),
				channel.getDigitalProtocol(), channel.getSystem(), channel.getRemarks())
				.stream().allMatch(String::isEmpty));
		assertTrue(channel.getProgrammingMetadata().isEmpty());
		assertEquals(RadioChannel.TX_POLICY_UNKNOWN, channel.getTxPolicy());
		channel.setTxPolicy(" ");
		assertEquals(RadioChannel.TX_POLICY_UNKNOWN, channel.getTxPolicy());
		channel.setRxFrequency("155.000000");
		assertEquals("", channel.getTxFrequency());
		assertEquals(RadioChannel.TX_POLICY_UNKNOWN, channel.getTxPolicy());
		channel.setTxPolicy(RadioChannel.TX_POLICY_RECEIVE_ONLY);
		assertEquals("RECEIVE_ONLY", channel.getTxPolicy());
		channel.setTxPolicy(RadioChannel.TX_POLICY_TRANSMIT);
		assertEquals("TRANSMIT", channel.getTxPolicy());

		Ics205Form form = mapper.readValue("""
				{"specialInstructions":null,"preparedByName":null,"preparedBySignature":null,
				"iapPage":null,"channels":null,"formId":null}
				""", Ics205Form.class);
		assertEquals("", form.getSpecialInstructions());
		assertEquals("", form.getPreparedByName());
		assertEquals("", form.getPreparedBySignature());
		assertEquals("", form.getIapPage());
		assertTrue(form.getChannels().isEmpty());
		assertNotNull(UUID.fromString(form.getFormId()));
		form.setPreparedByName("Entered name only");
		assertEquals("", form.getPreparedBySignature());
	}

	@Test
	void deepCopiesRetainAllValuesWithoutSharingListsChannelsOrMetadata() {
		AppData data = populatedDocument();
		Ics205Form original = data.getForm205();
		Ics205Form copy = original.deepCopy();
		assertEquals(mapper.valueToTree(original), mapper.valueToTree(copy));
		assertNotSame(original.getChannels(), copy.getChannels());
		assertNotSame(original.getChannels().get(0), copy.getChannels().get(0));
		assertNotSame(original.getChannels().get(0).getProgrammingMetadata(),
				copy.getChannels().get(0).getProgrammingMetadata());
		copy.getChannels().get(0).setChannelName("Changed");
		copy.getChannels().get(0).getProgrammingMetadata().put("Mode", "AM");
		assertEquals("Command", original.getChannels().get(0).getChannelName());
		assertEquals("FM", original.getChannels().get(0).getProgrammingMetadata().get("Mode"));
		copy.getChannels().clear();
		assertEquals(1, original.getChannels().size());

		Map<String, String> settings = new LinkedHashMap<>(Map.of("Tone", "100.0"));
		RadioChannel channel = new RadioChannel();
		channel.setProgrammingMetadata(settings);
		settings.clear();
		assertEquals("100.0", channel.getProgrammingMetadata().get("Tone"));
		List<RadioChannel> list = new ArrayList<>(Arrays.asList(null, channel));
		Ics205Form form = new Ics205Form();
		form.setChannels(list);
		list.clear();
		assertEquals(1, form.getChannels().size());
	}

	@Test
	void normalizationRepairsDuplicateRowsAndIsIdempotentAcrossPeriodSnapshots() {
		AppData data = populatedDocument();
		RadioChannel original = data.getForm205().getChannels().get(0);
		String stableId = original.getChannelId();
		data.getForm205().getChannels().add(original);
		data.getForm205().getChannels().add(original.deepCopy());
		data.getForm205().getChannels().add(null);
		AppData.OperationalPeriodRecord history = new AppData.OperationalPeriodRecord();
		history.setForm205(data.getForm205().deepCopy());
		data.getOperationalPeriodHistory().add(history);
		data.getOperationalPeriodHistory().add(null);

		data.normalizeIdentity();
		assertEquals(3, data.getForm205().getChannels().size());
		assertEquals(stableId, original.getChannelId());
		assertEquals(3L, data.getForm205().getChannels().stream().map(RadioChannel::getChannelId).distinct().count());
		assertEquals(stableId, history.getForm205().getChannels().get(0).getChannelId());
		var before = mapper.valueToTree(data);
		data.normalizeIdentity();
		assertEquals(before, mapper.valueToTree(data));
	}

	@Test
	void jsonDuplicateIdentitiesAreRepairedWithoutChangingFirstOccurrence() throws Exception {
		Ics205Form form = mapper.readValue("""
				{"channels":[{"channelId":"stable","rxFrequency":"155.000000"},
				{"channelId":"stable","rxFrequency":"156.000000"},{"channelId":""},null]}
				""", Ics205Form.class);
		assertEquals(3, form.getChannels().size());
		assertEquals("stable", form.getChannels().get(0).getChannelId());
		assertNotEquals("stable", form.getChannels().get(1).getChannelId());
		assertNotNull(UUID.fromString(form.getChannels().get(2).getChannelId()));
		assertEquals("155.000000", form.getChannels().get(0).getRxFrequency());
		assertEquals("156.000000", form.getChannels().get(1).getRxFrequency());
	}

	@Test
	void rolloverArchivesPeriodContextAndCarriesOnlyWhenExplicitlyRequested() throws Exception {
		for (boolean carry : List.of(false, true)) {
			AppData data = populatedDocument();
			Ics205Form previous = data.getForm205();
			previous.getChannels().get(0).setRadioCheckStatus(RadioCheckStatus.SUCCESSFUL);
			previous.getChannels().get(0).setRadioCheckedBy("COML");
			previous.getChannels().get(0).setRadioCheckedAt(LocalDateTime.of(2026, 10, 24, 9, 0));
			String channelId = previous.getChannels().get(0).getChannelId();
			LocalDateTime start = LocalDateTime.of(2026, 10, 24, 8, 0);
			LocalDateTime end = start.plusHours(12);
			data.getIncidentContext().setOperationalPeriodStart(start);
			data.getIncidentContext().setOperationalPeriodEnd(end);
			data.getOrganizationalChart().setIncidentCommanders(new ArrayList<>(List.of("Commander")));
			data.setIcs205Enabled(false);

			data.advanceToNewOperationalPeriod(carry);

			AppData.OperationalPeriodRecord record = data.getOperationalPeriodHistory().get(0);
			Ics205Form archived = record.getForm205();
			assertEquals(start, record.getPeriodStart());
			assertEquals(end, record.getPeriodEnd());
			assertEquals(List.of("Commander"), record.getIncidentCommanders());
			assertEquals(mapper.valueToTree(previous), mapper.valueToTree(archived));
			assertNotSame(previous, archived);
			assertNotSame(previous.getChannels().get(0), archived.getChannels().get(0));
			assertNotSame(previous.getChannels().get(0).getProgrammingMetadata(),
					archived.getChannels().get(0).getProgrammingMetadata());
			assertEquals("Explicit signature", archived.getPreparedBySignature());
			assertFalse(data.isIcs205Enabled());
			Ics205Form current = data.getForm205();
			assertNotEquals(archived.getFormId(), current.getFormId());
			assertEquals("", current.getPreparedBySignature());
			assertEquals("", current.getIapPage());
			assertNull(current.getDatePrepared());
			assertNull(current.getPreparedDateTime());
			if (carry) {
				assertEquals(channelId, current.getChannels().get(0).getChannelId());
				assertEquals(RadioCheckStatus.NOT_RECORDED,
						current.getChannels().get(0).getRadioCheckStatus());
				assertEquals("Dispatch instructions", current.getSpecialInstructions());
				assertEquals("Preparer", current.getPreparedByName());
				assertNotSame(current.getChannels().get(0), archived.getChannels().get(0));
				current.getChannels().get(0).setChannelName("Next period");
				current.getChannels().get(0).getProgrammingMetadata().put("Mode", "AM");
				assertEquals("Command", archived.getChannels().get(0).getChannelName());
				assertEquals("FM", archived.getChannels().get(0).getProgrammingMetadata().get("Mode"));
			} else {
				assertTrue(current.getChannels().isEmpty());
				assertEquals("", current.getSpecialInstructions());
				assertEquals("", current.getPreparedByName());
			}
			previous.getChannels().get(0).setChannelName("Changed old reference");
			assertEquals("Command", archived.getChannels().get(0).getChannelName());
			data.getIncidentContext().setOperationalPeriodStart(end);
			assertEquals(start, record.getPeriodStart());
			AppData restored = mapper.readValue(mapper.writeValueAsBytes(data), AppData.class);
			assertEquals(mapper.valueToTree(archived),
					mapper.valueToTree(restored.getOperationalPeriodHistory().get(0).getForm205()));
		}
	}

	@Test
	void defaultRolloverStartsEmptyAndSuccessiveSnapshotsRemainIndependent() {
		AppData data = populatedDocument();
		data.advanceToNewOperationalPeriod(true);
		data.getForm205().getChannels().get(0).setChannelName("Second period");
		data.advanceToNewOperationalPeriod();
		assertTrue(data.getForm205().getChannels().isEmpty());
		assertEquals("Command", data.getOperationalPeriodHistory().get(0).getForm205()
				.getChannels().get(0).getChannelName());
		assertEquals("Second period", data.getOperationalPeriodHistory().get(1).getForm205()
				.getChannels().get(0).getChannelName());
		assertEquals(data.getOperationalPeriodHistory().get(0).getForm205().getChannels().get(0).getChannelId(),
				data.getOperationalPeriodHistory().get(1).getForm205().getChannels().get(0).getChannelId());
	}

	@Test
	void fileStoreMigratesSchemaElevenAndPersistsInventoryAndHistory() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId legacyId = IncidentId.newId();
			Files.writeString(directory.resolve("incidents").resolve(legacyId.value() + ".json"),
					"{\"schemaVersion\":11,\"incidentId\":\"" + legacyId.value() + "\"}");
			AppData legacy = store.load(legacyId);
			assertEquals(AppData.CURRENT_SCHEMA_VERSION, legacy.getSchemaVersion());
			assertTrue(legacy.isIcs205Enabled());
			assertTrue(legacy.getForm205().getChannels().isEmpty());
			legacy.setForm205(populatedDocument().getForm205());
			legacy.advanceToNewOperationalPeriod(true);
			RadioChannel duplicate = legacy.getForm205().getChannels().get(0).deepCopy();
			legacy.getForm205().getChannels().add(duplicate);
			store.save(legacyId, legacy);

			AppData restored = store.load(legacyId);
			assertEquals(AppData.CURRENT_SCHEMA_VERSION, restored.getSchemaVersion());
			assertEquals(2L, restored.getForm205().getChannels().stream()
					.map(RadioChannel::getChannelId).distinct().count());
			assertEquals("155.123456789000", restored.getForm205().getChannels().get(0).getRxFrequency());
			assertEquals("Explicit signature",
					restored.getOperationalPeriodHistory().get(0).getForm205().getPreparedBySignature());
			assertEquals("", restored.getForm205().getPreparedBySignature());
			assertEquals(AppData.CURRENT_SCHEMA_VERSION, mapper.readTree(directory.resolve("incidents")
					.resolve(legacyId.value() + ".json").toFile()).get("schemaVersion").asInt());
		}
	}

	@Test
	void legacyRepositoryPersistsCommunicationsAndDisabledPreference() {
		LocalRepository repository = new LocalRepository(directory.resolve("workspace.json"));
		AppData data = populatedDocument();
		data.setIcs205Enabled(false);
		repository.save(data);
		AppData restored = repository.loadOrDefault();
		assertEquals(AppData.CURRENT_SCHEMA_VERSION, restored.getSchemaVersion());
		assertFalse(restored.isIcs205Enabled());
		assertEquals(mapper.valueToTree(data.getForm205()), mapper.valueToTree(restored.getForm205()));
	}

	/**
	 * Creates representative signed communications content for lifecycle tests.
	 * @return document containing one precisely specified radio channel
	 */
	private static AppData populatedDocument() {
		AppData data = new AppData();
		Ics205Form form = data.getForm205();
		form.setSpecialInstructions("Dispatch instructions");
		form.setPreparedByName("Preparer");
		form.setPreparedBySignature("Explicit signature");
		form.setDatePrepared(LocalDateTime.of(2026, 10, 24, 7, 30));
		form.setPreparedDateTime(LocalDateTime.of(2026, 10, 24, 7, 45));
		form.setIapPage("7");
		RadioChannel channel = new RadioChannel();
		channel.setChannelName("Command");
		channel.setRxFrequency("155.123456789000");
		channel.getProgrammingMetadata().put("Mode", "FM");
		form.getChannels().add(channel);
		return data;
	}
}
