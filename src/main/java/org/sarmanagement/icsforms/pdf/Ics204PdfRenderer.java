package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.ResourceAssignment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured PDF renderer for the Assignment List (ICS 204) form.
 */
public class Ics204PdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** {@inheritDoc} */
    @Override
    public String getFormKey() {
        return "ICS 204";
    }

    /** {@inheritDoc} */
    @Override
    public void render(AppData data, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        try (PDDocument document = new PDDocument()) {
            IncidentContext context = data.getIncidentContext();
            Ics204Form form = data.getForm204();
            List<List<String>> blocks = new ArrayList<>();
            blocks.add(List.of(
                    "Incident Name: " + safe(context.getIncidentName()),
                    "Operational Period: " + format(context.getOperationalPeriodStart()) + " to " + format(context.getOperationalPeriodEnd()),
                    "Prepared/Current User: " + safe(context.getCurrentUser()) + " / " + safe(context.getCurrentUserPositionTitle())
            ));
            blocks.add(List.of(
                    "Context: branch=" + safe(form.getBranch()) + ", division=" + safe(form.getDivision()) + ", group=" + safe(form.getGroup()) + ", staging=" + safe(form.getStagingArea()),
                    "Operations Section Chief: " + safe(form.getOperationsSectionChiefName()) + " / " + safe(form.getOperationsSectionChiefContact()),
                    "Branch Director: " + safe(form.getBranchDirectorName()) + " / " + safe(form.getBranchDirectorContact()),
                    "Division/Group Supervisor: " + safe(form.getDivisionGroupSupervisorName()) + " / " + safe(form.getDivisionGroupSupervisorContact())
            ));
            List<String> resourcesBlock = new ArrayList<>();
            resourcesBlock.add("Resources Assigned:");
            for (ResourceAssignment resource : form.getResourcesAssigned()) {
                resourcesBlock.add("- [" + safe(resource.getAssignmentId()) + "] " + safe(resource.getResourceIdentifier())
                        + ", leader=" + safe(resource.getLeader())
                        + ", persons=" + resource.getNumberOfPersons()
                        + ", contact=" + safe(resource.getContact()));
                resourcesBlock.add("  location=" + safe(resource.getReportingLocation())
                        + "; equipment=" + safe(resource.getSpecialEquipment())
                        + "; supplies=" + safe(resource.getSupplies())
                        + "; remarks=" + safe(resource.getRemarks())
                        + "; notes=" + safe(resource.getNotes()));
                resourcesBlock.add("  assignment=" + safe(resource.getAssignment() == null || resource.getAssignment().isBlank() ? form.getSharedWorkAssignment() : resource.getAssignment()));
            }
            blocks.add(resourcesBlock);
            List<String> communicationsBlock = new ArrayList<>();
            communicationsBlock.add("Special Instructions: " + safe(form.getSpecialInstructions()));
            communicationsBlock.add("Communications:");
            for (CommunicationEntry entry : form.getCommunications()) {
                communicationsBlock.add("- " + safe(entry.getNameOrFunction()) + ": " + safe(entry.getPrimaryContact()));
            }
            blocks.add(communicationsBlock);
            blocks.add(List.of(
                    "Prepared By: " + safe(form.getPreparedByName()) + " / " + safe(form.getPreparedByPositionTitle()) + " / " + format(form.getPreparedDateTime()),
                    "IAP Page: " + safe(form.getIapPage())
            ));
            writeDocument(document, "ICS 204", "Assignment List", blocks);
            document.save(outputFile.toFile());
        }
    }

    /**
     * Formats a date/time for PDF output.
     *
     * @param value date/time value.
     * @return formatted value.
     */
    private String format(java.time.LocalDateTime value) {
        return value == null ? "" : FORMATTER.format(value);
    }

    /**
     * Converts null strings to empty text.
     *
     * @param value string value.
     * @return safe text.
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }
}
