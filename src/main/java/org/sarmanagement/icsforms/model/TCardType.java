package org.sarmanagement.icsforms.model;

/**
 * ICS 219 T-Card (Resource Status Card) type taxonomy.
 *
 * <p>Each value corresponds to a standard ICS 219 card colour and resource category.
 * Only {@link #PERSONNEL} is fully editable in the current implementation; the other
 * types exist as model scaffolding for future form-filling support.</p>
 */
public enum TCardType {
    /** 219-1 Header card (grey). Groups cards by location or status. */
    HEADER("219-1 Header", "grey"),
    /** 219-2 Crew card (green). */
    CREW("219-2 Crew", "green"),
    /** 219-3 Engine card (rose). */
    ENGINE("219-3 Engine", "rose"),
    /** 219-4 Helicopter card (blue). */
    HELICOPTER("219-4 Helicopter", "blue"),
    /** 219-5 Personnel card (white). */
    PERSONNEL("219-5 Personnel", "white"),
    /** 219-6 Aircraft card (orange). Used for drones. */
    AIRCRAFT("219-6 Aircraft", "orange"),
    /** 219-7 Dozer card (yellow). */
    DOZER("219-7 Dozer", "yellow"),
    /** 219-8 Miscellaneous Equipment card (buff/tan). Used for canine+handler. */
    MISC_EQUIPMENT("219-8 Misc. Equipment", "buff/tan");

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
