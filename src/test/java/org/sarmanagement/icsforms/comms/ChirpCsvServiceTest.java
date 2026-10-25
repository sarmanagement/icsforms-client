/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.comms;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.Ics205Form;
import org.sarmanagement.icsforms.model.RadioChannel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests pinned upstream conventional mappings and explicit unsupported reports. */
class ChirpCsvServiceTest {
	private final ChirpCsvService service = new ChirpCsvService();

	@Test
	void fixtureProvenancePinsTheVerifiedUpstreamSource() throws Exception {
		Properties provenance = new Properties();
		try (InputStream stream = getClass().getResourceAsStream("/comms/chirp-fixture.properties")) {
			provenance.load(stream);
		}
		assertEquals(ChirpCsvService.UPSTREAM_COMMIT, provenance.getProperty("upstream.commit"));
		assertTrue(provenance.getProperty("digital.reason").contains("unverified"));
	}

	@Test
	void acceptsReorderedHeadersAndPreservesUpstreamComments() {
		String csv = "\uFEFF# source notes\r\nmOdE,OFFSET,duplex,FREQUENCY,name,LOCATION,tone,rToneFreq\r\n"
				+ "FM,0,,146.520000,\"Name, \"\"quoted\"\"\",0,Tone,100.0\r\n";
		Ics205Form form = service.importCsv(csv);
		assertEquals("# source notes\r\n", form.getSpecialInstructions());
		assertEquals("Name, \"quoted\"", form.getChannels().get(0).getChannelName());
		assertEquals("146.520000", form.getChannels().get(0).getTxFrequency());
		assertEquals("CTCSS:100.0", form.getChannels().get(0).getTxToneNac());
	}

	@Test
	void unknownTxAndUnsupportedDiagnosticsDoNotMutateNativePlan() {
		Ics205Form form = plan();
		RadioChannel original = form.getChannels().get(0);
		original.setTxPolicy(RadioChannel.TX_POLICY_UNKNOWN);
		original.setMode("M");
		ChirpCsvService.ExportReport report = service.exportCsv(form);
		report.excludedChannels().get(0).setChannelName("Mutated report copy");
		assertEquals("Analog", original.getChannelName());
		assertEquals("M", original.getMode());
		assertEquals(RadioChannel.TX_POLICY_UNKNOWN, original.getTxPolicy());
	}

	@Test
	void invalidRawProgrammingValuesAreExcludedInsteadOfSilentlyDefaulted() {
		for (Map<String, String> metadata : List.of(Map.of("chirp.Power", "turbo"),
				Map.of("chirp.TStep", "fast"), Map.of("chirp.DtcsPolarity", "??"),
				Map.of("chirp.Skip", "unknown"), Map.of("chirp.rToneFreq", "88.55"),
				Map.of("chirp.RxDtcsCode", "999"))) {
			Ics205Form form = plan();
			form.getChannels().get(0).setProgrammingMetadata(metadata);
			assertEquals(1, service.exportCsv(form).excludedChannels().size(), metadata.toString());
		}
	}

	@Test
	void importsAllConventionalDuplexesAtExactHzPrecision() throws Exception {
		Ics205Form form = service.importCsv("\uFEFF" + fixture());
		assertEquals(7, form.getChannels().size());
		assertEquals("A", form.getChannels().get(0).getMode());
		assertEquals("W", form.getChannels().get(0).getRxBandwidth());
		assertEquals("N", form.getChannels().get(2).getRxBandwidth());
		assertEquals("FM", form.getChannels().get(0).getProgrammingMetadata().get("chirp.Mode"));
		assertEquals("146.520000", form.getChannels().get(0).getTxFrequency());
		assertEquals("147.540000", form.getChannels().get(1).getTxFrequency());
		assertEquals("150.250000", form.getChannels().get(2).getTxFrequency());
		assertEquals("435.000000", form.getChannels().get(3).getTxFrequency());
		assertEquals(RadioChannel.TX_POLICY_RECEIVE_ONLY, form.getChannels().get(4).getTxPolicy());
		assertEquals("", form.getChannels().get(4).getTxFrequency());
		assertEquals("154.135956", form.getChannels().get(5).getTxFrequency());
		assertEquals("Quoted, \"name\"\nsecond line", form.getChannels().get(5).getRemarks());
		assertNotEquals(form.getChannels().get(0).getChannelId(), form.getChannels().get(1).getChannelId());
	}

