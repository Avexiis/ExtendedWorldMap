package com.ewm.store;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ImageCache extends LinkedHashMap<String, BufferedImage>
{
	private final long budgetBytes;
	private long liveBytes = 0L;

	public ImageCache(long budgetBytes)
	{
		super(512, 0.75f, true);
		this.budgetBytes = Math.max(64L * 1024L * 1024L, budgetBytes);
	}

	private static String key(int lod, int plane, int tx, int ty)
	{
		return lod + ":" + plane + ":" + tx + ":" + ty;
	}

	private static long approx(BufferedImage bi)
	{
		if (bi == null)
		{
			return 0;
		}
		return (long) bi.getWidth() * (long) bi.getHeight() * 4L;
	}

	public synchronized BufferedImage get(int lod, int plane, int tx, int ty)
	{
		return super.get(key(lod, plane, tx, ty));
	}

	public synchronized void put(int lod, int plane, int tx, int ty, BufferedImage img)
	{
		BufferedImage prev = super.put(key(lod, plane, tx, ty), img);
		if (prev != null)
		{
			liveBytes -= approx(prev);
		}
		if (img != null)
		{
			liveBytes += approx(img);
		}
		trimToBudget();
	}

	private void trimToBudget()
	{
		while (liveBytes > budgetBytes && !isEmpty())
		{
			Map.Entry<String, BufferedImage> eldest = entrySet().iterator().next();
			BufferedImage img = eldest.getValue();
			liveBytes -= approx(img);
			remove(eldest.getKey());
		}
	}

	@Override
	public synchronized BufferedImage remove(Object key)
	{
		BufferedImage prev = super.remove(key);
		if (prev != null)
		{
			liveBytes -= approx(prev);
		}
		return prev;
	}
}
