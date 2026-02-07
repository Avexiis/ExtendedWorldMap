package com.ewm.ground;

import java.awt.Color;
import javax.annotation.Nullable;
import lombok.Getter;

public final class WorldTileMarker
{
	@Getter
	private final int worldX;
	@Getter
	private final int worldY;
	@Getter
	private final int plane;
	@Nullable
	private final Color color;
	@Nullable
	private final String label;

	public WorldTileMarker(int worldX, int worldY, int plane, @Nullable Color color, @Nullable String label)
	{
		this.worldX = worldX;
		this.worldY = worldY;
		this.plane = plane;
		this.color = color;
		this.label = label;
	}

	@Nullable
	public Color getColor()
	{
		return color;
	}

	@Nullable
	public String getLabel()
	{
		return label;
	}
}