	@Test
	void independentCtcCssDtcsAndCrossPolarityFollowUpstreamSemantics() throws Exception {
		Ics205Form form = service.importCsv(fixture());
		assertEquals("CTCSS:100.0", form.getChannels().get(1).getTxToneNac());
		assertEquals("", form.getChannels().get(1).getRxToneNac());
		assertEquals("CTCSS:123.0", form.getChannels().get(2).getRxToneNac());
		assertEquals("CTCSS:123.0", form.getChannels().get(2).getTxToneNac());
		assertEquals("DTCS:023:N", form.getChannels().get(3).getTxToneNac());
		assertEquals("DTCS:023:R", form.getChannels().get(3).getRxToneNac());
		assertEquals("CTCSS:100.0", form.getChannels().get(5).getTxToneNac());
		assertEquals("DTCS:025:N", form.getChannels().get(5).getRxToneNac());
		assertEquals("DTCS:023:R", form.getChannels().get(6).getTxToneNac());
		assertEquals("DTCS:025:N", form.getChannels().get(6).getRxToneNac());
		assertEquals("025", form.getChannels().get(3).getProgrammingMetadata().get("chirp.RxDtcsCode"));
	}

	@Test
	void conventionalRoundtripPreservesSettingsAndPrintExcludedChannels() throws Exception {
		Ics205Form form = service.importCsv(fixture());
		form.getChannels().get(0).setIncludeOnForm(false);
		ChirpCsvService.ExportReport report = service.exportCsv(form);
		assertTrue(report.excludedChannels().isEmpty(), report.diagnostics().toString());
		assertTrue(report.csv().startsWith("Location,Name,Frequency,Duplex,Offset"));
		assertTrue(report.csv().contains("\r\n"));
		Ics205Form restored = service.importCsv(report.csv());
		assertEquals(7, restored.getChannels().size());
		for (int i = 0; i < form.getChannels().size(); i++) {
			RadioChannel before = form.getChannels().get(i);
			RadioChannel after = restored.getChannels().get(i);
			assertEquals(before.getRxFrequency(), after.getRxFrequency());
			assertEquals(before.getTxFrequency(), after.getTxFrequency());
			assertEquals(before.getTxToneNac(), after.getTxToneNac());
			assertEquals(before.getRxToneNac(), after.getRxToneNac());
			assertEquals(before.getRemarks(), after.getRemarks());
		}
		assertEquals("split", restored.getChannels().get(3).getProgrammingMetadata().get("chirp.Duplex"));
		assertEquals("off", restored.getChannels().get(4).getProgrammingMetadata().get("chirp.Duplex"));
	}

	@Test
	void unknownTxCannotSilentlyBecomeSimplexAndReceiveOnlyNeverTransmits() {
		Ics205Form form = plan();
		form.getChannels().get(0).setTxPolicy(RadioChannel.TX_POLICY_UNKNOWN);
		ChirpCsvService.ExportReport report = service.exportCsv(form);
		assertEquals(1, report.excludedChannels().size());
		assertEquals(1, CsvTable.parse(report.csv()).size());
		assertTrue(report.diagnostics().stream().anyMatch(message -> message.contains("Unknown TX policy")));
		form.getChannels().get(0).setTxPolicy(RadioChannel.TX_POLICY_RECEIVE_ONLY);
		form.getChannels().get(0).setTxFrequency("nonsense must not transmit");
		report = service.exportCsv(form);
		assertEquals(0, report.excludedChannels().size());
		assertEquals("off", CsvTable.records(report.csv(), List.of("Duplex")).get(0).get("Duplex"));
	}

