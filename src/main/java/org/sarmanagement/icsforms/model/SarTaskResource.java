package org.sarmanagement.icsforms.model;

/**
 * Resource entry printed on the SAR task assignment form.
 */
public class SarTaskResource {
    private String function = "";
    private String icsPosition = "";
    private String homeAgency = "";
    private String name = "";
    private TCardType cardType = TCardType.PERSONNEL;

    /** @return resource function or role. */
    public String getFunction() {
        return function;
    }

    /** @param function resource function or role. */
    public void setFunction(String function) {
        this.function = function == null ? "" : function;
    }

    /** @return ICS position or role. */
    public String getIcsPosition() {
        return icsPosition.isBlank() ? function : icsPosition;
    }

    /** @param icsPosition ICS position or role. */
    public void setIcsPosition(String icsPosition) {
        this.icsPosition = icsPosition == null ? "" : icsPosition;
    }

    /** @return home agency. */
    public String getHomeAgency() {
        return homeAgency;
    }

    /** @param homeAgency home agency. */
    public void setHomeAgency(String homeAgency) {
        this.homeAgency = homeAgency == null ? "" : homeAgency;
    }

    /** @return resource name. */
    public String getName() {
        return name;
    }

    /** @param name resource name. */
    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    /** @return T-card type for this resource ({@code null} treated as PERSONNEL). */
    public TCardType getCardType() {
        return cardType;
    }

    /** @param cardType T-card type for this resource. */
    public void setCardType(TCardType cardType) {
        this.cardType = cardType;
    }
}
