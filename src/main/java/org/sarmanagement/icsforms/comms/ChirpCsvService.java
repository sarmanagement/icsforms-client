/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.comms;

import org.sarmanagement.icsforms.model.Ics205Form;
import org.sarmanagement.icsforms.model.RadioChannel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

/**
 * Conservative conventional analog CHIRP interchange, verified against upstream
 * generic_csv.py ATTR_MAP and chirp_common.py Memory.CSV_FORMAT, split_tone_encode
 * and split_tone_decode at commit 21e42e4518c167548af90ed7f9edf75e516c491a.
 * DVMemory has a distinct serializer and generic CSV does not map its call fields;
 * DV/DN and other digital/image workflows are therefore deliberately not exported.
 * No radio-image drivers or Python runtime are used.
 */
public class ChirpCsvService {
	/** Pinned upstream source used to verify CSV semantics and test fixture design. */
	public static final String UPSTREAM_COMMIT = "21e42e4518c167548af90ed7f9edf75e516c491a";
	private static final String PREFIX = "chirp.";
	private static final List<String> HEADERS = List.of("Location", "Name", "Frequency", "Duplex", "Offset",
			"Tone", "rToneFreq", "cToneFreq", "DtcsCode", "DtcsPolarity", "RxDtcsCode", "CrossMode",
			"Mode", "TStep", "Skip", "Power", "Comment", "URCALL", "RPT1CALL", "RPT2CALL", "DVCODE");
	private static final Set<String> CROSS = Set.of("Tone->Tone", "DTCS->", "->DTCS", "Tone->DTCS",
			"DTCS->Tone", "->Tone", "DTCS->DTCS", "Tone->");

	/**
	 * A projection requiring explicit user confirmation of exclusions before saving.
	 * Native plan data is never removed or changed by this report.
	 * @param csv compatible conventional channel projection.
	 * @param diagnostics export warnings and exclusion reasons.
	 * @param excludedChannels channels not representable safely.
	 */
	public record ExportReport(String csv, List<String> diagnostics, List<RadioChannel> excludedChannels) {
	}

	/**
	 * Imports all channels, including unsupported modes, preserving original cells.
	 * CHIRP file comments are retained in the plan's special instructions.
	 * FM/NFM become ICS A with W/N bandwidth; recognized digital protocols become D.
	 * Original CHIRP Mode remains in programming metadata.
	 * @param csv CHIRP generic CSV text.
	 * @return detached plan with explicit TX policy derived from Duplex.
	 */
	public Ics205Form importCsv(String csv) {
		List<List<String>> parsed = CsvTable.parse(csv);
		List<List<String>> comments = parsed.stream().filter(row -> !row.isEmpty() && row.get(0).startsWith("#")).toList();
		List<Map<String, String>> records = CsvTable.records(parsed.stream()
				.filter(row -> row.isEmpty() || !row.get(0).startsWith("#")).toList(),
				List.of("Location", "Name", "Frequency", "Duplex", "Offset", "Mode"), HEADERS);
		Ics205Form form = new Ics205Form();
		if (!comments.isEmpty()) {
			StringBuilder preamble = new StringBuilder();
			comments.forEach(row -> CsvTable.append(preamble, row));
			form.setSpecialInstructions(preamble.toString());
		}
		List<RadioChannel> channels = new ArrayList<>();
		Set<Integer> locations = new HashSet<>();
		for (Map<String, String> row : records) {
			try {
				int location = location(row.get("Location"));
				if (!locations.add(location)) {
					throw new IllegalArgumentException("Duplicate Location " + location);
				}
				RadioChannel channel = new RadioChannel();
				channel.setChannelNumber(row.get("Location"));
				channel.setChannelName(row.get("Name"));
				channel.setRxFrequency(row.get("Frequency"));
				BigDecimal rx = frequency(row.get("Frequency"), false);
				BigDecimal offset = frequency(row.get("Offset"), true);
				String duplex = row.get("Duplex");
				BigDecimal tx = switch (duplex) {
					case "" -> rx;
					case "+" -> rx.add(offset);
					case "-" -> rx.subtract(offset);
					case "split" -> offset;
					case "off" -> null;
					default -> throw new IllegalArgumentException("Unsupported Duplex: " + duplex);
				};
				if (tx != null && tx.signum() <= 0) {
					throw new IllegalArgumentException("TX frequency must be positive.");
				}
				channel.setTxPolicy(tx == null ? RadioChannel.TX_POLICY_RECEIVE_ONLY : RadioChannel.TX_POLICY_TRANSMIT);
				channel.setTxFrequency(tx == null ? "" : tx.toPlainString());
				String importedMode = row.get("Mode");
				if (Set.of("FM", "NFM").contains(importedMode)) {
					channel.setMode("A");
					channel.setRxBandwidth("NFM".equals(importedMode) ? "N" : "W");
				} else if (Set.of("DV", "DN", "DMR", "P25", "NXDN").contains(importedMode)) {
					channel.setMode("D");
					channel.setDigitalProtocol("DV".equals(importedMode) ? "D-STAR" : importedMode);
				} else {
					channel.setMode("A".equals(importedMode) ? "CHIRP:A" : importedMode);
				}
				channel.setTxBandwidth(tx == null ? "" : channel.getRxBandwidth());
				channel.setRemarks(row.getOrDefault("Comment", ""));
				Map<String, String> metadata = new LinkedHashMap<>();
				row.forEach((key, value) -> metadata.put(PREFIX + key, value));
				metadata.put("icsforms.chirpSourceCommit", UPSTREAM_COMMIT);
				channel.setProgrammingMetadata(metadata);
				decodeTones(channel, row);
				channels.add(channel);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("CHIRP record " + (channels.size() + 2) + ": " + e.getMessage(), e);
			}
		}
		form.setChannels(channels);
		return form;
	}

