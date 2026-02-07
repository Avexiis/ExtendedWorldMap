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
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

public final class MapReader implements Closeable
{
	private static final byte[] MAGIC = new byte[]{
		(byte) 'A', (byte) 'T', (byte) 'L', (byte) 'S', (byte) 'v', (byte) '1', (byte) 0x00, (byte) 0x00
	};

	private final RandomAccessFile raf;
	private final Header header;
	private final Map<String, TileEntry> index = new ConcurrentHashMap<>();

	private MapReader(RandomAccessFile raf) throws IOException
	{
		this.raf = raf;
		this.header = readHeader();
		readIndex();
	}

	private static void require(boolean cond, String msg) throws IOException
	{
		if (!cond)
		{
			throw new IOException("Map: " + msg);
		}
	}

	public static MapReader open(File file) throws IOException
	{
		return new MapReader(new RandomAccessFile(file, "r"));
	}

	private static String key(int lod, int z, int tx, int ty)
	{
		return lod + ":" + z + ":" + tx + ":" + ty;
	}

	public Header header()
	{
		return header;
	}

	private Header readHeader() throws IOException
	{
		raf.seek(0);

		byte[] magic8 = new byte[8];
		raf.readFully(magic8);

		boolean magicOk = true;
		for (int i = 0; i < 8; i++)
		{
			if (magic8[i] != MAGIC[i])
			{
				magicOk = false;
				break;
			}
		}
		require(magicOk, "bad magic");

		int version = readU32();
		require(version == 2, "unsupported version: " + version);

		int srcW = readU32();
		int srcH = readU32();
		int tile = readU32();

		int numLods = readU32();
		require(numLods > 0, "no LODs");

		int[] lods = new int[numLods];
		for (int i = 0; i < numLods; i++)
		{
			lods[i] = readU32();
		}

		int tilesXFull = readU32();
		int tilesYFull = readU32();

		int numLayers = readU32();
		require(numLayers > 0, "numLayers <= 0");

		long indexOff = readU64();
		long dataOff = readU64();

		require(indexOff > 0, "indexOffset invalid");
		require(dataOff > indexOff, "dataOffset invalid");

		return new Header(version, srcW, srcH, tile, lods, tilesXFull, tilesYFull, numLayers, indexOff, dataOff);
	}

	private void readIndex() throws IOException
	{
		raf.seek(header.indexOffset);

		final int entrySize = 36;
		long totalEntries = (header.dataOffset - header.indexOffset) / entrySize;

		for (long i = 0; i < totalEntries; i++)
		{
			int lod = readU32();
			int z = readU32();
			int tx = readU32();
			int ty = readU32();
			int w = readU32();
			int h = readU32();
			long rel = readU64();
			int len = readU32();

			TileEntry e = new TileEntry(lod, z, tx, ty, w, h, rel, len);
			index.put(key(lod, z, tx, ty), e);
		}
	}

	private int readU32() throws IOException
	{
		byte[] b = new byte[4];
		raf.readFully(b);
		return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt();
	}

	private long readU64() throws IOException
	{
		byte[] b = new byte[8];
		raf.readFully(b);
		return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getLong();
	}

	public TileEntry getEntry(int lod, int z, int tx, int ty)
	{
		return index.get(key(lod, z, tx, ty));
	}

	public BufferedImage readTileImage(int lod, int z, int tx, int ty) throws IOException
	{
		TileEntry e = getEntry(lod, z, tx, ty);
		if (e == null)
		{
			return null;
		}

		byte[] buf = new byte[e.length];
		synchronized (raf)
		{
			raf.seek(header.dataOffset + e.relOffset);
			raf.readFully(buf);
		}

		try (ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(buf)))
		{
			ImageReader reader = ImageIO.getImageReadersByFormatName("png").next();
			try
			{
				reader.setInput(iis, true, true);
				return reader.read(0);
			}
			finally
			{
				reader.dispose();
			}
		}
	}

	@Override
	public void close() throws IOException
	{
		raf.close();
	}

	public static final class Header
	{
		public final int version;
		public final int srcWidth;
		public final int srcHeight;
		public final int tilePx;
		public final int[] lods;
		public final int tilesXFull;
		public final int tilesYFull;
		public final int numLayers;
		public final long indexOffset;
		public final long dataOffset;

		Header(
			int version,
			int srcWidth,
			int srcHeight,
			int tilePx,
			int[] lods,
			int tilesXFull,
			int tilesYFull,
			int numLayers,
			long indexOffset,
			long dataOffset
		)
		{
			this.version = version;
			this.srcWidth = srcWidth;
			this.srcHeight = srcHeight;
			this.tilePx = tilePx;
			this.lods = lods;
			this.tilesXFull = tilesXFull;
			this.tilesYFull = tilesYFull;
			this.numLayers = numLayers;
			this.indexOffset = indexOffset;
			this.dataOffset = dataOffset;
		}
	}

	public static final class TileEntry
	{
		public final int lod;
		public final int z;
		public final int tx;
		public final int ty;
		public final int imgW;
		public final int imgH;
		public final long relOffset;
		public final int length;

		TileEntry(int lod, int z, int tx, int ty, int imgW, int imgH, long relOffset, int length)
		{
			this.lod = lod;
			this.z = z;
			this.tx = tx;
			this.ty = ty;
			this.imgW = imgW;
			this.imgH = imgH;
			this.relOffset = relOffset;
			this.length = length;
		}
	}
}
