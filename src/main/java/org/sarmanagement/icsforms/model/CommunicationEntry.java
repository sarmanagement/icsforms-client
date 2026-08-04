package org.sarmanagement.icsforms.model;

/**
 * Communication row for ICS 204 or linked SAR task context.
 */
public class CommunicationEntry {
    private String nameOrFunction = "";
    private String primaryContact = "";

    /**
     * Returns the name or function label.
     *
     * @return name or function.
     */
    public String getNameOrFunction() {
        return nameOrFunction;
    }

    /**
     * Sets the name or function label.
     *
     * @param nameOrFunction name or function.
     */
    public void setNameOrFunction(String nameOrFunction) {
        this.nameOrFunction = nameOrFunction;
    }

    /**
     * Returns the primary contact channel.
     *
     * @return primary contact.
     */
    public String getPrimaryContact() {
        return primaryContact;
    }

    /**
     * Sets the primary contact channel.
     *
     * @param primaryContact primary contact.
     */
    public void setPrimaryContact(String primaryContact) {
        this.primaryContact = primaryContact;
    }
}