	@Test
	void allDigitalMixedTrunkedAndUnverifiedModesAreRetainedNativelyAndExcluded() {
		Ics205Form form = plan();
		List<RadioChannel> channels = new ArrayList<>();
		for (String mode : List.of("D", "M", "DV", "DN", "DMR", "P25", "NXDN", "AM", "UNKNOWN", "vendor-mode")) {
			RadioChannel channel = form.getChannels().get(0).deepCopy();
			channel.setChannelId(null);
			channel.setMode(mode);
			channel.setChannelName(mode);
			channels.add(channel);
		}
		form.setChannels(channels);
		ChirpCsvService.ExportReport report = service.exportCsv(form);
		assertEquals(channels.size(), report.excludedChannels().size());
		assertEquals(channels.size() + 1, report.diagnostics().size());
		assertEquals(channels.size(), new RadioChannelCsvService().importCsv(
				new RadioChannelCsvService().exportCsv(form)).getChannels().size());
		form = plan();
		form.getChannels().get(0).setSystem("Trunk network");
		assertEquals(1, service.exportCsv(form).excludedChannels().size());
		form.getChannels().get(0).setSystem("");
		form.getChannels().get(0).setDigitalProtocol("DMR");
		assertEquals(1, service.exportCsv(form).excludedChannels().size());
	}

	@Test
	void digitalImportPreservesCallsignAndVendorRawMetadataWithoutPretendingSupport() throws Exception {
		String csv = fixture().replace(",FM,", ",DV,");
		Ics205Form form = service.importCsv(csv);
		assertEquals("D", form.getChannels().get(0).getMode());
		assertEquals("D-STAR", form.getChannels().get(0).getDigitalProtocol());
		assertEquals("DV", form.getChannels().get(0).getProgrammingMetadata().get("chirp.Mode"));
		assertTrue(form.getChannels().get(0).getProgrammingMetadata().containsKey("chirp.URCALL"));
		assertFalse(service.exportCsv(form).excludedChannels().isEmpty());
	}

	@Test
	void rejectsMalformedAndAmbiguousLocationsFrequenciesAndDuplexes() {
		String header = "Location,Name,Frequency,Duplex,Offset,Mode\n";
		for (String row : List.of("0,A,146.1234567,,0,FM", "0,A,146.0,?,0,FM",
				"0,A,0,,0,FM", "0,A,1.0,-,5.0,FM", "-1,A,146.0,,0,FM",
				"999999999999,A,146.0,,0,FM", "0,A,146.0,split,0,FM")) {
			assertThrows(IllegalArgumentException.class, () -> service.importCsv(header + row), row);
		}
		assertThrows(IllegalArgumentException.class,
				() -> service.importCsv(header + "0,A,146.0,,0,FM\n0,B,147.0,,0,FM"));
		assertThrows(IllegalArgumentException.class, () -> service.importCsv("Name,Frequency\nA,146.0"));
		assertEquals("146.0", service.importCsv(header + "0,A,146.0,,,FM").getChannels().get(0).getTxFrequency());
	}

	@Test
	void mapsEveryCrossDirectionAndReportsUnsupportedSquelchWithoutLosingRawValues() throws Exception {
		for (String cross : List.of("Tone->Tone", "DTCS->", "->DTCS", "Tone->DTCS",
				"DTCS->Tone", "->Tone", "DTCS->DTCS", "Tone->")) {
			String csv = fixture().replace("Tone->DTCS", cross);
			Ics205Form form = service.importCsv(csv);
			assertTrue(service.exportCsv(form).excludedChannels().isEmpty(), cross);
		}
		Ics205Form reversed = service.importCsv(fixture().replace(",Tone,100.0", ",TSQL-R,100.0"));
		assertEquals("TSQL-R", reversed.getChannels().get(1).getProgrammingMetadata().get("chirp.Tone"));
		assertEquals(1, service.exportCsv(reversed).excludedChannels().size());
		assertThrows(IllegalArgumentException.class,
				() -> service.importCsv(fixture().replace(",NR,", ",XX,")));
	}