	/**
	 * Exports safe analog rows, reporting every excluded channel explicitly.
	 * UNKNOWN transmit policy is never inferred as simplex or permission to transmit.
	 * Explicit operator-supplied chirp.Power metadata is required; power is never invented.
	 * @param form native source plan; print selection does not limit export.
	 * @return CSV plus warnings and detached excluded channel copies.
	 */
	public ExportReport exportCsv(Ics205Form form) {
		if (form == null) {
			throw new IllegalArgumentException("Plan is required.");
		}
		if (form.getChannels().stream().anyMatch(channel -> channel == null)) {
			throw new IllegalArgumentException("Null channel rows cannot be exported safely.");
		}
		List<String> diagnostics = new ArrayList<>();
		diagnostics.add("CHIRP is a programming projection, not a lossless plan backup. "
				+ "Use native CSV to retain ICS assignments, form metadata, IDs and print selection. "
				+ "No hardware suitability, legal authorization, or radio compatibility is implied. "
				+ "CHIRP may substitute 50W when Power is unspecified; review power against the target "
				+ "radio image before transmitting. This export excludes channels without explicit Power.");
		List<String> omittedFormFields = new ArrayList<>();
		if (!form.getSpecialInstructions().isEmpty()) {
			omittedFormFields.add("specialInstructions");
		}
		if (!form.getPreparedByName().isEmpty()) {
			omittedFormFields.add("preparedByName");
		}
		if (!form.getPreparedBySignature().isEmpty()) {
			omittedFormFields.add("preparedBySignature");
		}
		if (form.getDatePrepared() != null) {
			omittedFormFields.add("datePrepared");
		}
		if (form.getPreparedDateTime() != null) {
			omittedFormFields.add("preparedDateTime");
		}
		if (!form.getIapPage().isEmpty()) {
			omittedFormFields.add("iapPage");
		}
		if (!omittedFormFields.isEmpty()) {
			diagnostics.add("CHIRP omits form fields " + String.join(", ", omittedFormFields)
					+ "; values remain in the native plan.");
		}
		List<RadioChannel> excluded = new ArrayList<>();
		List<Map<String, String>> rows = new ArrayList<>();
		List<String> headers = new ArrayList<>(HEADERS);
		Set<Integer> used = new HashSet<>();
		for (RadioChannel channel : form.deepCopy().getChannels()) {
			try {
				Map<String, String> row = encode(channel);
				int number = location(row.get("Location"));
				if (!used.add(number)) {
					throw new IllegalArgumentException("Duplicate Location " + number);
				}
				for (String header : row.keySet()) {
					if (!headers.contains(header)) {
						headers.add(header);
					}
				}
				rows.add(row);
				List<String> omitted = new ArrayList<>();
				if (!channel.getZoneGroup().isEmpty()) {
					omitted.add("zoneGroup");
				}
				if (!channel.getFunction().isEmpty()) {
					omitted.add("function");
				}
				if (!channel.getAssignment().isEmpty()) {
					omitted.add("assignment");
				}
				if (!channel.isIncludeOnForm()) {
					omitted.add("includeOnForm=false");
				}
				if (channel.getProgrammingMetadata().keySet().stream()
						.anyMatch(key -> !key.startsWith(PREFIX) && !"icsforms.chirpSourceCommit".equals(key))) {
					omitted.add("non-CHIRP programmingMetadata");
				}
				if (!omitted.isEmpty()) {
					diagnostics.add("Channel " + channel.getChannelName() + " [" + channel.getChannelId()
							+ "]: CHIRP omits " + String.join(", ", omitted) + "; values remain in the native plan.");
				}
			} catch (IllegalArgumentException e) {
				excluded.add(channel);
				diagnostics.add("Excluded " + channel.getChannelName() + " [" + channel.getChannelId() + "]: " + e.getMessage());
			}
		}
		StringBuilder csv = new StringBuilder();
		CsvTable.append(csv, headers);
		for (Map<String, String> row : rows) {
			CsvTable.append(csv, headers.stream().map(header -> row.getOrDefault(header, "")).toList());
		}
		return new ExportReport(csv.toString(), List.copyOf(diagnostics), List.copyOf(excluded));
	}

