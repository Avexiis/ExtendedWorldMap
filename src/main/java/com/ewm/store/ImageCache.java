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
