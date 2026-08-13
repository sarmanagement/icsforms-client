package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared organizational chart data reused across the first-cut form editors.
 *
 * <p>All position fields are optional.  Blank values are allowed and rendered gracefully
 * in the form editors and PDF output.  Each position now stores separate radio-channel and
 * phone-number fields.  Legacy JSON files that stored a single {@code *Contact} value will
 * have that value deserialized into the corresponding {@code *Phone} field via
 * {@link JsonAlias} annotations for backward compatibility.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrganizationalChart {
    // Incident command
    private List<String> incidentCommanders = new ArrayList<>();

    // Command Staff (optional positions)
    private String safetyOfficerName = "";
    private String safetyOfficerRadio = "";
    @JsonAlias("safetyOfficerContact")
    private String safetyOfficerPhone = "";

    private String publicInformationOfficerName = "";
    private String publicInformationOfficerRadio = "";
    @JsonAlias("publicInformationOfficerContact")
    private String publicInformationOfficerPhone = "";

    private String liaisonOfficerName = "";
    private String liaisonOfficerRadio = "";
    @JsonAlias("liaisonOfficerContact")
    private String liaisonOfficerPhone = "";

    // General Staff
    private String operationsSectionChiefName = "";
    private String operationsSectionChiefRadio = "";
    @JsonAlias("operationsSectionChiefContact")
    private String operationsSectionChiefPhone = "";

    private String planningSectionChiefName = "";
    private String planningSectionChiefRadio = "";
    @JsonAlias("planningSectionChiefContact")
    private String planningSectionChiefPhone = "";

    private String logisticsSectionChiefName = "";
    private String logisticsSectionChiefRadio = "";
    @JsonAlias("logisticsSectionChiefContact")
    private String logisticsSectionChiefPhone = "";

    private String financeAdminSectionChiefName = "";
    private String financeAdminSectionChiefRadio = "";
    @JsonAlias("financeAdminSectionChiefContact")
    private String financeAdminSectionChiefPhone = "";

    // Planning Section positions
    private String documentationUnitLeaderName = "";
    private String documentationUnitLeaderRadio = "";
    @JsonAlias("documentationUnitLeaderContact")
    private String documentationUnitLeaderPhone = "";

    // Logistics Section positions
    private String communicationsUnitLeaderName = "";
    private String communicationsUnitLeaderRadio = "";
    private String communicationsUnitLeaderPhone = "";

    private String communicationsTechnicianName = "";
    private String communicationsTechnicianRadio = "";
    private String communicationsTechnicianPhone = "";

    /**
     * Returns the incident commander or unified command names.
     */
    public List<String> getIncidentCommanders() { return incidentCommanders; }
    public void setIncidentCommanders(List<String> v) { incidentCommanders = v == null ? new ArrayList<>() : new ArrayList<>(v); }

    // --- Safety Officer ---
    public String getSafetyOfficerName()  { return safetyOfficerName; }
    public void setSafetyOfficerName(String v)  { safetyOfficerName  = v == null ? "" : v; }
    public String getSafetyOfficerRadio() { return safetyOfficerRadio; }
    public void setSafetyOfficerRadio(String v) { safetyOfficerRadio = v == null ? "" : v; }
    public String getSafetyOfficerPhone() { return safetyOfficerPhone; }
    public void setSafetyOfficerPhone(String v) { safetyOfficerPhone = v == null ? "" : v; }

    // --- PIO ---
    public String getPublicInformationOfficerName()  { return publicInformationOfficerName; }
    public void setPublicInformationOfficerName(String v)  { publicInformationOfficerName  = v == null ? "" : v; }
    public String getPublicInformationOfficerRadio() { return publicInformationOfficerRadio; }
    public void setPublicInformationOfficerRadio(String v) { publicInformationOfficerRadio = v == null ? "" : v; }
    public String getPublicInformationOfficerPhone() { return publicInformationOfficerPhone; }
    public void setPublicInformationOfficerPhone(String v) { publicInformationOfficerPhone = v == null ? "" : v; }

    // --- Liaison Officer ---
    public String getLiaisonOfficerName()  { return liaisonOfficerName; }
    public void setLiaisonOfficerName(String v)  { liaisonOfficerName  = v == null ? "" : v; }
    public String getLiaisonOfficerRadio() { return liaisonOfficerRadio; }
    public void setLiaisonOfficerRadio(String v) { liaisonOfficerRadio = v == null ? "" : v; }
    public String getLiaisonOfficerPhone() { return liaisonOfficerPhone; }
    public void setLiaisonOfficerPhone(String v) { liaisonOfficerPhone = v == null ? "" : v; }

    // --- Operations Section Chief ---
    public String getOperationsSectionChiefName()  { return operationsSectionChiefName; }
    public void setOperationsSectionChiefName(String v)  { operationsSectionChiefName  = v == null ? "" : v; }
    public String getOperationsSectionChiefRadio() { return operationsSectionChiefRadio; }
    public void setOperationsSectionChiefRadio(String v) { operationsSectionChiefRadio = v == null ? "" : v; }
    public String getOperationsSectionChiefPhone() { return operationsSectionChiefPhone; }
    public void setOperationsSectionChiefPhone(String v) { operationsSectionChiefPhone = v == null ? "" : v; }

    // --- Planning Section Chief ---
    public String getPlanningSectionChiefName()  { return planningSectionChiefName; }
    public void setPlanningSectionChiefName(String v)  { planningSectionChiefName  = v == null ? "" : v; }
    public String getPlanningSectionChiefRadio() { return planningSectionChiefRadio; }
    public void setPlanningSectionChiefRadio(String v) { planningSectionChiefRadio = v == null ? "" : v; }
    public String getPlanningSectionChiefPhone() { return planningSectionChiefPhone; }
    public void setPlanningSectionChiefPhone(String v) { planningSectionChiefPhone = v == null ? "" : v; }

    // --- Logistics Section Chief ---
    public String getLogisticsSectionChiefName()  { return logisticsSectionChiefName; }
    public void setLogisticsSectionChiefName(String v)  { logisticsSectionChiefName  = v == null ? "" : v; }
    public String getLogisticsSectionChiefRadio() { return logisticsSectionChiefRadio; }
    public void setLogisticsSectionChiefRadio(String v) { logisticsSectionChiefRadio = v == null ? "" : v; }
    public String getLogisticsSectionChiefPhone() { return logisticsSectionChiefPhone; }
    public void setLogisticsSectionChiefPhone(String v) { logisticsSectionChiefPhone = v == null ? "" : v; }

    // --- Finance/Admin Section Chief ---
    public String getFinanceAdminSectionChiefName()  { return financeAdminSectionChiefName; }
    public void setFinanceAdminSectionChiefName(String v)  { financeAdminSectionChiefName  = v == null ? "" : v; }
    public String getFinanceAdminSectionChiefRadio() { return financeAdminSectionChiefRadio; }
    public void setFinanceAdminSectionChiefRadio(String v) { financeAdminSectionChiefRadio = v == null ? "" : v; }
    public String getFinanceAdminSectionChiefPhone() { return financeAdminSectionChiefPhone; }
    public void setFinanceAdminSectionChiefPhone(String v) { financeAdminSectionChiefPhone = v == null ? "" : v; }

    // --- Documentation Unit Leader ---
    public String getDocumentationUnitLeaderName()  { return documentationUnitLeaderName; }
    public void setDocumentationUnitLeaderName(String v)  { documentationUnitLeaderName  = v == null ? "" : v; }
    public String getDocumentationUnitLeaderRadio() { return documentationUnitLeaderRadio; }
    public void setDocumentationUnitLeaderRadio(String v) { documentationUnitLeaderRadio = v == null ? "" : v; }
    public String getDocumentationUnitLeaderPhone() { return documentationUnitLeaderPhone; }
    public void setDocumentationUnitLeaderPhone(String v) { documentationUnitLeaderPhone = v == null ? "" : v; }

    // --- Communications Unit Leader (Logistics) ---
    public String getCommunicationsUnitLeaderName()  { return communicationsUnitLeaderName; }
    public void setCommunicationsUnitLeaderName(String v)  { communicationsUnitLeaderName  = v == null ? "" : v; }
    public String getCommunicationsUnitLeaderRadio() { return communicationsUnitLeaderRadio; }
    public void setCommunicationsUnitLeaderRadio(String v) { communicationsUnitLeaderRadio = v == null ? "" : v; }
    public String getCommunicationsUnitLeaderPhone() { return communicationsUnitLeaderPhone; }
    public void setCommunicationsUnitLeaderPhone(String v) { communicationsUnitLeaderPhone = v == null ? "" : v; }

    // --- Communications Technician (Logistics) ---
    public String getCommunicationsTechnicianName()  { return communicationsTechnicianName; }
    public void setCommunicationsTechnicianName(String v)  { communicationsTechnicianName  = v == null ? "" : v; }
    public String getCommunicationsTechnicianRadio() { return communicationsTechnicianRadio; }
    public void setCommunicationsTechnicianRadio(String v) { communicationsTechnicianRadio = v == null ? "" : v; }
    public String getCommunicationsTechnicianPhone() { return communicationsTechnicianPhone; }
    public void setCommunicationsTechnicianPhone(String v) { communicationsTechnicianPhone = v == null ? "" : v; }
}
