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
package com.ewm.ground;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;
import net.runelite.client.config.ConfigManager;

public final class GroundMarkerIndex
{
	public static final String CONFIG_GROUP = "groundMarker";
	public static final String REGION_PREFIX = "region_";

	private final ConcurrentMap<Long, WorldTileMarker> markersByKey = new ConcurrentHashMap<>();
	private volatile List<WorldTileMarker> markersSnapshot = Collections.emptyList();

	private static long packKey(int plane, int worldX, int worldY)
	{
		long p = plane & 0x3;
		long x = worldX & 0x3FFFFFF;
		long y = worldY & 0x3FFFFFF;
		return (p << 52) | (x << 26) | y;
	}

	public void clear()
	{
		markersByKey.clear();
		markersSnapshot = Collections.emptyList();
	}

	public List<WorldTileMarker> snapshot()
	{
		return markersSnapshot;
	}

	@Nullable
	public WorldTileMarker getAt(int plane, int worldX, int worldY)
	{
		return markersByKey.get(packKey(plane, worldX, worldY));
	}

	public void loadAllForBounds(ConfigManager configManager, Gson gson, int minRx, int minRy, int maxRx, int maxRy)
	{
		final List<WorldTileMarker> out = new ArrayList<>();

		for (int rx = minRx; rx <= maxRx; rx++)
		{
			for (int ry = minRy; ry <= maxRy; ry++)
			{
				int regionId = (rx << 8) | ry;
				String key = REGION_PREFIX + regionId;

				String json = configManager.getConfiguration(CONFIG_GROUP, key);
				if (json == null || json.isEmpty())
				{
					continue;
				}

				List<GroundMarkerDTO> points;
				try
				{
					points = gson.fromJson(json, new TypeToken<List<GroundMarkerDTO>>()
					{
					}.getType());
				}
				catch (Exception ex)
				{
					continue;
				}

				if (points == null || points.isEmpty())
				{
					continue;
				}

				int regionBaseX = rx * 64;
				int regionBaseY = ry * 64;

				for (GroundMarkerDTO p : points)
				{
					if (p.getRegionId() != regionId)
					{
						continue;
					}

					int worldX = regionBaseX + p.getRegionX();
					int worldY = regionBaseY + p.getRegionY();
					int plane = p.getZ();

					WorldTileMarker m = new WorldTileMarker(worldX, worldY, plane, p.getColor(), p.getLabel());
					out.add(m);
				}
			}
		}

		markersByKey.clear();
		for (WorldTileMarker m : out)
		{
			markersByKey.put(packKey(m.getPlane(), m.getWorldX(), m.getWorldY()), m);
		}
		markersSnapshot = Collections.unmodifiableList(out);
	}
}
