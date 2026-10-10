/*
 * Copyright (C) 2026 SAR Management contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.OpenActionItem;

/**
 * Exports all open-action tracker records using the standard ICS 233-CG grid.
 */
public final class Ics233CgPdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
	/** {@inheritDoc} */
	@Override
	public String getFormKey() {
		return "ICS 233-CG";
	}

	/** {@inheritDoc} */
	@Override
	public void render(AppData data, Path outputFile) throws IOException {
		AppData source = data == null ? new AppData() : data;
		List<List<String>> rows = new ArrayList<>();
		if (source.getOpenActionItems() != null) {
			for (OpenActionItem item : source.getOpenActionItems()) {
				if (item == null) {
					continue;
				}
				rows.add(List.of(Integer.toString(item.getNumber()), Ics233PdfLayout.safe(item.getItem()),
						Ics233PdfLayout.safe(item.getPoc()), item.isBriefedToPoc() ? "X" : "",
						formatPdfDateTime(source, item.getStartDate()), Ics233PdfLayout.safe(item.getStatus()),
						formatPdfDateTime(source, item.getTargetDate()), formatPdfDateTime(source, item.getActualDate())));
			}
		}
		new Ics233PdfLayout().render(source, outputFile, "ICS 233-CG OPEN ACTION TRACKER", rows, false);
	}
}
