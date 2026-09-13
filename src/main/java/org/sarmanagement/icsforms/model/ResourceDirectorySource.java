package org.sarmanagement.icsforms.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the canonical personnel-directory rows shared by the T-card Directory View and the
 * ICS 205A Communications List export.
 */
public final class ResourceDirectorySource {
    private static final Map<String, String> ORG_POSITION_BY_REF = createOrgPositionMap();

    private ResourceDirectorySource() {
    }

    /**
     * Builds directory rows from the current incident document.
     *
     * @param data incident document
     * @return canonical directory rows
     */
    public static List<ResourceDirectoryEntry> build(AppData data) {
        AppData safeData = data == null ? new AppData() : data;
        return build(safeData, safeData.getTCards());
    }

    /**
     * Builds directory rows from the current incident document and supplied T-card list.
     *
     * <p>This overload lets the T-card UI render the same canonical directory rows from the
     * in-memory table model before those edits are pushed back into {@link AppData}.</p>
     *
     * @param data incident document
     * @param cards T-cards to project into directory rows
     * @return canonical directory rows
     */
    public static List<ResourceDirectoryEntry> build(AppData data, List<TCard> cards) {
        AppData safeData = data == null ? new AppData() : data;
        Map<String, SarTaskAssignment> tasksById = new LinkedHashMap<>();
        for (SarTaskAssignment task : safeData.getSarTaskAssignments()) {
            if (task != null && !safe(task.getAssignmentId()).isBlank()) {
                tasksById.put(task.getAssignmentId().trim(), task);
            }
        }

        List<ResourceDirectoryEntry> rows = new ArrayList<>();
        if (cards == null) {
            return rows;
        }
        for (TCard card : cards) {
            if (card == null || card.getCardType() != TCardType.PERSONNEL) {
                continue;
            }
            String name = safe(card.getPersonName());
            if (name.isBlank()) {
                continue;
            }
            rows.add(new ResourceDirectoryEntry(
                    card,
                    name,
                    assignedPosition(card, safeData, tasksById),
                    safe(card.getHomeState()),
                    safe(card.getHomeAgency()),
                    assignment(card, tasksById),
                    safe(card.getStatus()),
                    contactMethods(card)
            ));
        }
        return rows;
    }

    /**
     * Returns a comparator that sorts rows alphabetically by last name, then full name.
     */
    public static Comparator<ResourceDirectoryEntry> byLastName() {
        return Comparator.comparing((ResourceDirectoryEntry row) -> lastNameSortKey(row.name()))
                .thenComparing(row -> safe(row.name()).toLowerCase(Locale.ROOT))
                .thenComparing(row -> safe(row.assignment()).toLowerCase(Locale.ROOT));
    }

    /**
     * Returns a comparator that sorts display names alphabetically by last name.
     */
    public static Comparator<String> displayNameComparator() {
        return Comparator.comparing(ResourceDirectorySource::lastNameSortKey)
                .thenComparing(value -> safe(value).toLowerCase(Locale.ROOT));
    }

    private static String assignedPosition(TCard card, AppData data, Map<String, SarTaskAssignment> tasksById) {
        String ref = safe(card.getSourceRef());
        if (ref.startsWith("sar:")) {
            SarTaskAssignment task = taskForRef(ref, tasksById);
            if (task == null) {
                return "";
            }
            if (ref.endsWith(":leader")) {
                return coalesce(task.getLeaderRole(), "Leader");
            }
            SarTaskResource resource = findTaskResource(card, task);
            if (resource != null) {
                return coalesce(resource.getIcsPosition(), resource.getFunction());
            }
            return "";
        }
        if ("context:preparer".equals(ref)) {
            IncidentContext context = data.getIncidentContext();
            return context == null ? "Preparer"
                    : coalesce(context.getCurrentUserPositionTitle(), "Preparer");
        }
        if (ref.startsWith("org:ic:")) {
            return "Incident Commander";
        }
        return ORG_POSITION_BY_REF.getOrDefault(ref, "");
    }

    private static String assignment(TCard card, Map<String, SarTaskAssignment> tasksById) {
        String ref = safe(card.getSourceRef());
        if (ref.startsWith("sar:")) {
            SarTaskAssignment task = taskForRef(ref, tasksById);
            return task == null ? "" : taskLabel(task);
        }
        return safe(card.getLocation());
    }

