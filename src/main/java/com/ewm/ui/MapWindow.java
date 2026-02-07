/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.ewm.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

public class MapWindow extends JFrame
{
	private final MapPanel panel;

	public MapWindow(MapPanel sharedPanel)
	{
		super("Extended World Map");
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setMinimumSize(new Dimension(560, 420));
		setPreferredSize(new Dimension(1100, 800));
		setResizable(true);
		setLayout(new BorderLayout());

		panel = sharedPanel;
		PanelSidebar sidebar = new PanelSidebar(panel);

		JPanel content = new JPanel(new BorderLayout());
		content.add(panel, BorderLayout.CENTER);
		content.add(sidebar, BorderLayout.EAST);

		add(content, BorderLayout.CENTER);

		pack();

		try
		{
			GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
			for (GraphicsDevice dev : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
			{
				if (dev.getDefaultConfiguration().getBounds().contains(getMousePosition()))
				{
					gd = dev;
					break;
				}
			}
			GraphicsConfiguration gc = gd.getDefaultConfiguration();
			Dimension scr = gc.getBounds().getSize();
			Dimension win = getSize();

			int x = gc.getBounds().x + (scr.width - win.width) / 2;
			int y = gc.getBounds().y + (scr.height - win.height) / 2;
			setLocation(x, y);
		}
		catch (Throwable ignore)
		{
			setLocationByPlatform(true);
		}

		panel.loadMapIfNeeded();

		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				panel.setTrackPlayer(false);
			}

			@Override
			public void windowClosed(WindowEvent e)
			{
				panel.setTrackPlayer(false);
			}
		});
	}

	public void refreshGroundMarkers()
	{
		panel.reloadGroundMarkersAsync();
	}
}
