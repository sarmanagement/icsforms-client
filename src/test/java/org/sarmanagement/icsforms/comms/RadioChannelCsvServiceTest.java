/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.comms;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.Ics205Form;
import org.sarmanagement.icsforms.model.RadioChannel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests complete native plan interchange, strict validation and file encoding. */
class RadioChannelCsvServiceTest {
	private final RadioChannelCsvService service = new RadioChannelCsvService();
	private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@Test
	void preservesEveryFieldAndExactStringsIncludingPrintExcludedDigitalRows() {
		Ics205Form form = new Ics205Form();
		form.setSpecialInstructions("Use \"Alpha\", then\r\nBravo\nCafé 日本語");
		form.setPreparedByName("Preparer");
		form.setPreparedBySignature("Explicit signature");
		form.setDatePrepared(LocalDateTime.of(2026, 10, 25, 1, 2, 3, 123456789));
		form.setPreparedDateTime(LocalDateTime.of(2026, 10, 25, 4, 5));
		form.setIapPage("002");
		RadioChannel channel = new RadioChannel();
		channel.setZoneGroup("Zone, \"one\"");
		channel.setChannelNumber("0001");
		channel.setFunction("Command");
		channel.setChannelName("Search");
		channel.setAssignment("North\nSouth");
		channel.setRxFrequency("155.123456789000");
		channel.setRxBandwidth("12.500");
		channel.setRxToneNac("NAC:$293");
		channel.setTxFrequency("160.123456789000");
		channel.setTxBandwidth("6.250");
		channel.setTxToneNac("ColorCode:01");
		channel.setMode("M");
		channel.setDigitalProtocol("DMR");
		channel.setSystem("Trunked network");
		channel.setRemarks("Radio-only, no form");
		channel.setTxPolicy(RadioChannel.TX_POLICY_RECEIVE_ONLY);
		channel.setIncludeOnForm(false);
		channel.setProgrammingMetadata(new LinkedHashMap<>(Map.of("unknown", "001,\r\n\"x\"", "tone", "023")));
		channel.getProgrammingMetadata().put("unset", null);
		form.setChannels(List.of(channel, new RadioChannel()));

		Ics205Form restored = service.importCsv("\uFEFF" + service.exportCsv(form));

		assertEquals(mapper.valueToTree(form), mapper.valueToTree(restored));
		assertEquals(form.getChannels().get(0).getChannelId(), restored.getChannels().get(0).getChannelId());
		assertTrue(!restored.getChannels().get(0).isIncludeOnForm());
	}

	@Test
	void retainsMetadataForAnEmptyPlanAndAcceptsReorderedHeaders() {
		Ics205Form form = new Ics205Form();
		form.setSpecialInstructions("No channels yet");
		List<List<String>> rows = CsvTable.parse(service.exportCsv(form));
		rows.set(0, rows.get(0).stream().map(header -> header.toUpperCase(Locale.ROOT)).toList());
		StringBuilder reordered = new StringBuilder();
		for (List<String> row : rows) {
			List<String> cells = new ArrayList<>(row);
			Collections.reverse(cells);
			CsvTable.append(reordered, cells);
		}
		Ics205Form restored = service.importCsv(reordered.toString());
		assertEquals(mapper.valueToTree(form), mapper.valueToTree(restored));
		assertEquals(2, rows.size());
	}

	@Test
	void malformedJsonAndValueTypesIdentifyActualRecordAndColumn() {
		Ics205Form form = new Ics205Form();
		form.setChannels(List.of(new RadioChannel()));
		for (String invalid : List.of("\"unclosed", "155.100", "\"155.100\" false")) {
			List<List<String>> rows = CsvTable.parse(service.exportCsv(form));
			rows.get(2).set(rows.get(0).indexOf("rxFrequency"), invalid);
			StringBuilder csv = new StringBuilder();
			CsvTable.append(csv, rows.get(0));
			CsvTable.append(csv, rows.get(1));
			csv.append("\r\n");
			CsvTable.append(csv, rows.get(2));
			IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
					() -> service.importCsv(csv.toString()));
			assertTrue(exception.getMessage().contains("record 4"), exception.getMessage());
			assertTrue(exception.getMessage().contains("column rxFrequency"), exception.getMessage());
		}
	}

	@Test
	void rejectsWrongVersionDuplicateIdsTypesAndUnknownData() {
		Ics205Form form = new Ics205Form();
		form.setChannels(List.of(new RadioChannel()));
		List<List<String>> original = CsvTable.parse(service.exportCsv(form));
		for (String mutation : List.of("version", "duplicate", "type", "id", "record", "metadata")) {
			List<List<String>> rows = original.stream().map(ArrayList::new).map(row -> (List<String>) row).toList();
			int idIndex = rows.get(0).indexOf("channelId");
			switch (mutation) {
				case "version" -> rows.get(1).set(0, "2");
				case "duplicate" -> rows = new ArrayList<>(List.of(rows.get(0), rows.get(1), rows.get(2), rows.get(2)));
				case "type" -> rows.get(2).set(rows.get(0).indexOf("rxFrequency"), "155.100");
				case "id" -> rows.get(2).set(idIndex, "\"\"");
				case "record" -> rows.get(2).set(1, "unknown");
				case "metadata" -> rows.get(1).set(2, "{\"unrecognized\":\"must not disappear\"}");
				default -> throw new AssertionError(mutation);
			}
			StringBuilder csv = new StringBuilder();
			rows.forEach(row -> CsvTable.append(csv, row));
			assertThrows(IllegalArgumentException.class, () -> service.importCsv(csv.toString()), mutation);
		}
		assertThrows(IllegalArgumentException.class, () -> service.importCsv(""));
		assertThrows(IllegalArgumentException.class, () -> service.importCsv("schemaVersion,recordType\n1,form"));
	}

	@Test
	void writesAndReadsUtf8FilesWithoutTemporaryDirectories() throws Exception {
		Path path = Path.of("target", "native-comms-test.csv");
		Files.createDirectories(path.getParent());
		Ics205Form form = new Ics205Form();
		form.setSpecialInstructions("Français 日本語");
		try {
			service.write(path, form);
			assertEquals(mapper.valueToTree(form), mapper.valueToTree(service.read(path)));
		} finally {
			Files.deleteIfExists(path);
		}
	}

	@Test
	void parserHandlesRfc4180AndRejectsMalformedQuotesHeadersAndWidths() {
		assertEquals(List.of(List.of("a", "b"), List.of("one,\"two\"", "line\r\nnext")),
				CsvTable.parse("\uFEFFa,b\r\n\"one,\"\"two\"\"\",\"line\r\nnext\""));
		for (String bad : List.of("a,b\n\"unclosed,x", "a,b\nx\"y,z", "a,b\n\"x\"z,y",
				"a,a\nx,y", "a,A,b\nx,y,z", "a,b\nx", "a,b\nx,y,z", ",b\nx,y")) {
			assertThrows(IllegalArgumentException.class, () -> CsvTable.records(bad, List.of("a", "b")), bad);
		}
	}
}