	@Test
	void nativeEditsOverrideRawProgrammingMetadataAndRetainUnknownColumns() throws Exception {
		Ics205Form form = service.importCsv(fixture());
		RadioChannel channel = form.getChannels().get(1);
		channel.setRxToneNac("DTCS:025:R");
		channel.setTxToneNac("CTCSS:123.0");
		channel.setRxFrequency("147.000000");
		channel.setTxFrequency("150.000000");
		channel.getProgrammingMetadata().put("chirp.VendorExtension", "raw, \"023\"");
		form.setChannels(List.of(channel));
		ChirpCsvService.ExportReport report = service.exportCsv(form);
		assertTrue(report.excludedChannels().isEmpty());
		Ics205Form restored = service.importCsv(report.csv());
		assertEquals("150.000000", restored.getChannels().get(0).getTxFrequency());
		assertEquals("147.000000", restored.getChannels().get(0).getRxFrequency());
		assertEquals("DTCS:025:R", restored.getChannels().get(0).getRxToneNac());
		assertEquals("raw, \"023\"", restored.getChannels().get(0).getProgrammingMetadata().get("chirp.VendorExtension"));
	}

	@Test
	void rejectsDuplicateOutputLocationsMismatchedBandwidthAndUnsupportedToneSyntax() {
		Ics205Form form = plan();
		form.getChannels().add(form.getChannels().get(0).deepCopy());
		assertEquals(1, service.exportCsv(form).excludedChannels().size());
		form = plan();
		form.getChannels().get(0).setTxBandwidth("6.25");
		assertEquals(1, service.exportCsv(form).excludedChannels().size());
		form.getChannels().get(0).setTxBandwidth("");
		form.getChannels().get(0).setRxToneNac("NAC:293");
		assertEquals(1, service.exportCsv(form).excludedChannels().size());
		form.getChannels().get(0).setRxToneNac("");
		form.getChannels().get(0).setMode("A");
		assertEquals(1, service.exportCsv(form).excludedChannels().size());
		form.getChannels().get(0).setRxBandwidth("12.5");
		assertEquals(0, service.exportCsv(form).excludedChannels().size());
	}

	@Test
	void rawImportedCellsCannotOverrideLaterPolicyModeProtocolOrToneEdits() throws Exception {
		Ics205Form form = service.importCsv(fixture());
		RadioChannel channel = form.getChannels().get(0);
		form.setChannels(List.of(channel));
		channel.setTxPolicy(RadioChannel.TX_POLICY_RECEIVE_ONLY);
		channel.setMode("NFM");
		channel.setRxBandwidth("12.5");
		channel.setTxBandwidth("12.5");
		channel.setTxToneNac("CTCSS:123.0");
		Map<String, String> exported = CsvTable.records(service.exportCsv(form).csv(),
				List.of("Duplex", "Mode", "Tone", "rToneFreq")).get(0);
		assertEquals("off", exported.get("Duplex"));
		assertEquals("NFM", exported.get("Mode"));
		assertEquals("Tone", exported.get("Tone"));
		assertEquals("123.0", exported.get("rToneFreq"));
		channel.setDigitalProtocol("DMR");
		assertEquals(1, service.exportCsv(form).excludedChannels().size());
		channel.setDigitalProtocol("");
		channel.setMode("UNVERIFIED");
		assertEquals(1, service.exportCsv(form).excludedChannels().size());
		channel.setMode("FM");
		channel.setRxBandwidth("25");
		channel.setTxBandwidth("25");
		channel.setTxPolicy(RadioChannel.TX_POLICY_UNKNOWN);
		assertEquals(1, service.exportCsv(form).excludedChannels().size());
	}

