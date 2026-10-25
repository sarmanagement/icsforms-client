/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;

/**
 * A radio channel in the communications plan. Radio settings remain raw strings
 * so precision, unsupported settings, and imported programming data are not lost.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RadioChannel {
	public static final String TX_POLICY_UNKNOWN = "UNKNOWN";
	public static final String TX_POLICY_TRANSMIT = "TRANSMIT";
	public static final String TX_POLICY_RECEIVE_ONLY = "RECEIVE_ONLY";

	private String channelId = UUID.randomUUID().toString();
	private String zoneGroup = "";
	private String channelNumber = "";
	private String function = "";
	private String channelName = "";
	private String assignment = "";
	private String rxFrequency = "";
	private String rxBandwidth = "";
	private String rxToneNac = "";
	private String txFrequency = "";
	private String txBandwidth = "";
	private String txToneNac = "";
	private String mode = "";
	private String digitalProtocol = "";
	private String system = "";
	private String remarks = "";
	private boolean includeOnForm = true;
	private String txPolicy = TX_POLICY_UNKNOWN;
	private String coverageGuidance = "";
	private String relayGuidance = "";
	private RadioCheckStatus radioCheckStatus = RadioCheckStatus.NOT_RECORDED;
	private String radioCheckedBy = "";
	private LocalDateTime radioCheckedAt;
	private String radioCheckNotes = "";
	private Map<String, String> programmingMetadata = new LinkedHashMap<>();

	/**
	 * Returns the stable channel identity.
	 * @return channel UUID string
	 */
	public String getChannelId() {
		return channelId;
	}

	/**
	 * Sets the stable channel identity.
	 * @param channelId identity; null or blank generates a UUID
	 */
	public void setChannelId(String channelId) {
		this.channelId = channelId == null || channelId.isBlank() ? UUID.randomUUID().toString() : channelId;
	}

	/**
	 * Returns the zone or group.
	 * @return raw zone/group
	 */
	public String getZoneGroup() {
		return zoneGroup;
	}

	/**
	 * Sets the zone or group.
	 * @param zoneGroup raw zone/group; null becomes empty
	 */
	public void setZoneGroup(String zoneGroup) {
		this.zoneGroup = safe(zoneGroup);
	}

	/**
	 * Returns the radio channel number.
	 * @return raw channel number
	 */
	public String getChannelNumber() {
		return channelNumber;
	}

	/**
	 * Sets the radio channel number.
	 * @param channelNumber raw channel number; null becomes empty
	 */
	public void setChannelNumber(String channelNumber) {
		this.channelNumber = safe(channelNumber);
	}

	/**
	 * Returns the operational function.
	 * @return raw function
	 */
	public String getFunction() {
		return function;
	}

	/**
	 * Sets the operational function.
	 * @param function raw function; null becomes empty
	 */
	public void setFunction(String function) {
		this.function = safe(function);
	}

	/**
	 * Returns the channel name.
	 * @return raw channel name
	 */
	public String getChannelName() {
		return channelName;
	}

	/**
	 * Sets the channel name.
	 * @param channelName raw name; null becomes empty
	 */
	public void setChannelName(String channelName) {
		this.channelName = safe(channelName);
	}

	/**
	 * Returns the channel assignment.
	 * @return raw assignment
	 */
	public String getAssignment() {
		return assignment;
	}

	/**
	 * Sets the channel assignment.
	 * @param assignment raw assignment; null becomes empty
	 */
	public void setAssignment(String assignment) {
		this.assignment = safe(assignment);
	}

	/**
	 * Returns the receive frequency without numeric conversion.
	 * @return raw receive frequency
	 */
	public String getRxFrequency() {
		return rxFrequency;
	}

	/**
	 * Sets the receive frequency without rounding.
	 * @param rxFrequency raw frequency; null becomes empty
	 */
	public void setRxFrequency(String rxFrequency) {
		this.rxFrequency = safe(rxFrequency);
	}

	/**
	 * Returns the receive bandwidth.
	 * @return raw receive bandwidth
	 */
	public String getRxBandwidth() {
		return rxBandwidth;
	}

	/**
	 * Sets the receive bandwidth.
	 * @param rxBandwidth raw bandwidth; null becomes empty
	 */
	public void setRxBandwidth(String rxBandwidth) {
		this.rxBandwidth = safe(rxBandwidth);
	}

	/**
	 * Returns the receive tone or network access code.
	 * @return raw receive tone/NAC
	 */
	public String getRxToneNac() {
		return rxToneNac;
	}

	/**
	 * Sets the receive tone or network access code.
	 * @param rxToneNac raw tone/NAC; null becomes empty
	 */
	public void setRxToneNac(String rxToneNac) {
		this.rxToneNac = safe(rxToneNac);
	}

	/**
	 * Returns the transmit frequency without numeric conversion.
	 * @return raw transmit frequency
	 */
	public String getTxFrequency() {
		return txFrequency;
	}

	/**
	 * Sets the transmit frequency without rounding.
	 * @param txFrequency raw frequency; null becomes empty
	 */
	public void setTxFrequency(String txFrequency) {
		this.txFrequency = safe(txFrequency);
	}

	/**
	 * Returns the transmit bandwidth.
	 * @return raw transmit bandwidth
	 */
	public String getTxBandwidth() {
		return txBandwidth;
	}

	/**
	 * Sets the transmit bandwidth.
	 * @param txBandwidth raw bandwidth; null becomes empty
	 */
	public void setTxBandwidth(String txBandwidth) {
		this.txBandwidth = safe(txBandwidth);
	}

	/**
	 * Returns the transmit tone or network access code.
	 * @return raw transmit tone/NAC
	 */
	public String getTxToneNac() {
		return txToneNac;
	}

	/**
	 * Sets the transmit tone or network access code.
	 * @param txToneNac raw tone/NAC; null becomes empty
	 */
	public void setTxToneNac(String txToneNac) {
		this.txToneNac = safe(txToneNac);
	}

	/**
	 * Returns the ICS analog/digital/mixed mode, preserving unknown values.
	 * @return raw ICS mode, ordinarily A, D, or M
	 */
	public String getMode() {
		return mode;
	}

	/**
	 * Sets the ICS mode without coercing imported unknown values.
	 * @param mode raw mode; null becomes empty
	 */
	public void setMode(String mode) {
		this.mode = safe(mode);
	}

	/**
	 * Returns the digital protocol separately from the ICS mode.
	 * @return raw digital protocol
	 */
	public String getDigitalProtocol() {
		return digitalProtocol;
	}

	/**
	 * Sets the digital protocol.
	 * @param digitalProtocol raw protocol; null becomes empty
	 */
	public void setDigitalProtocol(String digitalProtocol) {
		this.digitalProtocol = safe(digitalProtocol);
	}

	/**
	 * Returns the trunked radio system.
	 * @return raw system name
	 */
	public String getSystem() {
		return system;
	}

	/**
	 * Sets the trunked radio system.
	 * @param system raw system name; null becomes empty
	 */
	public void setSystem(String system) {
		this.system = safe(system);
	}

	/**
	 * Returns channel remarks.
	 * @return raw remarks
	 */
	public String getRemarks() {
		return remarks;
	}

	/**
	 * Sets channel remarks.
	 * @param remarks raw remarks; null becomes empty
	 */
	public void setRemarks(String remarks) {
		this.remarks = safe(remarks);
	}

	/**
	 * Returns whether this inventory channel appears on the ICS 205.
	 * @return true when included
	 */
	public boolean isIncludeOnForm() {
		return includeOnForm;
	}

	/**
	 * Selects this channel for form output without removing inventory data.
	 * @param includeOnForm true to include
	 */
	public void setIncludeOnForm(boolean includeOnForm) {
		this.includeOnForm = includeOnForm;
	}

	/**
	 * Returns the explicit transmit policy, independently of frequency presence.
	 * @return UNKNOWN, TRANSMIT, RECEIVE_ONLY, or a preserved future value
	 */
	public String getTxPolicy() {
		return txPolicy;
	}

	/**
	 * Sets transmit permission without inferring it from receive frequency.
	 * @param txPolicy raw policy; null or blank becomes UNKNOWN
	 */
	public void setTxPolicy(String txPolicy) {
		this.txPolicy = txPolicy == null || txPolicy.isBlank() ? TX_POLICY_UNKNOWN : txPolicy;
	}

	/**
	 * Returns operator-entered coverage guidance.
	 *
	 * @return coverage guidance.
	 */
	public String getCoverageGuidance() {
		return coverageGuidance;
	}

	/**
	 * Sets operator-entered coverage guidance.
	 *
	 * @param coverageGuidance coverage guidance; null becomes empty.
	 */
	public void setCoverageGuidance(String coverageGuidance) {
		this.coverageGuidance = safe(coverageGuidance);
	}

	/**
	 * Returns operator-entered relay guidance.
	 *
	 * @return relay guidance.
	 */
	public String getRelayGuidance() {
		return relayGuidance;
	}

	/**
	 * Sets operator-entered relay guidance.
	 *
	 * @param relayGuidance relay guidance; null becomes empty.
	 */
	public void setRelayGuidance(String relayGuidance) {
		this.relayGuidance = safe(relayGuidance);
	}

	/**
	 * Returns the explicit radio-check result.
	 *
	 * @return radio-check status, never null.
	 */
	public RadioCheckStatus getRadioCheckStatus() {
		return radioCheckStatus == null ? RadioCheckStatus.NOT_RECORDED : radioCheckStatus;
	}

	/**
	 * Sets the explicit radio-check result.
	 *
	 * @param radioCheckStatus result; null becomes not recorded.
	 */
	public void setRadioCheckStatus(RadioCheckStatus radioCheckStatus) {
		this.radioCheckStatus = radioCheckStatus == null ? RadioCheckStatus.NOT_RECORDED : radioCheckStatus;
	}

	/**
	 * Returns the person who recorded the radio check.
	 *
	 * @return checker name.
	 */
	public String getRadioCheckedBy() {
		return radioCheckedBy;
	}

	/**
	 * Sets the person who recorded the radio check.
	 *
	 * @param radioCheckedBy checker name; null becomes empty.
	 */
	public void setRadioCheckedBy(String radioCheckedBy) {
		this.radioCheckedBy = safe(radioCheckedBy);
	}

	/**
	 * Returns the radio-check time.
	 *
	 * @return check time, or null.
	 */
	public LocalDateTime getRadioCheckedAt() {
		return radioCheckedAt;
	}

	/**
	 * Sets the radio-check time.
	 *
	 * @param radioCheckedAt check time, or null.
	 */
	public void setRadioCheckedAt(LocalDateTime radioCheckedAt) {
		this.radioCheckedAt = radioCheckedAt;
	}

	/**
	 * Returns operator-entered radio-check notes.
	 *
	 * @return check notes.
	 */
	public String getRadioCheckNotes() {
		return radioCheckNotes;
	}

	/**
	 * Sets operator-entered radio-check notes.
	 *
	 * @param radioCheckNotes check notes; null becomes empty.
	 */
	public void setRadioCheckNotes(String radioCheckNotes) {
		this.radioCheckNotes = safe(radioCheckNotes);
	}

	/**
	 * Returns preserved radio-programming settings, including unknown columns.
	 * @return mutable metadata map, never null
	 */
	public Map<String, String> getProgrammingMetadata() {
		return programmingMetadata;
	}

	/**
	 * Replaces preserved programming settings with an independent map.
	 * @param programmingMetadata raw settings; null becomes empty
	 */
	public void setProgrammingMetadata(Map<String, String> programmingMetadata) {
		this.programmingMetadata = programmingMetadata == null
				? new LinkedHashMap<>() : new LinkedHashMap<>(programmingMetadata);
	}

	/**
	 * Copies all values and metadata, retaining the stable external-link identity.
	 * @return independent channel copy
	 */
	public RadioChannel deepCopy() {
		RadioChannel copy = new RadioChannel();
		copy.channelId = channelId;
		copy.zoneGroup = zoneGroup;
		copy.channelNumber = channelNumber;
		copy.function = function;
		copy.channelName = channelName;
		copy.assignment = assignment;
		copy.rxFrequency = rxFrequency;
		copy.rxBandwidth = rxBandwidth;
		copy.rxToneNac = rxToneNac;
		copy.txFrequency = txFrequency;
		copy.txBandwidth = txBandwidth;
		copy.txToneNac = txToneNac;
		copy.mode = mode;
		copy.digitalProtocol = digitalProtocol;
		copy.system = system;
		copy.remarks = remarks;
		copy.includeOnForm = includeOnForm;
		copy.txPolicy = txPolicy;
		copy.coverageGuidance = coverageGuidance;
		copy.relayGuidance = relayGuidance;
		copy.radioCheckStatus = radioCheckStatus;
		copy.radioCheckedBy = radioCheckedBy;
		copy.radioCheckedAt = radioCheckedAt;
		copy.radioCheckNotes = radioCheckNotes;
		copy.setProgrammingMetadata(programmingMetadata);
		return copy;
	}

	/**
	 * Converts null text to empty without trimming or otherwise altering raw data.
	 * @param value raw text
	 * @return non-null text
	 */
	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
