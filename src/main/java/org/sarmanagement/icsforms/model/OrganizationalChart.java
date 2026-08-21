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
    private String incidentCommanderRadio = "";
    private String incidentCommanderPhone = "";

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

    private String resourcesUnitLeaderName = "";
    private String resourcesUnitLeaderRadio = "";
    private String resourcesUnitLeaderPhone = "";

    private String situationUnitLeaderName = "";
    private String situationUnitLeaderRadio = "";
    private String situationUnitLeaderPhone = "";

    private String demobilizationUnitLeaderName = "";
    private String demobilizationUnitLeaderRadio = "";
    private String demobilizationUnitLeaderPhone = "";

    // Operations Section positions
    private String stagingAreaManagerName = "";
    private String stagingAreaManagerRadio = "";
    private String stagingAreaManagerPhone = "";

    // Logistics Section positions
    private String communicationsUnitLeaderName = "";
    private String communicationsUnitLeaderRadio = "";
    private String communicationsUnitLeaderPhone = "";

    private String communicationsTechnicianName = "";
    private String communicationsTechnicianRadio = "";
    private String communicationsTechnicianPhone = "";

    private String supplyUnitLeaderName = "";
    private String supplyUnitLeaderRadio = "";
    private String supplyUnitLeaderPhone = "";

    private String facilitiesUnitLeaderName = "";
    private String facilitiesUnitLeaderRadio = "";
    private String facilitiesUnitLeaderPhone = "";

    private String groundSupportUnitLeaderName = "";
    private String groundSupportUnitLeaderRadio = "";
    private String groundSupportUnitLeaderPhone = "";

    private String foodUnitLeaderName = "";
    private String foodUnitLeaderRadio = "";
    private String foodUnitLeaderPhone = "";

    // Finance/Admin Section positions
    private String timeUnitLeaderName = "";
    private String timeUnitLeaderRadio = "";
    private String timeUnitLeaderPhone = "";

    private String procurementUnitLeaderName = "";
    private String procurementUnitLeaderRadio = "";
    private String procurementUnitLeaderPhone = "";

    private String compClaimsUnitLeaderName = "";
    private String compClaimsUnitLeaderRadio = "";
    private String compClaimsUnitLeaderPhone = "";

    private String costUnitLeaderName = "";
    private String costUnitLeaderRadio = "";
    private String costUnitLeaderPhone = "";

    // User-defined additional positions
    private List<OrgChartEntry> additionalPositions = new ArrayList<>();

    /**
     * Returns the incident commander or unified command names.
     */
    public List<String> getIncidentCommanders() { return incidentCommanders; }
    public void setIncidentCommanders(List<String> v) { incidentCommanders = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public String getIncidentCommanderRadio() { return incidentCommanderRadio; }
    public void setIncidentCommanderRadio(String v) { incidentCommanderRadio = v == null ? "" : v; }
    public String getIncidentCommanderPhone() { return incidentCommanderPhone; }
    public void setIncidentCommanderPhone(String v) { incidentCommanderPhone = v == null ? "" : v; }

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

    // --- Staging Area Manager (Operations) ---
    public String getStagingAreaManagerName()  { return stagingAreaManagerName; }
    public void setStagingAreaManagerName(String v)  { stagingAreaManagerName  = v == null ? "" : v; }
    public String getStagingAreaManagerRadio() { return stagingAreaManagerRadio; }
    public void setStagingAreaManagerRadio(String v) { stagingAreaManagerRadio = v == null ? "" : v; }
    public String getStagingAreaManagerPhone() { return stagingAreaManagerPhone; }
    public void setStagingAreaManagerPhone(String v) { stagingAreaManagerPhone = v == null ? "" : v; }

    // --- Resources Unit Leader (Planning) ---
    public String getResourcesUnitLeaderName()  { return resourcesUnitLeaderName; }
    public void setResourcesUnitLeaderName(String v)  { resourcesUnitLeaderName  = v == null ? "" : v; }
    public String getResourcesUnitLeaderRadio() { return resourcesUnitLeaderRadio; }
    public void setResourcesUnitLeaderRadio(String v) { resourcesUnitLeaderRadio = v == null ? "" : v; }
    public String getResourcesUnitLeaderPhone() { return resourcesUnitLeaderPhone; }
    public void setResourcesUnitLeaderPhone(String v) { resourcesUnitLeaderPhone = v == null ? "" : v; }

    // --- Situation Unit Leader (Planning) ---
    public String getSituationUnitLeaderName()  { return situationUnitLeaderName; }
    public void setSituationUnitLeaderName(String v)  { situationUnitLeaderName  = v == null ? "" : v; }
    public String getSituationUnitLeaderRadio() { return situationUnitLeaderRadio; }
    public void setSituationUnitLeaderRadio(String v) { situationUnitLeaderRadio = v == null ? "" : v; }
    public String getSituationUnitLeaderPhone() { return situationUnitLeaderPhone; }
    public void setSituationUnitLeaderPhone(String v) { situationUnitLeaderPhone = v == null ? "" : v; }

    // --- Demobilization Unit Leader (Planning) ---
    public String getDemobilizationUnitLeaderName()  { return demobilizationUnitLeaderName; }
    public void setDemobilizationUnitLeaderName(String v)  { demobilizationUnitLeaderName  = v == null ? "" : v; }
    public String getDemobilizationUnitLeaderRadio() { return demobilizationUnitLeaderRadio; }
    public void setDemobilizationUnitLeaderRadio(String v) { demobilizationUnitLeaderRadio = v == null ? "" : v; }
    public String getDemobilizationUnitLeaderPhone() { return demobilizationUnitLeaderPhone; }
    public void setDemobilizationUnitLeaderPhone(String v) { demobilizationUnitLeaderPhone = v == null ? "" : v; }

    // --- Supply Unit Leader (Logistics) ---
    public String getSupplyUnitLeaderName()  { return supplyUnitLeaderName; }
    public void setSupplyUnitLeaderName(String v)  { supplyUnitLeaderName  = v == null ? "" : v; }
    public String getSupplyUnitLeaderRadio() { return supplyUnitLeaderRadio; }
    public void setSupplyUnitLeaderRadio(String v) { supplyUnitLeaderRadio = v == null ? "" : v; }
    public String getSupplyUnitLeaderPhone() { return supplyUnitLeaderPhone; }
    public void setSupplyUnitLeaderPhone(String v) { supplyUnitLeaderPhone = v == null ? "" : v; }

    // --- Facilities Unit Leader (Logistics) ---
    public String getFacilitiesUnitLeaderName()  { return facilitiesUnitLeaderName; }
    public void setFacilitiesUnitLeaderName(String v)  { facilitiesUnitLeaderName  = v == null ? "" : v; }
    public String getFacilitiesUnitLeaderRadio() { return facilitiesUnitLeaderRadio; }
    public void setFacilitiesUnitLeaderRadio(String v) { facilitiesUnitLeaderRadio = v == null ? "" : v; }
    public String getFacilitiesUnitLeaderPhone() { return facilitiesUnitLeaderPhone; }
    public void setFacilitiesUnitLeaderPhone(String v) { facilitiesUnitLeaderPhone = v == null ? "" : v; }

    // --- Ground Support Unit Leader (Logistics) ---
    public String getGroundSupportUnitLeaderName()  { return groundSupportUnitLeaderName; }
    public void setGroundSupportUnitLeaderName(String v)  { groundSupportUnitLeaderName  = v == null ? "" : v; }
    public String getGroundSupportUnitLeaderRadio() { return groundSupportUnitLeaderRadio; }
    public void setGroundSupportUnitLeaderRadio(String v) { groundSupportUnitLeaderRadio = v == null ? "" : v; }
    public String getGroundSupportUnitLeaderPhone() { return groundSupportUnitLeaderPhone; }
    public void setGroundSupportUnitLeaderPhone(String v) { groundSupportUnitLeaderPhone = v == null ? "" : v; }

    // --- Food Unit Leader (Logistics) ---
    public String getFoodUnitLeaderName()  { return foodUnitLeaderName; }
    public void setFoodUnitLeaderName(String v)  { foodUnitLeaderName  = v == null ? "" : v; }
    public String getFoodUnitLeaderRadio() { return foodUnitLeaderRadio; }
    public void setFoodUnitLeaderRadio(String v) { foodUnitLeaderRadio = v == null ? "" : v; }
    public String getFoodUnitLeaderPhone() { return foodUnitLeaderPhone; }
    public void setFoodUnitLeaderPhone(String v) { foodUnitLeaderPhone = v == null ? "" : v; }

    // --- Time Unit Leader (Finance/Admin) ---
    public String getTimeUnitLeaderName()  { return timeUnitLeaderName; }
    public void setTimeUnitLeaderName(String v)  { timeUnitLeaderName  = v == null ? "" : v; }
    public String getTimeUnitLeaderRadio() { return timeUnitLeaderRadio; }
    public void setTimeUnitLeaderRadio(String v) { timeUnitLeaderRadio = v == null ? "" : v; }
    public String getTimeUnitLeaderPhone() { return timeUnitLeaderPhone; }
    public void setTimeUnitLeaderPhone(String v) { timeUnitLeaderPhone = v == null ? "" : v; }

    // --- Procurement Unit Leader (Finance/Admin) ---
    public String getProcurementUnitLeaderName()  { return procurementUnitLeaderName; }
    public void setProcurementUnitLeaderName(String v)  { procurementUnitLeaderName  = v == null ? "" : v; }
    public String getProcurementUnitLeaderRadio() { return procurementUnitLeaderRadio; }
    public void setProcurementUnitLeaderRadio(String v) { procurementUnitLeaderRadio = v == null ? "" : v; }
    public String getProcurementUnitLeaderPhone() { return procurementUnitLeaderPhone; }
    public void setProcurementUnitLeaderPhone(String v) { procurementUnitLeaderPhone = v == null ? "" : v; }

    // --- Comp/Claims Unit Leader (Finance/Admin) ---
    public String getCompClaimsUnitLeaderName()  { return compClaimsUnitLeaderName; }
    public void setCompClaimsUnitLeaderName(String v)  { compClaimsUnitLeaderName  = v == null ? "" : v; }
    public String getCompClaimsUnitLeaderRadio() { return compClaimsUnitLeaderRadio; }
    public void setCompClaimsUnitLeaderRadio(String v) { compClaimsUnitLeaderRadio = v == null ? "" : v; }
    public String getCompClaimsUnitLeaderPhone() { return compClaimsUnitLeaderPhone; }
    public void setCompClaimsUnitLeaderPhone(String v) { compClaimsUnitLeaderPhone = v == null ? "" : v; }

    // --- Cost Unit Leader (Finance/Admin) ---
    public String getCostUnitLeaderName()  { return costUnitLeaderName; }
    public void setCostUnitLeaderName(String v)  { costUnitLeaderName  = v == null ? "" : v; }
    public String getCostUnitLeaderRadio() { return costUnitLeaderRadio; }
    public void setCostUnitLeaderRadio(String v) { costUnitLeaderRadio = v == null ? "" : v; }
    public String getCostUnitLeaderPhone() { return costUnitLeaderPhone; }
    public void setCostUnitLeaderPhone(String v) { costUnitLeaderPhone = v == null ? "" : v; }

    // --- Additional / custom positions ---
    public List<OrgChartEntry> getAdditionalPositions() { return additionalPositions; }
    public void setAdditionalPositions(List<OrgChartEntry> v) {
        additionalPositions = v == null ? new ArrayList<>() : new ArrayList<>(v);
    }
}
