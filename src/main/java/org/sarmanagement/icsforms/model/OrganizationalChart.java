package org.sarmanagement.icsforms.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared organizational chart data reused across the first-cut form editors.
 *
 * <p>All position fields are optional.  Blank values are allowed and rendered gracefully
 * in the form editors and PDF output.  Contact values may be a radio channel or phone number.</p>
 */
public class OrganizationalChart {
    // Incident command
    private List<String> incidentCommanders = new ArrayList<>();

    // Command Staff (optional positions)
    private String safetyOfficerName = "";
    private String safetyOfficerContact = "";
    private String publicInformationOfficerName = "";
    private String publicInformationOfficerContact = "";
    private String liaisonOfficerName = "";
    private String liaisonOfficerContact = "";

    // General Staff
    private String operationsSectionChiefName = "";
    private String operationsSectionChiefContact = "";
    private String planningSectionChiefName = "";
    private String planningSectionChiefContact = "";
    private String logisticsSectionChiefName = "";
    private String logisticsSectionChiefContact = "";
    private String financeAdminSectionChiefName = "";
    private String financeAdminSectionChiefContact = "";

    // Planning Section positions
    private String documentationUnitLeaderName = "";
    private String documentationUnitLeaderContact = "";

    /**
     * Returns the incident commander or unified command names.
     *
     * @return incident commander names.
     */
    public List<String> getIncidentCommanders() {
        return incidentCommanders;
    }

    /**
     * Sets the incident commander or unified command names.
     *
     * @param incidentCommanders incident commander names.
     */
    public void setIncidentCommanders(List<String> incidentCommanders) {
        this.incidentCommanders = incidentCommanders == null ? new ArrayList<>() : new ArrayList<>(incidentCommanders);
    }

    /**
     * Returns the operations section chief name.
     *
     * @return operations section chief name.
     */
    public String getOperationsSectionChiefName() {
        return operationsSectionChiefName;
    }

    /**
     * Sets the operations section chief name.
     *
     * @param operationsSectionChiefName operations section chief name.
     */
    public void setOperationsSectionChiefName(String operationsSectionChiefName) {
        this.operationsSectionChiefName = operationsSectionChiefName;
    }

    /**
     * Returns the operations section chief contact (radio/phone).
     *
     * @return operations section chief contact.
     */
    public String getOperationsSectionChiefContact() {
        return operationsSectionChiefContact;
    }

    /**
     * Sets the operations section chief contact.
     *
     * @param operationsSectionChiefContact operations section chief contact.
     */
    public void setOperationsSectionChiefContact(String operationsSectionChiefContact) {
        this.operationsSectionChiefContact = operationsSectionChiefContact == null ? "" : operationsSectionChiefContact;
    }

    /**
     * Returns the safety officer name.
     *
     * @return safety officer name.
     */
    public String getSafetyOfficerName() {
        return safetyOfficerName;
    }

    /**
     * Sets the safety officer name.
     *
     * @param safetyOfficerName safety officer name.
     */
    public void setSafetyOfficerName(String safetyOfficerName) {
        this.safetyOfficerName = safetyOfficerName == null ? "" : safetyOfficerName;
    }

    /**
     * Returns the safety officer contact (radio/phone).
     *
     * @return safety officer contact.
     */
    public String getSafetyOfficerContact() {
        return safetyOfficerContact;
    }

    /**
     * Sets the safety officer contact.
     *
     * @param safetyOfficerContact safety officer contact.
     */
    public void setSafetyOfficerContact(String safetyOfficerContact) {
        this.safetyOfficerContact = safetyOfficerContact == null ? "" : safetyOfficerContact;
    }

    /**
     * Returns the public information officer name.
     *
     * @return PIO name.
     */
    public String getPublicInformationOfficerName() {
        return publicInformationOfficerName;
    }

    /**
     * Sets the public information officer name.
     *
     * @param publicInformationOfficerName PIO name.
     */
    public void setPublicInformationOfficerName(String publicInformationOfficerName) {
        this.publicInformationOfficerName = publicInformationOfficerName == null ? "" : publicInformationOfficerName;
    }

    /**
     * Returns the public information officer contact (radio/phone).
     *
     * @return PIO contact.
     */
    public String getPublicInformationOfficerContact() {
        return publicInformationOfficerContact;
    }

    /**
     * Sets the public information officer contact.
     *
     * @param publicInformationOfficerContact PIO contact.
     */
    public void setPublicInformationOfficerContact(String publicInformationOfficerContact) {
        this.publicInformationOfficerContact = publicInformationOfficerContact == null ? "" : publicInformationOfficerContact;
    }

    /**
     * Returns the liaison officer name.
     *
     * @return liaison officer name.
     */
    public String getLiaisonOfficerName() {
        return liaisonOfficerName;
    }

    /**
     * Sets the liaison officer name.
     *
     * @param liaisonOfficerName liaison officer name.
     */
    public void setLiaisonOfficerName(String liaisonOfficerName) {
        this.liaisonOfficerName = liaisonOfficerName == null ? "" : liaisonOfficerName;
    }

