package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single user-defined position entry in the organizational chart.
 *
 * <p>These entries are stored in {@link OrganizationalChart#getAdditionalPositions()} and
 * represent roles that the user adds via the "Add position" dialog on the Org Chart tab.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrgChartEntry {
    /** Chart section this position belongs to. */
    public enum Section {
        COMMAND_STAFF, OPERATIONS, PLANNING, LOGISTICS, FINANCE_ADMIN
    }

    private Section section = Section.OPERATIONS;
    private String title  = "";
    private String name   = "";
    private String radio  = "";
    private String phone  = "";

    public Section getSection()    { return section; }
    public void    setSection(Section v) { section = v == null ? Section.OPERATIONS : v; }
    public String  getTitle()      { return title; }
    public void    setTitle(String v)    { title  = v == null ? "" : v; }
    public String  getName()       { return name; }
    public void    setName(String v)     { name   = v == null ? "" : v; }
    public String  getRadio()      { return radio; }
    public void    setRadio(String v)    { radio  = v == null ? "" : v; }
    public String  getPhone()      { return phone; }
    public void    setPhone(String v)    { phone  = v == null ? "" : v; }
}
