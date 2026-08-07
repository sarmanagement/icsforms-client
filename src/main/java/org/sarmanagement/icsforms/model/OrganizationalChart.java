package org.sarmanagement.icsforms.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared organizational chart data reused across the first-cut form editors.
 */
public class OrganizationalChart {
    private List<String> incidentCommanders = new ArrayList<>();
    private String operationsSectionChiefName = "";

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
}
