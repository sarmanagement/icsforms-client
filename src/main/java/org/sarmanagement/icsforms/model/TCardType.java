package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * ICS 219 T-Card (Resource Status Card) type taxonomy.
 *
 * <p>Each value corresponds to a standard ICS 219 card colour and resource category.
 * Some types carry an optional SAR-mode label that is displayed instead of the standard
 * label when the incident is running in SAR mode (see {@link #getLabel(boolean)}).</p>
 */
public enum TCardType {
    /** 219-1 Header card (grey). Groups cards by location or status. */
    HEADER("219-1 Header", "grey"),
    /** 219-2 Crew/Team card (green). */
    CREW("219-2 Crew/Team", "green"),
    /** 219-3 Engine card (rose). */
    ENGINE("219-3 Engine", "rose"),
    /** 219-4 Helicopter card (blue). */
    HELICOPTER("219-4 Helicopter", "blue"),
    /** 219-5 Personnel card (white). */
    PERSONNEL("219-5 Personnel", "white"),
    /**
     * 219-6 Fixed-Wing card (orange). Used for fixed-wing aircraft and drones/UAS.
     * JSON alias {@code "AIRCRAFT"} accepted for backward compatibility.
     */
    @JsonAlias("AIRCRAFT")
    FIXED_WING("219-6 Fixed-Wing", "orange"),
    /**
     * 219-7 Equipment card (yellow). Used for ground equipment.
     * In SAR mode the label includes the canine note because canines are treated as
     * working assets/equipment in ICS SAR operations.
     * JSON alias {@code "DOZER"} accepted for backward compatibility.
     */
    @JsonAlias("DOZER")
    EQUIPMENT("219-7 Equipment", "yellow", "219-7 Equipment (inc. Canine)"),
    /** 219-8 Miscellaneous Equipment / Task Force card (tan). */
    MISC_EQUIPMENT("219-8 Misc. Equipment", "tan"),
    /**
     * 219-10 Generic card (light purple).
     *
     * <p>Application-defined extension card for general-purpose resources that do not
     * fit any of the standard ICS 219-1 through 219-8 categories.</p>
     */
    GENERIC("219-10 Generic", "light purple");

    private final String label;
    private final String color;
    /** Optional SAR-mode label; {@code null} when the standard label applies in all modes. */
    private final String sarLabel;

    TCardType(String label, String color) {
        this(label, color, null);
    }

    TCardType(String label, String color, String sarLabel) {
        this.label = label;
        this.color = color;
        this.sarLabel = sarLabel;
    }

    /**
     * Returns the human-readable label for this card type.
     *
     * @return standard card type label.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the label appropriate for the given mode.
     *
     * <p>When {@code sarMode} is {@code true} and this type has a SAR-specific label,
     * the SAR label is returned; otherwise the standard label is returned.</p>
     *
     * @param sarMode {@code true} when the incident is running in SAR mode.
     * @return the label to display.
     */
    public String getLabel(boolean sarMode) {
        return (sarMode && sarLabel != null) ? sarLabel : label;
    }

    /**
     * Returns the standard colour name for this card type.
     *
     * @return card colour name.
     */
    public String getColor() {
        return color;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return label;
    }
}
