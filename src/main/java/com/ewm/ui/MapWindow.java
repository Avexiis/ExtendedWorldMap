package com.ewm.ui;

import com.ewm.ExtendedWorldMapConfig;
import com.ewm.store.FileManager;
import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;

public class MapWindow extends JFrame
{
	private final MapPanel panel;

	public MapWindow(Client client, ExtendedWorldMapConfig cfg, FileManager mapFiles, ConfigManager configManager, Gson gson)
	{
		super("Extended World Map");
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setMinimumSize(new Dimension(560, 420));
		setPreferredSize(new Dimension(1100, 800));
		setResizable(true);
		setLayout(new BorderLayout());

		panel = new MapPanel(client, cfg, mapFiles, configManager, gson);
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

		panel.loadMap();
	}

	public void refreshGroundMarkers()
	{
		panel.reloadGroundMarkersAsync();
	}
}
