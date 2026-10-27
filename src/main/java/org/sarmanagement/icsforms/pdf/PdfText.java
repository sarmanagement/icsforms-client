/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ResourceDirectoryEntry;
import org.sarmanagement.icsforms.model.ResourceDirectorySource;
import org.sarmanagement.icsforms.model.SarTaskAssignment;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 * Normalizes layout whitespace and validates each renderer's lossless encoding policy.
 */
public final class PdfText {
	private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
	private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

	/**
	 * Prevents construction of the text utility.
	 */
	private PdfText() {
	}

	/**
	 * Begins a text block whose close balances it even when drawing fails.
	 *
	 * @param stream page content stream.
	 * @return scope that ends the text block.
	 * @throws IOException when beginning the block fails.
	 */
	static TextBlock begin(PDPageContentStream stream) throws IOException {
		stream.beginText();
		return new TextBlock(stream);
	}

	/**
	 * Text block scope with try-with-resources exception suppression.
	 *
	 * @param stream page content stream.
	 */
	record TextBlock(PDPageContentStream stream) implements AutoCloseable {
		/** {@inheritDoc} */
		@Override
		public void close() throws IOException {
			stream.endText();
		}
	}

	/**
	 * Preserves logical line breaks while expanding tabs for PDF layout.
	 *
	 * @param value optional source text.
	 * @return normalized, non-null text.
	 */
	public static String normalize(String value) {
		return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
	}

	/**
	 * Normalizes text for an inline field without modifying stored content.
	 *
	 * @param value optional source text.
	 * @return validated single-line text.
	 */
	static String singleLine(String value) {
		validate(value, "PDF", "text");
		return normalize(value).replace('\n', ' ');
	}

	/**
	 * Validates a field before whitespace processing can hide unwanted characters.
	 *
	 * @param value source text.
	 * @param form form identifier.
	 * @param field field name or model path.
	 * @throws IllegalArgumentException when a control or unsupported character occurs.
	 */
	public static void validate(String value, String form, String field) {
		validate(value, form, field, false);
	}

	/**
	 * Validates controls while allowing a renderer's existing reversible glyph escapes.
	 *
	 * @param value source text.
	 * @param form form identifier.
	 * @param field field name or model path.
	 * @throws IllegalArgumentException when an unwanted control character occurs.
	 */
	static void validateEscaped(String value, String form, String field) {
		validate(value, form, field, true);
	}

