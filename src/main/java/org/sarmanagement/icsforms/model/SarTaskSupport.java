package org.sarmanagement.icsforms.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized SAR task option lists and qualitative POD factor profiles.
 */
public final class SarTaskSupport {
    public static final String RESOURCE_TYPE_CANINE = "Canine";
    public static final String RESOURCE_TYPE_EQUINE = "Equine";
    public static final String RESOURCE_TYPE_GROUND = "Ground";
    public static final String RESOURCE_TYPE_TECHNICAL = "Technical";
    public static final String RESOURCE_TYPE_AIR = "Air";
    public static final String RESOURCE_TYPE_WATER = "Water";

    public static final String TASK_TYPE_AREA = "Area";
    public static final String TASK_TYPE_ROUTE = "Route";
    public static final String TASK_TYPE_POINT = "Point";

    private static final List<String> RESOURCE_TYPES = List.of(
            "", RESOURCE_TYPE_CANINE, RESOURCE_TYPE_EQUINE, RESOURCE_TYPE_GROUND,
            RESOURCE_TYPE_TECHNICAL, RESOURCE_TYPE_AIR, RESOURCE_TYPE_WATER);
    private static final List<String> TASK_TYPES = List.of("", TASK_TYPE_AREA, TASK_TYPE_ROUTE, TASK_TYPE_POINT);

    private static final List<PodFactorTemplate> HUMAN_GROUND_FACTORS = List.of(
            new PodFactorTemplate("Hazards", 10, false),
            new PodFactorTemplate("Terrain", 10, true),
            new PodFactorTemplate("Vegetation", 10, true),
            new PodFactorTemplate("Weather", 10, true),
            new PodFactorTemplate("Team Composition", 10, true),
            new PodFactorTemplate("Light", 10, true),
            new PodFactorTemplate("Area Size", 10, true),
            new PodFactorTemplate("Tactics", 10, true),
            new PodFactorTemplate("Spacing / Sweep Width", 10, true),
            new PodFactorTemplate("Instinct and Other Variables", 10, true)
    );
    private static final List<PodFactorTemplate> CANINE_FACTORS = List.of(
            new PodFactorTemplate("Hazards Observed", 5, false),
            new PodFactorTemplate("Wind", 10, true),
            new PodFactorTemplate("Humidity", 10, true),
            new PodFactorTemplate("Vegetation", 10, true),
            new PodFactorTemplate("Established Sweep Width Pattern", 10, true),
            new PodFactorTemplate("Team Wellness", 10, true),
            new PodFactorTemplate("Contamination", 10, true),
            new PodFactorTemplate("Handler/K-9 Certification", 5, true),
            new PodFactorTemplate("Light", 5, true),
            new PodFactorTemplate("Weather/Temperature", 5, true),
            new PodFactorTemplate("Terrain Features", 5, true),
            new PodFactorTemplate("Area Size/Time Allotment", 5, true),
            new PodFactorTemplate("Team Fatigue", 5, true),
            new PodFactorTemplate("Instinct and Other Variables", 5, true)
    );
    private static final List<PodFactorTemplate> EQUINE_FACTORS = List.of(
            new PodFactorTemplate("Hazards Observed", 5, false),
            new PodFactorTemplate("Rider Training and Experience", 10, true),
            new PodFactorTemplate("Mount Experience", 10, true),
            new PodFactorTemplate("Mount Management", 10, true),
            new PodFactorTemplate("Terrain", 10, true),
            new PodFactorTemplate("Vegetation", 10, true),
            new PodFactorTemplate("Contamination", 5, true),
            new PodFactorTemplate("Rider SAR Training/Certification", 5, true),
            new PodFactorTemplate("Light", 5, true),
            new PodFactorTemplate("Weather", 5, true),
            new PodFactorTemplate("Fatigue", 5, true),
            new PodFactorTemplate("Humidity", 5, true),
            new PodFactorTemplate("Area Features", 5, true),
            new PodFactorTemplate("Wind", 5, true),
            new PodFactorTemplate("Other Variables", 5, true)
    );

    private SarTaskSupport() {
    }

    public static List<String> resourceTypes() {
        return new ArrayList<>(RESOURCE_TYPES);
    }

    public static List<String> taskTypes() {
        return new ArrayList<>(TASK_TYPES);
    }

    public static String normalizedResourceType(String resourceType) {
        String normalized = safe(resourceType);
        for (String candidate : RESOURCE_TYPES) {
            if (!candidate.isBlank() && candidate.equalsIgnoreCase(normalized)) {
                return candidate;
            }
        }
        return normalized;
    }

    public static String normalizedTaskType(String taskType) {
        String normalized = safe(taskType);
        for (String candidate : TASK_TYPES) {
            if (!candidate.isBlank() && candidate.equalsIgnoreCase(normalized)) {
                return candidate;
            }
        }
        return normalized;
    }

    public static String podProfileLabel(String resourceType) {
        return switch (normalizedResourceType(resourceType)) {
            case RESOURCE_TYPE_CANINE -> "Canine Resources";
            case RESOURCE_TYPE_EQUINE -> "Equine Resources";
            default -> "Human Ground Searchers";
        };
    }

    public static boolean usesCanineFactors(String resourceType) {
        return RESOURCE_TYPE_CANINE.equals(normalizedResourceType(resourceType));
    }

    public static List<PodFactorRating> factorRatings(String resourceType, List<PodFactorRating> existing) {
        List<PodFactorTemplate> templates = factorTemplates(resourceType);
        List<PodFactorRating> ratings = new ArrayList<>();
        for (PodFactorTemplate template : templates) {
            PodFactorRating rating = new PodFactorRating();
            rating.setName(template.name());
            rating.setMaxScore(template.maxScore());
            PodFactorRating previous = findByName(existing, template.name());
            if (previous != null) {
                rating.setScore(previous.getScore());
                if (template.allowDescription()) {
                    rating.setDescription(previous.getDescription());
                }
            }
            ratings.add(rating);
        }
        return ratings;
    }

    public static boolean allowsDescription(String resourceType, String factorName) {
        for (PodFactorTemplate template : factorTemplates(resourceType)) {
            if (template.name().equals(factorName)) {
                return template.allowDescription();
            }
        }
        return true;
    }

    private static List<PodFactorTemplate> factorTemplates(String resourceType) {
        return switch (normalizedResourceType(resourceType)) {
            case RESOURCE_TYPE_CANINE -> CANINE_FACTORS;
            case RESOURCE_TYPE_EQUINE -> EQUINE_FACTORS;
            default -> HUMAN_GROUND_FACTORS;
        };
    }

    private static PodFactorRating findByName(List<PodFactorRating> existing, String name) {
        if (existing == null) {
            return null;
        }
        for (PodFactorRating rating : existing) {
            if (rating != null && name.equals(rating.getName())) {
                return rating;
            }
        }
        return null;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record PodFactorTemplate(String name, int maxScore, boolean allowDescription) {
    }
}
