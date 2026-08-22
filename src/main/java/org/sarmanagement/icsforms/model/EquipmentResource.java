package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Canonical ground-equipment resource — covers ICS 219-3 Engine (rose), 219-7 Equipment
 * (yellow, non-canine), and 219-8 Miscellaneous Equipment (tan).
 *
 * <p>An equipment resource may have zero or one designated operator/handler tracked via
 * {@link #getOperatorResourceId()}.  The {@link #getTCardType()} returns the appropriate
 * 219 card type based on the {@link #getEquipmentCategory()} set at creation.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EquipmentResource extends IncidentResource {

    /**
     * Broad equipment category, used to pick the right T-card colour.
     */
    public enum EquipmentCategory {
        /** ICS 219-3 — Engine (rose). */
        ENGINE,
        /** ICS 219-7 — Ground equipment (yellow). */
        EQUIPMENT,
        /** ICS 219-8 — Miscellaneous equipment / task force (tan). */
        MISC_EQUIPMENT
    }

    private EquipmentCategory equipmentCategory = EquipmentCategory.EQUIPMENT;

    /**
     * {@code resourceId} of the {@link PersonnelResource} who is the primary operator
     * or handler of this equipment.  Blank when no operator has been linked.
     */
    private String operatorResourceId = "";

    /**
     * Cached display name of the operator, for rendering convenience.
     * Canonical value lives in the operator's {@link PersonnelResource}.
     */
    private String operatorDisplayName = "";

    /**
     * Creates a new empty equipment resource with a generated {@code resourceId}.
     */
    public EquipmentResource() {
        super();
    }

    /**
     * Convenience factory.
     *
     * @param identifier equipment identifier or description.
     * @param category   equipment category.
     * @return new equipment resource.
     */
    public static EquipmentResource of(String identifier, EquipmentCategory category) {
        EquipmentResource r = new EquipmentResource();
        r.setDisplayName(identifier == null ? "" : identifier);
        r.setEquipmentCategory(category == null ? EquipmentCategory.EQUIPMENT : category);
        return r;
    }

    /**
     * Returns the equipment category.
     *
     * @return equipment category.
     */
    public EquipmentCategory getEquipmentCategory() {
        return equipmentCategory;
    }

    /**
     * Sets the equipment category.
     *
     * @param equipmentCategory equipment category; {@code null} treated as
     *                          {@link EquipmentCategory#EQUIPMENT}.
     */
    public void setEquipmentCategory(EquipmentCategory equipmentCategory) {
        this.equipmentCategory = equipmentCategory == null ? EquipmentCategory.EQUIPMENT : equipmentCategory;
    }

    /**
     * Returns the {@code resourceId} of the operator {@link PersonnelResource}.
     *
     * @return operator resource ID.
     */
    public String getOperatorResourceId() {
        return operatorResourceId;
    }

    /**
     * Sets the {@code resourceId} of the operator {@link PersonnelResource}.
     *
     * @param operatorResourceId operator resource ID; {@code null} treated as blank.
     */
    public void setOperatorResourceId(String operatorResourceId) {
        this.operatorResourceId = operatorResourceId == null ? "" : operatorResourceId;
    }

    /**
     * Returns the cached operator display name for rendering convenience.
     *
     * @return operator display name.
     */
    public String getOperatorDisplayName() {
        return operatorDisplayName;
    }

    /**
     * Sets the cached operator display name.
     *
     * @param operatorDisplayName operator display name; {@code null} treated as blank.
     */
    public void setOperatorDisplayName(String operatorDisplayName) {
        this.operatorDisplayName = operatorDisplayName == null ? "" : operatorDisplayName;
    }

    /** {@inheritDoc} */
    @Override
    public TCardType getTCardType() {
        if (equipmentCategory == null) {
            return TCardType.EQUIPMENT;
        }
        return switch (equipmentCategory) {
            case ENGINE        -> TCardType.ENGINE;
            case MISC_EQUIPMENT -> TCardType.MISC_EQUIPMENT;
            default            -> TCardType.EQUIPMENT;
        };
    }
}
