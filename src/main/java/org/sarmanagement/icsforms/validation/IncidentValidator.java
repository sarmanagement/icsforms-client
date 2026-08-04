package org.sarmanagement.icsforms.validation;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.ResourceAssignment;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies first-cut validation rules for shared incident data, ICS 202, and ICS 204.
 */
public class IncidentValidator {
    /**
     * Validates the current incident document.
     *
     * @param data incident document to validate.
     * @return actionable validation messages keyed to fields or form sections.
     */
    public List<ValidationMessage> validate(AppData data) {
        List<ValidationMessage> messages = new ArrayList<>();
        if (data == null) {
            messages.add(new ValidationMessage("document", "Incident document is missing."));
            return messages;
        }

        validateContext(data.getIncidentContext(), messages);
        validate202(data.getForm202(), messages);
        validate204(data.getForm204(), messages);
        return messages;
    }

    /**
     * Returns whether export should be blocked.
     *
     * @param data incident document to validate.
     * @return {@code true} when validation errors exist.
     */
    public boolean hasErrors(AppData data) {
        return !validate(data).isEmpty();
    }

    /**
     * Validates shared incident metadata.
     *
     * @param context shared incident context.
     * @param messages collector for validation messages.
     */
    private void validateContext(IncidentContext context, List<ValidationMessage> messages) {
        if (context == null) {
            messages.add(new ValidationMessage("incidentContext", "Shared incident context is required."));
            return;
        }
        if (blank(context.getIncidentName())) {
            messages.add(new ValidationMessage("incidentName", "Incident name is required."));
        }
        if (context.getOperationalPeriodStart() == null) {
            messages.add(new ValidationMessage("operationalPeriodStart", "Operational period start date/time is required."));
        }
        if (context.getOperationalPeriodEnd() == null) {
            messages.add(new ValidationMessage("operationalPeriodEnd", "Operational period end date/time is required."));
        }
        if (context.getOperationalPeriodStart() != null && context.getOperationalPeriodEnd() != null
                && context.getOperationalPeriodStart().isAfter(context.getOperationalPeriodEnd())) {
            messages.add(new ValidationMessage("operationalPeriod", "Operational period start must be before or equal to the end."));
        }
        if (blank(context.getCurrentUser())) {
            messages.add(new ValidationMessage("currentUser", "Preparer identity is required."));
        }
    }

    /**
     * Validates ICS 202 requirements.
     *
     * @param form incident objectives form.
     * @param messages collector for validation messages.
     */
    private void validate202(Ics202Form form, List<ValidationMessage> messages) {
        if (form == null) {
            messages.add(new ValidationMessage("ics202", "ICS 202 content is required."));
            return;
        }
        if (form.getObjectives().stream().noneMatch(item -> !blank(item))) {
            messages.add(new ValidationMessage("ics202.objectives", "At least one incident objective is required."));
        }
        if (blank(form.getPreparedByName())) {
            messages.add(new ValidationMessage("ics202.preparedByName", "ICS 202 preparer name is required."));
        }
        if (blank(form.getPreparedByPositionTitle())) {
            messages.add(new ValidationMessage("ics202.preparedByPositionTitle", "ICS 202 preparer position/title is required."));
        }
        if (blank(form.getPreparedBySignature())) {
            messages.add(new ValidationMessage("ics202.preparedBySignature", "ICS 202 preparer signature is required."));
        }
        if (blank(form.getApprovedByIncidentCommanderName())) {
            messages.add(new ValidationMessage("ics202.approvedByIncidentCommanderName", "Incident commander approval name is required."));
        }
        if (blank(form.getApprovedBySignature())) {
            messages.add(new ValidationMessage("ics202.approvedBySignature", "Incident commander signature is required."));
        }
        if (form.getApprovedDateTime() == null) {
            messages.add(new ValidationMessage("ics202.approvedDateTime", "Incident commander approval date/time is required."));
        }
    }

    /**
     * Validates ICS 204 requirements.
     *
     * @param form assignment list form.
     * @param messages collector for validation messages.
     */
    private void validate204(Ics204Form form, List<ValidationMessage> messages) {
        if (form == null) {
            messages.add(new ValidationMessage("ics204", "ICS 204 content is required."));
            return;
        }
        if (blank(form.getOperationsSectionChiefName())) {
            messages.add(new ValidationMessage("ics204.operationsSectionChiefName", "Operations section chief name is required."));
        }
        if (blank(form.getOperationsSectionChiefContact())) {
            messages.add(new ValidationMessage("ics204.operationsSectionChiefContact", "Operations section chief contact is required."));
        }
        if (form.requiresBranchDivisionGroupSupervisor() && blank(form.getDivisionGroupSupervisorName())) {
            messages.add(new ValidationMessage("ics204.divisionGroupSupervisorName", "Division/group supervisor is required when branch, division, or group is set."));
        }
        if (form.requiresBranchDivisionGroupSupervisor() && blank(form.getDivisionGroupSupervisorContact())) {
            messages.add(new ValidationMessage("ics204.divisionGroupSupervisorContact", "Division/group supervisor contact is required when branch, division, or group is set."));
        }
        if (form.getResourcesAssigned().isEmpty()) {
            messages.add(new ValidationMessage("ics204.resourcesAssigned", "At least one resource assignment is required."));
        }
        for (int i = 0; i < form.getResourcesAssigned().size(); i++) {
            ResourceAssignment resource = form.getResourcesAssigned().get(i);
            if (blank(resource.getResourceIdentifier())) {
                messages.add(new ValidationMessage("ics204.resourcesAssigned[" + i + "].resourceIdentifier", "Each resource must have an identifier."));
            }
            if (blank(effectiveAssignment(form, resource))) {
                messages.add(new ValidationMessage("ics204.resourcesAssigned[" + i + "].assignment", "Each resource needs a work assignment or shared assignment text."));
            }
        }
        for (int i = 0; i < form.getCommunications().size(); i++) {
            CommunicationEntry entry = form.getCommunications().get(i);
            if (blank(entry.getNameOrFunction()) || blank(entry.getPrimaryContact())) {
                messages.add(new ValidationMessage("ics204.communications[" + i + "]", "Communication rows need both name/function and primary contact."));
            }
        }
        if (blank(form.getPreparedByName())) {
            messages.add(new ValidationMessage("ics204.preparedByName", "ICS 204 preparer name is required."));
        }
        if (blank(form.getPreparedByPositionTitle())) {
            messages.add(new ValidationMessage("ics204.preparedByPositionTitle", "ICS 204 preparer position/title is required."));
        }
        if (blank(form.getPreparedBySignature())) {
            messages.add(new ValidationMessage("ics204.preparedBySignature", "ICS 204 preparer signature is required."));
        }
        if (form.getPreparedDateTime() == null) {
            messages.add(new ValidationMessage("ics204.preparedDateTime", "ICS 204 preparer date/time is required."));
        }
    }

    /**
     * Resolves the assignment text that should flow into SAR scaffolding and PDF output.
     *
     * @param form parent ICS 204 form.
     * @param resource resource row.
     * @return effective assignment text.
     */
    private String effectiveAssignment(Ics204Form form, ResourceAssignment resource) {
        return blank(resource.getAssignment()) ? form.getSharedWorkAssignment() : resource.getAssignment();
    }

    /**
     * Returns whether a value is blank.
     *
     * @param value value to inspect.
     * @return {@code true} when the value is null or blank.
     */
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