	/**
	 * Maps one conventional channel without changing the native source.
	 * @param channel source channel.
	 * @return CHIRP cells.
	 */
	private static Map<String, String> encode(RadioChannel channel) {
		if (!Set.of(RadioChannel.TX_POLICY_TRANSMIT, RadioChannel.TX_POLICY_RECEIVE_ONLY).contains(channel.getTxPolicy())) {
			throw new IllegalArgumentException("Unknown TX policy; explicitly choose transmit or receive-only.");
		}
		if (RadioChannel.TX_POLICY_RECEIVE_ONLY.equals(channel.getTxPolicy())) {
			List<String> conflicts = new ArrayList<>();
			if (!channel.getTxFrequency().isEmpty()) {
				conflicts.add("txFrequency");
			}
			if (!channel.getTxToneNac().isEmpty()) {
				conflicts.add("txToneNac");
			}
			if (!channel.getTxBandwidth().isEmpty()) {
				conflicts.add("txBandwidth");
			}
			if (!conflicts.isEmpty()) {
				throw new IllegalArgumentException("receive-only policy conflicts with populated "
						+ String.join(", ", conflicts) + "; clear those fields or correct TX policy. Native values are unchanged.");
			}
		}
		String mode = channel.getMode();
		String rxBandwidth = bandwidth(channel.getRxBandwidth());
		String txBandwidth = bandwidth(channel.getTxBandwidth());
		if ("A".equals(mode)) {
			mode = switch (rxBandwidth) {
				case "N" -> "NFM";
				case "W" -> "FM";
				default -> throw new IllegalArgumentException("Analog A requires explicit N/12.5 or W/25 kHz bandwidth.");
			};
		}
		if (!Set.of("FM", "NFM").contains(mode)) {
			throw new IllegalArgumentException("Unsupported digital, mixed, or unverified mode '" + mode + "'; native data retained.");
		}
		if (!channel.getDigitalProtocol().isBlank()) {
			throw new IllegalArgumentException("Unsupported digitalProtocol '" + channel.getDigitalProtocol()
					+ "'; native data retained.");
		}
		if (!channel.getSystem().isBlank()) {
			throw new IllegalArgumentException("Unsupported system '" + channel.getSystem()
					+ "'; system/trunking configuration remains native-only.");
		}
		rejectRoutingMetadata(channel);
		String expectedBandwidth = "FM".equals(mode) ? "W" : "N";
		if ((!rxBandwidth.isEmpty() && !expectedBandwidth.equals(rxBandwidth))
				|| (!txBandwidth.isEmpty() && !expectedBandwidth.equals(txBandwidth))) {
			throw new IllegalArgumentException("Mode and independent RX/TX bandwidths are not representable in CHIRP.");
		}
		Map<String, String> row = defaults();
		channel.getProgrammingMetadata().forEach((key, value) -> {
			if (key == null) {
				throw new IllegalArgumentException("Null programming metadata keys cannot be exported safely.");
			}
			if (key.startsWith(PREFIX)) {
				if (value == null) {
					throw new IllegalArgumentException("Null preserved CHIRP cells cannot be exported safely.");
				}
				row.put(key.substring(PREFIX.length()), value);
			}
		});
		row.put("Location", channel.getChannelNumber());
		row.put("Name", channel.getChannelName());
		row.put("Frequency", channel.getRxFrequency());
		row.put("Mode", mode);
		row.put("Comment", channel.getRemarks());
		BigDecimal rx = frequency(channel.getRxFrequency(), false);
		if (RadioChannel.TX_POLICY_RECEIVE_ONLY.equals(channel.getTxPolicy())) {
			row.put("Duplex", "off");
			row.put("Offset", "0.000000");
		} else {
			BigDecimal tx = frequency(channel.getTxFrequency(), false);
			BigDecimal delta = tx.subtract(rx);
			String originalDuplex = row.get("Duplex");
			if ("split".equals(originalDuplex) && delta.signum() != 0) {
				row.put("Duplex", "split");
				row.put("Offset", channel.getTxFrequency());
			} else {
				row.put("Duplex", delta.signum() == 0 ? "" : delta.signum() > 0 ? "+" : "-");
				row.put("Offset", delta.abs().toPlainString());
			}
		}
		if (Set.of("TSQL-R", "DTCS-R").contains(row.get("Tone"))) {
			throw new IllegalArgumentException("Reverse squelch tone modes are retained only in native CSV.");
		}
		encodeTones(row, channel.getTxToneNac(), channel.getRxToneNac());
		validateOptional(row);
		return row;
	}

