package org.sarmanagement.icsforms.model;

/**
 * Qualitative POD factor rating collected during SAR task debriefing.
 */
public class PodFactorRating {
    private String name = "";
    private int maxScore = 10;
    private Integer score;
    private String description = "";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(int maxScore) {
        this.maxScore = maxScore <= 0 ? 10 : maxScore;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }
}