    private static String contactMethods(TCard card) {
        List<String> parts = new ArrayList<>();
        if (!safe(card.getRadioChannel()).isBlank()) {
            parts.add("Radio: " + card.getRadioChannel().trim());
        }
        if (!safe(card.getPhoneNumber()).isBlank()) {
            parts.add("Phone: " + card.getPhoneNumber().trim());
        }
        return String.join("; ", parts);
    }

    private static SarTaskAssignment taskForRef(String ref, Map<String, SarTaskAssignment> tasksById) {
        String[] parts = ref.split(":");
        if (parts.length < 2) {
            return null;
        }
        return tasksById.get(parts[1]);
    }

    private static SarTaskResource findTaskResource(TCard card, SarTaskAssignment task) {
        List<SarTaskResource> resources = task.getResourcesAssigned();
        if (resources == null || resources.isEmpty()) {
            return null;
        }
        String cardId = safe(card.getResourceId());
        if (!cardId.isBlank()) {
            for (SarTaskResource resource : resources) {
                if (resource != null && cardId.equals(resource.getResourceId())) {
                    return resource;
                }
            }
        }
        String cardName = safe(card.getPersonName());
        for (SarTaskResource resource : resources) {
            if (resource != null && cardName.equalsIgnoreCase(safe(resource.getName()))) {
                return resource;
            }
        }
        return null;
    }

    private static String taskLabel(SarTaskAssignment task) {
        if (task == null) {
            return "";
        }
        String number = safe(task.getAssignmentTeamNumber());
        String resource = safe(task.getResourceIdentifier());
        String leader = safe(task.getLeader());
        String assignmentPreview = firstWords(task.getAssignment(), 6);
        StringBuilder label = new StringBuilder();
        if (!number.isBlank()) {
            label.append(number);
        }
        if (!resource.isBlank()) {
            if (!label.isEmpty()) {
                label.append(" · ");
            }
            label.append(resource);
        }
        if (!leader.isBlank()) {
            if (!label.isEmpty()) {
                label.append(" · ");
            }
            label.append("Lead: ").append(leader);
        }
        if (!assignmentPreview.isBlank()) {
            if (!label.isEmpty()) {
                label.append(" · ");
            }
            label.append(assignmentPreview);
        }
        return label.toString();
    }

    private static String firstWords(String text, int limit) {
        String normalized = safe(text);
        if (normalized.isBlank()) {
            return "";
        }
        String[] words = normalized.split("\\s+");
        int count = Math.min(limit, words.length);
        return String.join(" ", java.util.Arrays.copyOf(words, count));
    }

    private static String lastNameSortKey(String name) {
        String normalized = safe(name).trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.contains(",")) {
            String[] parts = normalized.split(",", 2);
            String last = safe(parts[0]).toLowerCase(Locale.ROOT);
            String rest = parts.length > 1 ? safe(parts[1]).toLowerCase(Locale.ROOT) : "";
            return last + "|" + rest;
        }
        String[] parts = normalized.split("\\s+");
        String last = parts[parts.length - 1].toLowerCase(Locale.ROOT);
        String firsts = parts.length > 1
                ? String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1)).toLowerCase(Locale.ROOT)
                : "";
        return last + "|" + firsts;
    }

    private static String coalesce(String preferred, String fallback) {
        String safePreferred = safe(preferred);
        return !safePreferred.isBlank() ? safePreferred : safe(fallback);
    }

    private static String safe(String value) {
        return Objects.toString(value, "").trim();
    }

    private static Map<String, String> createOrgPositionMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("org:safetyOfficer", "Safety Officer");
        map.put("org:pio", "Public Information Officer");
        map.put("org:liaisonOfficer", "Liaison Officer");
        map.put("org:operationsChief", "Operations Section Chief");
        map.put("org:planningChief", "Planning Section Chief");
        map.put("org:logisticsChief", "Logistics Section Chief");
        map.put("org:financeAdminChief", "Finance/Admin Section Chief");
        map.put("org:documentationUnitLeader", "Documentation Unit Leader");
        map.put("org:commUnitLeader", "Communications Unit Leader");
        map.put("org:commTechnician", "Communications Technician");
        return map;
    }
}
