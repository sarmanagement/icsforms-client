package org.sarmanagement.icsforms.model;

/**
 * Resource entry printed on the SAR task assignment form.
 */
public class SarTaskResource {
    private String function = "";
    private String name = "";

    /** @return resource function or role. */
    public String getFunction() {
        return function;
    }

    /** @param function resource function or role. */
    public void setFunction(String function) {
        this.function = function == null ? "" : function;
    }

    /** @return resource name. */
    public String getName() {
        return name;
    }

    /** @param name resource name. */
    public void setName(String name) {
        this.name = name == null ? "" : name;
    }
}