	/**
	 * Applies the encoding policy without modifying original source text.
	 *
	 * @param value source text.
	 * @param form form identifier.
	 * @param field field name or model path.
	 * @param escapeUnsupported whether printable unsupported glyphs will be escaped.
	 * @throws IllegalArgumentException when the renderer cannot preserve a character.
	 */
	private static void validate(String value, String form, String field, boolean escapeUnsupported) {
		String text = value == null ? "" : value;
		for (int offset = 0; offset < text.length();) {
			int codePoint = text.codePointAt(offset);
			offset += Character.charCount(codePoint);
			if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t') {
				continue;
			}
			boolean control = Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT;
			try {
				if (control) {
					throw new IllegalArgumentException("Control character");
				}
				synchronized (FONT) {
					FONT.getStringWidth(new String(Character.toChars(codePoint)));
				}
			} catch (IOException | IllegalArgumentException exception) {
				if (!control && escapeUnsupported) {
					continue;
				}
				throw new IllegalArgumentException(form + ", field " + field + ": "
						+ String.format("U+%04X", codePoint)
						+ (control ? " is an unwanted control character." : " is not supported by the PDF font.")
						+ " Edit this field to remove or replace that character, then retry export."
						+ " The original content has not been changed.", exception);
			}
		}
	}

	/**
	 * Validates the source fields used by a form, retaining actionable model paths.
	 *
	 * @param data source document, or null for a blank form.
	 * @param form form identifier.
	 * @throws IllegalArgumentException when a source field cannot be rendered or reversibly escaped.
	 */
	static void validateForm(AppData data, String form) {
		if (data == null) {
			return;
		}
		JsonNode root = MAPPER.valueToTree(data);
		boolean escapeUnsupported = "ICS 233-CG".equals(form) || "Clue Log (ICS 233-CG adapted)".equals(form);
		if (!"Clue Log".equals(form)) {
			validateFields(root.get("incidentContext"), form, "incidentContext", escapeUnsupported, "incidentName");
			if ("ICS 202".equals(form) || "ICS 205A".equals(form) || "IAP cover".equals(form)) {
				validateFields(root.get("incidentContext"), form, "incidentContext", escapeUnsupported,
						"currentUser", "currentUserPositionTitle");
			}
		}
		String[] fields = switch (form) {
			case "ICS 201" -> new String[]{"form201"};
			case "ICS 202" -> new String[]{"form202"};
			case "ICS 207" -> new String[]{"form207"};
			case "ICS 205A" -> new String[]{"form205aIapPage"};
			case "ICS 208" -> new String[]{"form208"};
			case "ICS 214" -> new String[]{"activityLogs", "activityEventTypes"};
			case "ICS 215A" -> new String[]{"form215a"};
			case "ICS 233-CG" -> new String[]{"openActionItems"};
			case "SAR Task Assignment" -> new String[]{"sarTaskAssignments"};
			case "Clue Log", "Clue Log (ICS 233-CG adapted)" -> new String[]{"clueLogEntries"};
			default -> new String[0];
		};
		for (String field : fields) {
			validateNode(root.get(field), form, field, escapeUnsupported);
		}
		validateSupportingFields(data, root, form);
	}

	/**
	 * Projects supporting models to printed fields rather than validating unrelated metadata.
	 *
	 * @param data original incident.
	 * @param root serialized incident for field-path diagnostics.
	 * @param form selected renderer.
	 */
	private static void validateSupportingFields(AppData data, JsonNode root, String form) {
		if ("ICS 205".equals(form)) {
			JsonNode plan = root.get("form205");
			validateFields(plan, form, "form205", false, "specialInstructions", "preparedByName",
					"preparedBySignature", "iapPage");
			JsonNode channels = plan == null ? null : plan.get("channels");
			if (channels != null && channels.isArray()) {
				for (int i = 0; i < channels.size(); i++) {
					JsonNode channel = channels.get(i);
					if (channel.path("includeOnForm").asBoolean(true)) {
						validateFields(channel, form, "form205.channels[" + i + "]", false, "zoneGroup",
								"channelNumber", "function", "channelName", "assignment", "rxFrequency", "rxBandwidth",
								"rxToneNac", "txFrequency", "txBandwidth", "txToneNac", "mode", "digitalProtocol",
								"system", "remarks", "txPolicy");
					}
				}
			}
		} else if ("ICS 204".equals(form)) {
			String[] fields = {"selectedContextHeading", "selectedContextValue", "operationsSectionChiefName",
					"operationsSectionChiefContact", "secondaryManagementRoleLabel", "secondaryManagementName",
					"secondaryManagementContact", "specialInstructions", "sharedWorkAssignment", "preparedByName",
					"preparedByPositionTitle", "preparedBySignature", "iapPage", "communications.name",
					"communications.function", "communications.primaryContact", "resourcesAssigned.assignmentTeamNumber",
					"resourcesAssigned.resourceIdentifier", "resourcesAssigned.leader", "resourcesAssigned.contact",
					"resourcesAssigned.reportingLocation", "resourcesAssigned.specialEquipment", "resourcesAssigned.remarks",
					"resourcesAssigned.notes", "resourcesAssigned.resourceType", "resourcesAssigned.taskType",
					"resourcesAssigned.supplies", "resourcesAssigned.assignment"};
			validateFields(root.get("form204"), form, "form204", false, fields);
			validateFields(root.get("additionalForms204"), form, "additionalForms204", false, fields);
		} else if ("ICS 201".equals(form)) {
			validateFields(root.get("organizationalChart"), form, "organizationalChart", false,
					"incidentCommanders", "safetyOfficerName", "publicInformationOfficerName", "liaisonOfficerName",
					"operationsSectionChiefName", "planningSectionChiefName", "logisticsSectionChiefName",
					"financeAdminSectionChiefName", "communicationsUnitLeaderName", "documentationUnitLeaderName");
		} else if ("ICS 203".equals(form) || "ICS 207".equals(form)) {
			validateFields(root.get("organizationalChart"), form, "organizationalChart", false,
					"safetyOfficerName", "publicInformationOfficerName", "liaisonOfficerName",
					"operationsSectionChiefName", "planningSectionChiefName", "logisticsSectionChiefName",
					"financeAdminSectionChiefName", "resourcesUnitLeaderName", "situationUnitLeaderName",
					"documentationUnitLeaderName", "demobilizationUnitLeaderName", "supplyUnitLeaderName",
					"facilitiesUnitLeaderName", "groundSupportUnitLeaderName", "communicationsUnitLeaderName",
					"foodUnitLeaderName", "communicationsTechnicianName", "stagingAreaManagerName",
					"timeUnitLeaderName", "procurementUnitLeaderName", "compClaimsUnitLeaderName", "costUnitLeaderName",
					"additionalPositions.name", "additionalPositions.title");
			if ("ICS 203".equals(form)) {
				boolean entriesUsed = data.getOrganizationalChart().getIncidentCommanderEntries().stream()
						.anyMatch(entry -> entry != null && !entry.getName().isBlank());
				validateFields(root.get("organizationalChart"), form, "organizationalChart", false,
						entriesUsed ? "incidentCommanderEntries.name" : "incidentCommanders");
				validateFields(root.get("organizationalChart").get("assignmentList"), form,
						"organizationalChart.assignmentList", false, "positions", "agencyRepresentatives.name",
						"agencyRepresentatives.title", "branches.name", "branches.director", "branches.deputy",
						"branches.divisions.name", "branches.divisions.title", "preparedByName",
						"preparedByPositionTitle", "preparedBySignature", "iapPage");
			} else {
				for (int index = 0; index < data.getSarTaskAssignments().size(); index++) {
					SarTaskAssignment task = data.getSarTaskAssignments().get(index);
					if (task != null) {
						String field = task.getResourceIdentifier().isBlank() ? "assignmentTeamNumber" : "resourceIdentifier";
						validateFields(root.get("sarTaskAssignments").get(index), form,
								"sarTaskAssignments[" + (index + 1) + "]", false, field, "leader");
					}
				}
				validateFields(root.get("organizationalChart"), form, "organizationalChart", false, "incidentCommanders");
			}
		} else if ("ICS 205A".equals(form)) {
			for (ResourceDirectoryEntry entry : ResourceDirectorySource.build(data)) {
				String path = "tcards[" + (data.getTCards().indexOf(entry.card()) + 1) + "]";
				validate(entry.card().getPersonName(), form, path + ".personName");
				validate(entry.card().getPhoneNumber(), form, path + ".phoneNumber");
				validate(entry.card().getRadioChannel(), form, path + ".radioChannel");
				validate(entry.assignedPosition(), form, path + ".assignedPosition");
			}
		} else if ("SAR Task Assignment".equals(form)) {
			for (int index = 0; index < data.getClueLogEntries().size(); index++) {
				var clue = data.getClueLogEntries().get(index);
				if (clue != null && data.getSarTaskAssignments().stream().anyMatch(task -> task != null
						&& (!task.getAssignmentId().isBlank() && task.getAssignmentId().equals(clue.getAssignmentId())
						|| task.getAssignmentId().isBlank() && !task.getAssignmentTeamNumber().isBlank()
						&& task.getAssignmentTeamNumber().equals(clue.getDetectingTask())))) {
					validateFields(root.get("clueLogEntries").get(index), form,
							"clueLogEntries[" + (index + 1) + "]", false,
							"location", "description", "immediateAction", "followUp");
				}
			}
		}
	}

	/**
	 * Validates explicitly selected properties, preserving array indexes and original values.
	 *
	 * @param node model object or array.
	 * @param form selected renderer.
	 * @param path source field path.
	 * @param escapeUnsupported whether printable unsupported glyphs are reversibly escaped.
	 * @param fields printed properties; dots select nested properties.
	 */
	private static void validateFields(JsonNode node, String form, String path, boolean escapeUnsupported,
			String... fields) {
		if (node == null || node.isNull()) {
			return;
		}
		if (node.isArray()) {
			for (int index = 0; index < node.size(); index++) {
				validateFields(node.get(index), form, path + "[" + (index + 1) + "]", escapeUnsupported, fields);
			}
			return;
		}
		for (String field : fields) {
			int separator = field.indexOf('.');
			if (separator < 0) {
				validateNode(node.get(field), form, path + "." + field, escapeUnsupported);
			} else {
				String property = field.substring(0, separator);
				validateFields(node.get(property), form, path + "." + property, escapeUnsupported,
						field.substring(separator + 1));
			}
		}
	}

	/**
	 * Walks a serialized model branch without altering any source values.
	 *
	 * @param node model branch.
	 * @param form form identifier.
	 * @param path field path.
	 * @param escapeUnsupported whether unsupported printable glyphs are reversibly escaped.
	 */
	private static void validateNode(JsonNode node, String form, String path, boolean escapeUnsupported) {
		if (node == null || node.isNull()) {
			return;
		}
		if (node.isTextual()) {
			validate(node.textValue(), form, path, escapeUnsupported);
		} else if (node.isArray()) {
			for (int index = 0; index < node.size(); index++) {
				validateNode(node.get(index), form, path + "[" + (index + 1) + "]", escapeUnsupported);
			}
		} else if (node.isObject()) {
			Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> field = fields.next();
				validateNode(field.getValue(), form, path + "." + field.getKey(), escapeUnsupported);
			}
		}
	}
}