	/**
	 * Rejects digital routing settings on conventional analog projections.
	 * @param channel source channel.
	 */
	private static void rejectRoutingMetadata(RadioChannel channel) {
		Set<String> routingFields = Set.of("URCALL", "RPT1CALL", "RPT2CALL", "DVCODE", "RPT1", "RPT2");
		for (Map.Entry<String, String> entry : channel.getProgrammingMetadata().entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
				continue;
			}
			String key = entry.getKey().toUpperCase(Locale.ROOT);
			String field = key.startsWith("CHIRP.") ? key.substring("CHIRP.".length()) : key;
			if (routingFields.contains(field)) {
				throw new IllegalArgumentException("Nonblank digital routing field " + entry.getKey()
						+ " cannot be projected as analog programming data; retained in native metadata.");
			}
		}
	}

	/**
	 * Creates upstream-compatible defaults for nonessential programming fields.
	 * @return default cell map.
	 */
	private static Map<String, String> defaults() {
		Map<String, String> row = new LinkedHashMap<>();
		HEADERS.forEach(header -> row.put(header, ""));
		row.put("rToneFreq", "88.5");
		row.put("cToneFreq", "88.5");
		row.put("DtcsCode", "023");
		row.put("RxDtcsCode", "023");
		row.put("DtcsPolarity", "NN");
		row.put("CrossMode", "Tone->Tone");
		row.put("TStep", "5.00");
		return row;
	}

	/**
	 * Resolves conventional narrow/wide aliases without changing native text.
	 * @param text N/W or a supported decimal kHz value.
	 * @return N, W, or blank when unset.
	 */
	private static String bandwidth(String text) {
		String value = text.trim().toUpperCase(Locale.ROOT);
		if (Set.of("", "N", "W").contains(value)) {
			return value;
		}
		try {
			BigDecimal number = new BigDecimal(value);
			if (number.compareTo(new BigDecimal("12.5")) == 0) {
				return "N";
			}
			if (number.compareTo(new BigDecimal("25")) == 0) {
				return "W";
			}
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Unsupported bandwidth: " + text, e);
		}
		throw new IllegalArgumentException("Unsupported bandwidth: " + text);
	}

	/**
	 * Decodes independent analog squelch values, retaining unsupported raw modes.
	 * @param channel destination channel.
	 * @param input original cells.
	 */
	private static void decodeTones(RadioChannel channel, Map<String, String> input) {
		Map<String, String> row = defaults();
		row.putAll(input);
		if (input.containsKey("rToneFreq") && !input.containsKey("cToneFreq")) {
			row.put("cToneFreq", input.get("rToneFreq"));
		} else if (input.containsKey("cToneFreq") && !input.containsKey("rToneFreq")) {
			row.put("rToneFreq", input.get("cToneFreq"));
		}
		String tone = row.get("Tone");
		String tx = "";
		String rx = "";
		switch (tone) {
			case "" -> { }
			case "Tone" -> tx = decodedTone(input, row, "Tone", "rToneFreq", "N");
			case "TSQL" -> {
				tx = decodedTone(input, row, "Tone", "cToneFreq", "N");
				rx = tx;
			}
			case "DTCS" -> {
				String polarity = polarity(row.get("DtcsPolarity"));
				tx = decodedTone(input, row, "DTCS", "DtcsCode", polarity.substring(0, 1));
				rx = decodedTone(input, row, "DTCS", "DtcsCode", polarity.substring(1));
			}
			case "Cross" -> {
				String cross = row.get("CrossMode");
				if (!CROSS.contains(cross)) {
					throw new IllegalArgumentException("Invalid CrossMode " + cross);
				}
				int arrow = cross.indexOf("->");
				String txMode = cross.substring(0, arrow);
				String rxMode = cross.substring(arrow + 2);
				String polarity = polarity(row.get("DtcsPolarity"));
				tx = decodedTone(input, row, txMode, "Tone".equals(txMode) ? "rToneFreq" : "DtcsCode", polarity.substring(0, 1));
				rx = decodedTone(input, row, rxMode, "Tone".equals(rxMode) ? "cToneFreq" : "RxDtcsCode", polarity.substring(1));
			}
			case "TSQL-R", "DTCS-R" -> {
				tx = "RAW:" + tone;
				rx = tx;
			}
			default -> throw new IllegalArgumentException("Unknown Tone mode " + tone);
		}
		channel.setTxToneNac(tx);
		channel.setRxToneNac(rx);
	}

	/**
	 * Decodes an active tone only when supplied by an explicit compatible header.
	 * Single CTCSS headers may supply both sides, matching upstream _clean_tmode;
	 * missing active CTCSS/DTCS values never acquire inactive-field defaults.
	 * @param input original imported cells.
	 * @param effective cells after upstream-compatible CTCSS header copying.
	 * @param mode tone type, or blank for no tone.
	 * @param field active numeric tone field.
	 * @param polarity DTCS polarity.
	 * @return canonical native tone text.
	 */
	private static String decodedTone(Map<String, String> input, Map<String, String> effective,
			String mode, String field, String polarity) {
		if (mode.isEmpty()) {
			return "";
		}
		boolean supplied = input.containsKey(field)
				|| (Set.of("rToneFreq", "cToneFreq").contains(field)
						&& (input.containsKey("rToneFreq") || input.containsKey("cToneFreq")));
		if (!supplied || effective.get(field).isBlank()) {
			throw new IllegalArgumentException("Missing active tone value in column " + field + ".");
		}
		return tone(mode, effective.get(field), polarity);
	}

	/**
	 * Formats canonical tone text with independent DTCS polarity.
	 * @param mode tone type or blank.
	 * @param value numeric tone value.
	 * @param polarity N or R.
	 * @return canonical native tone text.
	 */
	private static String tone(String mode, String value, String polarity) {
		if (mode.isEmpty()) {
			return "";
		}
		if ("Tone".equals(mode)) {
			BigDecimal number = new BigDecimal(value);
			if (number.compareTo(new BigDecimal("50")) <= 0 || number.compareTo(new BigDecimal("300")) >= 0) {
				throw new IllegalArgumentException("CTCSS must be between 50 and 300 Hz.");
			}
			if (number.stripTrailingZeros().scale() > 1) {
				throw new IllegalArgumentException("CHIRP CTCSS serialization supports tenths of a Hz only.");
			}
			return "CTCSS:" + value;
		}
		if (!value.matches("[0-7]{1,3}")) {
			throw new IllegalArgumentException("DTCS must be one to three octal digits.");
		}
		return "DTCS:" + "0".repeat(3 - value.length()) + value + ":" + polarity;
	}

	/**
	 * Encodes independent tone and squelch fields using upstream cross semantics.
	 * @param row output cells.
	 * @param tx native transmit tone.
	 * @param rx native receive tone.
	 */
	private static void encodeTones(Map<String, String> row, String tx, String rx) {
		String txMode = encodeTone(row, tx, true);
		String rxMode = encodeTone(row, rx, false);
		String cross = txMode + "->" + rxMode;
		if (txMode.isEmpty() && rxMode.isEmpty()) {
			row.put("Tone", "");
		} else if ("Tone".equals(txMode) && rxMode.isEmpty()) {
			row.put("Tone", "Tone");
		} else if ("Tone".equals(txMode) && "Tone".equals(rxMode)
				&& new BigDecimal(row.get("rToneFreq")).compareTo(new BigDecimal(row.get("cToneFreq"))) == 0) {
			row.put("Tone", "TSQL");
		} else if ("DTCS".equals(txMode) && "DTCS".equals(rxMode)
				&& row.get("DtcsCode").equals(row.get("RxDtcsCode"))) {
			row.put("Tone", "DTCS");
		} else if (CROSS.contains(cross)) {
			row.put("Tone", "Cross");
			row.put("CrossMode", cross);
		} else {
			throw new IllegalArgumentException("Unsupported independent tone configuration.");
		}
	}

	/**
	 * Parses a native squelch value without guessing unsupported NAC/code syntax.
	 * @param row destination programming cells.
	 * @param text blank, numeric CTCSS, CTCSS:value or DTCS:code:polarity.
	 * @param transmit true for transmit.
	 * @return upstream tone type or blank.
	 */
	private static String encodeTone(Map<String, String> row, String text, boolean transmit) {
		if (text == null || text.isBlank()) {
			return "";
		}
		if (text.matches("(CTCSS:)?[0-9]+(\\.[0-9]+)?")) {
			String value = text.startsWith("CTCSS:") ? text.substring(6) : text;
			tone("Tone", value, "N");
			row.put(transmit ? "rToneFreq" : "cToneFreq", value);
			return "Tone";
		}
		if (text.matches("DTCS:[0-7]{3}:[NR]")) {
			String value = text.substring(5, 8);
			String pol = polarity(row.get("DtcsPolarity"));
			row.put(transmit ? "DtcsCode" : "RxDtcsCode", value);
			row.put("DtcsPolarity", transmit ? text.substring(9) + pol.substring(1) : pol.substring(0, 1) + text.substring(9));
			return "DTCS";
		}
		throw new IllegalArgumentException("Unverified tone/NAC syntax: " + text);
	}

	/**
	 * Validates DTCS polarity.
	 * @param value polarity pair.
	 * @return validated pair.
	 */
	private static String polarity(String value) {
		if (!Set.of("NN", "NR", "RN", "RR").contains(value)) {
			throw new IllegalArgumentException("Invalid DtcsPolarity " + value);
		}
		return value;
	}

	/**
	 * Parses MHz exactly at CHIRP's integral-Hz precision.
	 * @param text decimal MHz.
	 * @param zeroAllowed whether zero is allowed.
	 * @return exact frequency.
	 */
	private static BigDecimal frequency(String text, boolean zeroAllowed) {
		if (zeroAllowed && "".equals(text)) {
			return BigDecimal.ZERO;
		}
		if (text == null || !text.matches("[0-9]+(\\.[0-9]{1,6})?")) {
			throw new IllegalArgumentException("Frequency must be decimal MHz with at most six decimal places: " + text);
		}
		BigDecimal value = new BigDecimal(text);
		if (value.signum() < 0 || (!zeroAllowed && value.signum() == 0)) {
			throw new IllegalArgumentException("Frequency must be positive.");
		}
		return value;
	}

	/**
	 * Parses a nonnegative memory location.
	 * @param text memory location.
	 * @return integer memory location.
	 */
	private static int location(String text) {
		if (text == null || !text.matches("[0-9]+")) {
			throw new IllegalArgumentException("Location must be an explicit nonnegative integer.");
		}
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Location exceeds the supported integer range.", e);
		}
	}

	/**
	 * Rejects preserved cells that cannot be represented safely by upstream types.
	 * @param row output cells.
	 */
	private static void validateOptional(Map<String, String> row) {
		if (row.get("Power").isBlank()) {
			throw new IllegalArgumentException("Unknown CHIRP Power; an operator must explicitly choose "
					+ "programmingMetadata chirp.Power before export. No transmit power is inferred.");
		}
		polarity(row.get("DtcsPolarity"));
		tone("Tone", row.get("rToneFreq"), "N");
		tone("Tone", row.get("cToneFreq"), "N");
		tone("DTCS", row.get("DtcsCode"), "N");
		tone("DTCS", row.get("RxDtcsCode"), "N");
		if (!CROSS.contains(row.get("CrossMode"))) {
			throw new IllegalArgumentException("Invalid preserved CrossMode.");
		}
		if (!Set.of("", "S", "P").contains(row.get("Skip"))) {
			throw new IllegalArgumentException("Invalid Skip value.");
		}
		if (!row.get("Power").matches("[0-9]+(\\.[0-9]+)?[Ww]?")
				|| !row.get("TStep").matches("[0-9]+(\\.[0-9]+)?")) {
			throw new IllegalArgumentException("Unverified Power or TStep value.");
		}
		String power = row.get("Power").replace("W", "").replace("w", "");
		if (new BigDecimal(power).signum() <= 0) {
			throw new IllegalArgumentException("CHIRP Power must be explicitly positive; hardware suitability remains unverified.");
		}
	}
}
