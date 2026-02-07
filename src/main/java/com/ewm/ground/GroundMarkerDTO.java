package com.ewm.ground;

import java.awt.Color;
import javax.annotation.Nullable;
import lombok.Getter;

public final class GroundMarkerDTO
{
	@Getter
	private int regionId;
	@Getter
	private int regionX;
	@Getter
	private int regionY;
	@Getter
	private int z;
	@Nullable
	private Color color;
	@Nullable
	private String label;

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
