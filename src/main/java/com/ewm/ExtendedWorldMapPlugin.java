package com.ewm;

import com.ewm.store.FileManager;
import com.ewm.ui.Dock;
import com.ewm.ui.Window;
import com.google.inject.Binder;
import com.google.inject.Provides;
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

	private FileManager fileManager;

	private Window mapFrame;
	private Dock mapDock;

	private boolean isClientReady()
	{
		return client != null
			&& client.getGameState() == GameState.LOGGED_IN
			&& client.getLocalPlayer() != null;
	}

	@Override
	public void configure(Binder binder)
	{
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

			menuManager.removeManagedCustomMenu(dockMenu);
			menuManager.removeManagedCustomMenu(windowMenu);

			fileManager = null;
		});
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

			if (mapFrame == null || !mapFrame.isDisplayable())
			{
				mapFrame = new Window(client, config, fileManager);
			}

			mapFrame.setVisible(true);
			mapFrame.toFront();
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
				if (mapDock == null || !mapDock.isDisplayable())
				{
					mapDock = new Dock(client, config, fileManager, client.getCanvas());
				}
				mapDock.openWithinOwner();
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

	@Provides
	ExtendedWorldMapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ExtendedWorldMapConfig.class);
	}
}
