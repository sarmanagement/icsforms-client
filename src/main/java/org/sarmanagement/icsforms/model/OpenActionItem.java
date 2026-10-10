/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/** An ICS 233 action item with its current point of contact and UTC dates. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenActionItem {
	private int number;
	private String item = "";
	private String poc = "";
	private boolean briefedToPoc;
	private LocalDateTime startDate;
	private LocalDateTime targetDate;
	private LocalDateTime actualDate;
	private String status = "";

	/** Creates an empty action item. */
	public OpenActionItem() {
	}

	/**
	 * Returns the automatically assigned sequence number.
	 * @return sequence number
	 */
	public int getNumber() {
		return number;
	}

	/**
	 * Sets the sequence number.
	 * @param number sequence number
	 */
	public void setNumber(int number) {
		this.number = number;
	}

	/**
	 * Returns the action description.
	 * @return description, never null
	 */
	public String getItem() {
		return item;
	}

	/**
	 * Sets the action description.
	 * @param item description; null becomes empty
	 */
	public void setItem(String item) {
		this.item = item == null ? "" : item;
	}

	/**
	 * Returns the current point of contact.
	 * @return responsible person or resource, never null
	 */
	public String getPoc() {
		return poc;
	}

	/**
	 * Sets the current point of contact without keeping history.
	 * @param poc responsible person or resource; null becomes empty
	 */
	public void setPoc(String poc) {
		this.poc = poc == null ? "" : poc;
	}

	/**
	 * Returns whether the current POC has been briefed.
	 * @return true when briefed
	 */
	public boolean isBriefedToPoc() {
		return briefedToPoc;
	}

	/**
	 * Sets whether the current POC has been briefed.
	 * @param briefedToPoc true when briefed
	 */
	public void setBriefedToPoc(boolean briefedToPoc) {
		this.briefedToPoc = briefedToPoc;
	}

	/**
	 * Returns the UTC start time.
	 * @return start time, or null when unset
	 */
	public LocalDateTime getStartDate() {
		return startDate;
	}

	/**
	 * Sets the UTC start time.
	 * @param startDate start time, or null when unset
	 */
	public void setStartDate(LocalDateTime startDate) {
		this.startDate = startDate;
	}

	/**
	 * Returns the UTC target completion time.
	 * @return target time, or null when unset
	 */
	public LocalDateTime getTargetDate() {
		return targetDate;
	}

	/**
	 * Sets the UTC target completion time.
	 * @param targetDate target time, or null when unset
	 */
	public void setTargetDate(LocalDateTime targetDate) {
		this.targetDate = targetDate;
	}

	/**
	 * Returns the UTC actual completion time.
	 * @return completion time, or null when open
	 */
	public LocalDateTime getActualDate() {
		return actualDate;
	}

	/**
	 * Sets the UTC actual completion time.
	 * @param actualDate completion time, or null when open
	 */
	public void setActualDate(LocalDateTime actualDate) {
		this.actualDate = actualDate;
	}

	/**
	 * Returns free-text progress/status notes.
	 * @return status, never null
	 */
	public String getStatus() {
		return status;
	}

	/**
	 * Sets free-text progress/status notes.
	 * @param status notes; null becomes empty
	 */
	public void setStatus(String status) {
		this.status = status == null ? "" : status;
	}

	/**
	 * Returns whether an actual completion time has been recorded.
	 * @return true when completed
	 */
	@JsonIgnore
	public boolean isCompleted() {
		return actualDate != null;
	}
}