	@Test
	void explicitlyReportsEveryPopulatedNonProgrammingProjectionOmission() {
		Ics205Form form = plan();
		form.setSpecialInstructions("Check in hourly");
		form.setPreparedByName("Planner");
		form.setPreparedBySignature("Signed");
		form.setDatePrepared(java.time.LocalDateTime.of(2026, 10, 25, 0, 0));
		form.setPreparedDateTime(form.getDatePrepared());
		form.setIapPage("5");
		RadioChannel channel = form.getChannels().get(0);
		channel.setZoneGroup("Zone 1");
		channel.setFunction("Command");
		channel.setAssignment("Team A");
		channel.setIncludeOnForm(false);
		channel.setProgrammingMetadata(Map.of("vendor-only", "retained", "chirp.Power", "5W"));
		ChirpCsvService.ExportReport report = service.exportCsv(form);
		assertEquals(0, report.excludedChannels().size());
		String diagnostics = String.join("\n", report.diagnostics());
		for (String field : List.of("specialInstructions", "preparedByName", "preparedBySignature",
				"datePrepared", "preparedDateTime", "iapPage", "zoneGroup", "function", "assignment",
				"includeOnForm=false", "non-CHIRP programmingMetadata")) {
			assertTrue(diagnostics.contains(field), field);
		}
		assertEquals("Team A", channel.getAssignment());
		assertEquals("retained", channel.getProgrammingMetadata().get("vendor-only"));
	}

	@Test
	void neverInventsTransmitPowerForManualOrMissingPowerImports() {
		Ics205Form form = plan();
		form.getChannels().get(0).getProgrammingMetadata().clear();
		ChirpCsvService.ExportReport report = service.exportCsv(form);
		assertEquals(1, report.excludedChannels().size());
		assertTrue(report.diagnostics().stream().anyMatch(message -> message.contains("Unknown CHIRP Power")));
		assertFalse(report.csv().contains("50W"));
		assertEquals(0, form.getChannels().get(0).getProgrammingMetadata().size());
		form.getChannels().get(0).getProgrammingMetadata().put("chirp.Power", "");
		assertEquals(1, service.exportCsv(form).excludedChannels().size());
		form.getChannels().get(0).getProgrammingMetadata().put("chirp.Power", "0W");
		assertEquals(1, service.exportCsv(form).excludedChannels().size());
		form.getChannels().get(0).getProgrammingMetadata().put("chirp.Power", "2.5W");
		Map<String, String> row = CsvTable.records(service.exportCsv(form).csv(), List.of("Power")).get(0);
		assertEquals("2.5W", row.get("Power"));
		Ics205Form imported = service.importCsv("Location,Name,Frequency,Duplex,Offset,Mode\n0,A,146.5,,0,FM");
		assertFalse(imported.getChannels().get(0).getProgrammingMetadata().containsKey("chirp.Power"));
		assertEquals(1, service.exportCsv(imported).excludedChannels().size());
	}

	@Test
	void exportsCanonicalIcsAnalogModeWithNarrowWideAndDecimalAliases() {
		for (String alias : List.of("N", "n", "12.5", "12.500", "W", "w", "25", "25.000")) {
			Ics205Form form = plan();
			RadioChannel channel = form.getChannels().get(0);
			channel.setMode("A");
			channel.setRxBandwidth(alias);
			channel.setTxBandwidth(alias);
			ChirpCsvService.ExportReport report = service.exportCsv(form);
			assertTrue(report.excludedChannels().isEmpty(), alias + report.diagnostics());
			String mode = CsvTable.records(report.csv(), List.of("Mode")).get(0).get("Mode");
			assertEquals(List.of("N", "n", "12.5", "12.500").contains(alias) ? "NFM" : "FM", mode);
		}
	}

	@Test
	void mapsVerifiedDigitalProtocolsSeparatelyWithoutReclassifyingUnknownModes() {
		for (String mode : List.of("DV", "DN", "DMR", "P25", "NXDN", "UNKNOWN")) {
			Ics205Form imported = service.importCsv("Location,Name,Frequency,Duplex,Offset,Mode\n0,A,146.5,,0," + mode);
			RadioChannel channel = imported.getChannels().get(0);
			assertEquals("UNKNOWN".equals(mode) ? "UNKNOWN" : "D", channel.getMode());
			assertEquals("DV".equals(mode) ? "D-STAR" : "UNKNOWN".equals(mode) ? "" : mode,
					channel.getDigitalProtocol());
			assertEquals(mode, channel.getProgrammingMetadata().get("chirp.Mode"));
			assertEquals(1, service.exportCsv(imported).excludedChannels().size());
		}
		Ics205Form invalidAnalog = service.importCsv("Location,Name,Frequency,Duplex,Offset,Mode\n0,A,146.5,,0,A");
		RadioChannel channel = invalidAnalog.getChannels().get(0);
		channel.setRxBandwidth("N");
		channel.getProgrammingMetadata().put("chirp.Power", "5W");
		assertEquals("CHIRP:A", channel.getMode());
		assertEquals("A", channel.getProgrammingMetadata().get("chirp.Mode"));
		assertEquals(1, service.exportCsv(invalidAnalog).excludedChannels().size());
	}

