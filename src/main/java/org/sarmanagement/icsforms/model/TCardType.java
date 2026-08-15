package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * ICS 219 T-Card (Resource Status Card) type taxonomy.
 *
 * <p>Each value corresponds to a standard ICS 219 card colour and resource category.</p>
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
     * 219-7 Equipment card (yellow). Used for ground equipment including canines
     * (which are treated as working assets/equipment in ICS).
     * JSON alias {@code "DOZER"} accepted for backward compatibility.
     */
    @JsonAlias("DOZER")
    EQUIPMENT("219-7 Equipment", "yellow"),
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

    TCardType(String label, String color) {
        this.label = label;
        this.color = color;
    }

    /**
     * Returns the human-readable label for this card type.
     *
     * @return card type label.
     */
    public String getLabel() {
        return label;
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