    /**
     * Returns the liaison officer contact (radio/phone).
     *
     * @return liaison officer contact.
     */
    public String getLiaisonOfficerContact() {
        return liaisonOfficerContact;
    }

    /**
     * Sets the liaison officer contact.
     *
     * @param liaisonOfficerContact liaison officer contact.
     */
    public void setLiaisonOfficerContact(String liaisonOfficerContact) {
        this.liaisonOfficerContact = liaisonOfficerContact == null ? "" : liaisonOfficerContact;
    }

    /**
     * Returns the planning section chief name.
     *
     * @return planning section chief name.
     */
    public String getPlanningSectionChiefName() {
        return planningSectionChiefName;
    }

    /**
     * Sets the planning section chief name.
     *
     * @param planningSectionChiefName planning section chief name.
     */
    public void setPlanningSectionChiefName(String planningSectionChiefName) {
        this.planningSectionChiefName = planningSectionChiefName == null ? "" : planningSectionChiefName;
    }

    /**
     * Returns the planning section chief contact (radio/phone).
     *
     * @return planning section chief contact.
     */
    public String getPlanningSectionChiefContact() {
        return planningSectionChiefContact;
    }

    /**
     * Sets the planning section chief contact.
     *
     * @param planningSectionChiefContact planning section chief contact.
     */
    public void setPlanningSectionChiefContact(String planningSectionChiefContact) {
        this.planningSectionChiefContact = planningSectionChiefContact == null ? "" : planningSectionChiefContact;
    }

    /**
     * Returns the logistics section chief name.
     *
     * @return logistics section chief name.
     */
    public String getLogisticsSectionChiefName() {
        return logisticsSectionChiefName;
    }

    /**
     * Sets the logistics section chief name.
     *
     * @param logisticsSectionChiefName logistics section chief name.
     */
    public void setLogisticsSectionChiefName(String logisticsSectionChiefName) {
        this.logisticsSectionChiefName = logisticsSectionChiefName == null ? "" : logisticsSectionChiefName;
    }

    /**
     * Returns the logistics section chief contact (radio/phone).
     *
     * @return logistics section chief contact.
     */
    public String getLogisticsSectionChiefContact() {
        return logisticsSectionChiefContact;
    }

    /**
     * Sets the logistics section chief contact.
     *
     * @param logisticsSectionChiefContact logistics section chief contact.
     */
    public void setLogisticsSectionChiefContact(String logisticsSectionChiefContact) {
        this.logisticsSectionChiefContact = logisticsSectionChiefContact == null ? "" : logisticsSectionChiefContact;
    }

    /**
     * Returns the finance/admin section chief name.
     *
     * @return finance/admin section chief name.
     */
    public String getFinanceAdminSectionChiefName() {
        return financeAdminSectionChiefName;
    }

    /**
     * Sets the finance/admin section chief name.
     *
     * @param financeAdminSectionChiefName finance/admin section chief name.
     */
    public void setFinanceAdminSectionChiefName(String financeAdminSectionChiefName) {
        this.financeAdminSectionChiefName = financeAdminSectionChiefName == null ? "" : financeAdminSectionChiefName;
    }

    /**
     * Returns the finance/admin section chief contact (radio/phone).
     *
     * @return finance/admin section chief contact.
     */
    public String getFinanceAdminSectionChiefContact() {
        return financeAdminSectionChiefContact;
    }

    /**
     * Sets the finance/admin section chief contact.
     *
     * @param financeAdminSectionChiefContact finance/admin section chief contact.
     */
    public void setFinanceAdminSectionChiefContact(String financeAdminSectionChiefContact) {
        this.financeAdminSectionChiefContact = financeAdminSectionChiefContact == null ? "" : financeAdminSectionChiefContact;
    }

    /**
     * Returns the documentation unit leader name (Planning Section).
     *
     * @return documentation unit leader name.
     */
    public String getDocumentationUnitLeaderName() {
        return documentationUnitLeaderName;
    }

    /**
     * Sets the documentation unit leader name.
     *
     * @param documentationUnitLeaderName documentation unit leader name.
     */
    public void setDocumentationUnitLeaderName(String documentationUnitLeaderName) {
        this.documentationUnitLeaderName = documentationUnitLeaderName == null ? "" : documentationUnitLeaderName;
    }

    /**
     * Returns the documentation unit leader contact (radio/phone).
     *
     * @return documentation unit leader contact.
     */
    public String getDocumentationUnitLeaderContact() {
        return documentationUnitLeaderContact;
    }

    /**
     * Sets the documentation unit leader contact.
     *
     * @param documentationUnitLeaderContact documentation unit leader contact.
     */
    public void setDocumentationUnitLeaderContact(String documentationUnitLeaderContact) {
        this.documentationUnitLeaderContact = documentationUnitLeaderContact == null ? "" : documentationUnitLeaderContact;
    }
}