	@Test
	void receiveOnlyContradictoryTransmitSettingsProduceExplicitPerChannelWarnings() {
		Ics205Form form = plan();
		RadioChannel channel = form.getChannels().get(0);
		channel.setTxPolicy(RadioChannel.TX_POLICY_RECEIVE_ONLY);
		channel.setTxFrequency("147.123456");
		channel.setTxToneNac("DTCS:023:R");
		channel.setTxBandwidth("W");
		ChirpCsvService.ExportReport report = service.exportCsv(form);
		assertTrue(report.excludedChannels().isEmpty(), report.diagnostics().toString());
		String warning = report.diagnostics().stream()
				.filter(message -> message.contains("receive-only Duplex=off")).findFirst().orElseThrow();
		assertTrue(warning.contains(channel.getChannelId()));
		assertTrue(warning.contains("txFrequency"));
		assertTrue(warning.contains("txToneNac"));
		assertTrue(warning.contains("txBandwidth"));
		assertEquals("147.123456", channel.getTxFrequency());
		assertEquals("DTCS:023:R", channel.getTxToneNac());
		assertEquals("W", channel.getTxBandwidth());
		assertEquals("off", CsvTable.records(report.csv(), List.of("Duplex")).get(0).get("Duplex"));
	}

	@Test
	void digitalRoutingMetadataIsNeverProjectedAsAnalogCommentsOrSettings() {
		for (String key : List.of("chirp.URCALL", "chirp.RPT1CALL", "chirp.RPT2CALL", "chirp.DVCODE",
				"URCALL", "RPT1", "RPT2", "DVCODE", "chirp.urcall")) {
			Ics205Form form = plan();
			RadioChannel channel = form.getChannels().get(0);
			channel.getProgrammingMetadata().put(key, "001");
			ChirpCsvService.ExportReport report = service.exportCsv(form);
			assertEquals(1, report.excludedChannels().size(), key);
			assertTrue(report.diagnostics().stream()
					.anyMatch(message -> message.contains("digital routing field " + key)), key);
			assertEquals(1, CsvTable.parse(report.csv()).size());
			RadioChannelCsvService nativeService = new RadioChannelCsvService();
			assertEquals("001", nativeService.importCsv(nativeService.exportCsv(form))
					.getChannels().get(0).getProgrammingMetadata().get(key));
		}
		Ics205Form form = plan();
		form.getChannels().get(0).setSystem("Named trunking system");
		assertTrue(service.exportCsv(form).diagnostics().stream()
				.anyMatch(message -> message.contains("Unsupported system 'Named trunking system'")));
	}

	/**
	 * Loads the hand-authored, source-verified conventional fixture.
	 * @return fixture text.
	 * @throws IOException when reading fails.
	 */
	private String fixture() throws IOException {
		try (InputStream stream = getClass().getResourceAsStream("/comms/chirp-conventional.csv")) {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/**
	 * Creates a minimal explicitly authorized conventional channel.
	 * @return native plan.
	 */
	private static Ics205Form plan() {
		Ics205Form form = new Ics205Form();
		RadioChannel channel = new RadioChannel();
		channel.setChannelNumber("0");
		channel.setChannelName("Analog");
		channel.setMode("FM");
		channel.setRxFrequency("146.520000");
		channel.setTxFrequency("146.520000");
		channel.setTxPolicy(RadioChannel.TX_POLICY_TRANSMIT);
		channel.setProgrammingMetadata(Map.of("chirp.Power", "5W"));
		form.setChannels(List.of(channel));
		return form;
	}
}
