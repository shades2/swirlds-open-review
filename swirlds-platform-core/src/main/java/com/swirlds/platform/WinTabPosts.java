/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */
package com.swirlds.platform;

import javax.swing.JLabel;

/**
 * The tab in the Browser window that shows available apps, running swirlds, and saved swirlds.
 */
class WinTabPosts extends WinBrowser.PrePaintableJPanel {
	private static final long serialVersionUID = 1L;

	public WinTabPosts() {
		JLabel label = new JLabel("There are no recent posts.");
		label.setFont(WinBrowser.FONT);
		add(label);
	}

	/** {@inheritDoc} */
	void prePaint() {
	}
}
