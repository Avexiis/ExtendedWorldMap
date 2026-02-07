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
package com.ewm;

import com.ewm.store.FileManager;
import com.ewm.ui.MapDock;
import com.ewm.ui.MapPanel;
import com.ewm.ui.MapWindow;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Container;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.menus.WidgetMenuOption;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import okhttp3.OkHttpClient;

@Slf4j
@Singleton
@PluginDescriptor(
	name = "Extended World Map",
	description = "Shows a full world map with all map regions visible in their real locations.",
	tags = {"worldmap", "world", "map", "extended", "extendedworldmap"}
)
public class ExtendedWorldMapPlugin extends Plugin
{
	private static final int WORLDMAP_ORB_WIDGET_ID = InterfaceID.Orbs.WORLDMAP;
	private static final int WORLDMAP_ORB_NOMAP_WIDGET_ID = InterfaceID.OrbsNomap.WORLDMAP;

	private static final String GROUND_MARKER_GROUP = "groundMarker";
	private static final String GROUND_MARKER_REGION_PREFIX = "region_";

	private final WidgetMenuOption dockMenu = new WidgetMenuOption(
		"Show", "Extended Map Dock", WORLDMAP_ORB_WIDGET_ID, WORLDMAP_ORB_NOMAP_WIDGET_ID
	);

	private final WidgetMenuOption windowMenu = new WidgetMenuOption(
		"Show", "Extended Map Window", WORLDMAP_ORB_WIDGET_ID, WORLDMAP_ORB_NOMAP_WIDGET_ID
	);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ExtendedWorldMapConfig config;

	@Inject
	private MenuManager menuManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	private FileManager fileManager;

	private MapWindow mapFrame;

	private MapDock mapDock;

	private MapPanel mapPanel;

	private static void detachFromParentIfNeeded(MapPanel panel)
	{
		if (panel == null)
		{
			return;
		}

		Container parent = panel.getParent();
		if (parent != null)
		{
			try
			{
				parent.remove(panel);
				parent.revalidate();
				parent.repaint();
			}
			catch (Throwable ignore)
			{
			}
		}
	}

	private boolean isClientReady()
	{
		return client != null
			&& client.getGameState() == GameState.LOGGED_IN
			&& client.getLocalPlayer() != null;
	}

	@Override
	protected void startUp()
	{
		fileManager = new FileManager(okHttpClient, FileManager.MAP_DOWNLOAD_URL);

		menuManager.addManagedCustomMenu(dockMenu, entry ->
		{
			clientThread.invokeLater(() ->
			{
				if (!isClientReady())
				{
					return;
				}

				boolean wantDock = mapDock == null || !mapDock.isDisplayable();
				setDockVisible(wantDock);
			});
		});

		menuManager.addManagedCustomMenu(windowMenu, entry ->
		{
			clientThread.invokeLater(() ->
			{
				if (!isClientReady())
				{
					return;
				}

				openWindow();
			});
		});
	}

	@Override
	protected void shutDown()
	{
		SwingUtilities.invokeLater(() ->
		{
			closeDockOnEdt();

			if (mapFrame != null)
			{
				mapFrame.dispose();
				mapFrame = null;
			}

			if (mapPanel != null)
			{
				mapPanel.shutdown();
				mapPanel = null;
			}

			menuManager.removeManagedCustomMenu(dockMenu);
			menuManager.removeManagedCustomMenu(windowMenu);

			fileManager = null;
		});
	}

	private MapPanel getOrCreatePanel()
	{
		if (mapPanel == null)
		{
			mapPanel = new MapPanel(client, config, fileManager, configManager, gson);
		}
		return mapPanel;
	}

	private void openWindow()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (!isClientReady())
			{
				return;
			}

			closeDockOnEdt();

			MapPanel panel = getOrCreatePanel();
			detachFromParentIfNeeded(panel);

			if (mapFrame == null || !mapFrame.isDisplayable())
			{
				mapFrame = new MapWindow(panel);
			}

			mapFrame.setVisible(true);
			mapFrame.toFront();

			panel.loadMapIfNeeded();
			mapFrame.refreshGroundMarkers();

			SwingUtilities.invokeLater(panel::focusPlayer);
		});
	}

	private void setDockVisible(boolean wantDock)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (!isClientReady())
			{
				return;
			}

			if (wantDock)
			{
				if (mapFrame != null && mapFrame.isDisplayable())
				{
					mapFrame.dispose();
					mapFrame = null;
				}

				MapPanel panel = getOrCreatePanel();
				detachFromParentIfNeeded(panel);

				if (mapDock == null || !mapDock.isDisplayable())
				{
					mapDock = new MapDock(panel, client.getCanvas(), configManager);
				}

				mapDock.openWithinOwner();

				panel.loadMapIfNeeded();
				mapDock.refreshGroundMarkers();

				SwingUtilities.invokeLater(panel::focusPlayer);
			}
			else
			{
				closeDockOnEdt();
			}
		});
	}

	private void closeDockOnEdt()
	{
		if (mapDock != null)
		{
			mapDock.dispose();
			mapDock = null;
		}
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGIN_SCREEN)
		{
			SwingUtilities.invokeLater(() ->
			{
				closeDockOnEdt();
				if (mapFrame != null)
				{
					mapFrame.dispose();
					mapFrame = null;
				}
			});
		}
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event)
	{
		if (!GROUND_MARKER_GROUP.equals(event.getGroup()))
		{
			return;
		}

		String key = event.getKey();
		if (key == null || !key.startsWith(GROUND_MARKER_REGION_PREFIX))
		{
			return;
		}

		SwingUtilities.invokeLater(() ->
		{
			if (mapDock != null && mapDock.isDisplayable())
			{
				mapDock.refreshGroundMarkers();
			}
			if (mapFrame != null && mapFrame.isDisplayable())
			{
				mapFrame.refreshGroundMarkers();
			}
		});
	}

	@Provides
	ExtendedWorldMapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ExtendedWorldMapConfig.class);
	}
}
