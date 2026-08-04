package org.sarmanagement.icsforms.model;

/**
 * Communication row for ICS 204 or linked SAR task context.
 */
public class CommunicationEntry {
    private String name = "";
    private String function = "";
    private String primaryContact = "";

    /**
     * Returns the contact name.
     *
     * @return contact name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the contact name.
     *
     * @param name contact name.
     */
    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    /**
     * Returns the function/role label.
     *
     * @return function or role.
     */
    public String getFunction() {
        return function;
    }

    /**
     * Sets the function/role label.
     *
     * @param function function or role.
     */
    public void setFunction(String function) {
        this.function = function == null ? "" : function;
    }

    /**
     * Returns the combined name/function label used by legacy callers.
     *
     * @return name or function.
     */
    public String getNameOrFunction() {
        if (!name.isBlank() && !function.isBlank()) {
            return name + " / " + function;
        }
        return !name.isBlank() ? name : function;
    }

    /**
     * Sets the legacy combined name/function label.
     *
     * @param nameOrFunction name or function.
     */
    public void setNameOrFunction(String nameOrFunction) {
        String value = nameOrFunction == null ? "" : nameOrFunction.trim();
        if (value.contains("/")) {
            String[] parts = value.split("/", 2);
            setName(parts[0].trim());
            setFunction(parts[1].trim());
        } else {
            setName(value);
            setFunction("");
        }
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
