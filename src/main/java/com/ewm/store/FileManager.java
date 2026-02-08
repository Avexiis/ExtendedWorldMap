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

import com.google.gson.Gson;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import javax.annotation.Nullable;
import net.runelite.client.RuneLite;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class FileManager
{
	public static final String MAP_FILE_NAME = "atlas_bg.atlas";

	public static final String MAP_DOWNLOAD_URL =
		"https://github.com/Avexiis/ExtendedMapViewer_Resources/raw/refs/heads/main/atlas_bg.atlas";

	private static final String HDR_ETAG = "ETag";
	private static final String HDR_LAST_MODIFIED = "Last-Modified";
	private static final String HDR_CONTENT_LENGTH = "Content-Length";
	private static final String HDR_IF_NONE_MATCH = "If-None-Match";
	private static final String HDR_IF_MODIFIED_SINCE = "If-Modified-Since";

	private static final Object MAP_UPDATE_LOCK = new Object();

	private final OkHttpClient http;
	private final String mapUrl;
	private final Gson gson;

	public FileManager(OkHttpClient http, String mapUrl, Gson gson)
	{
		this.http = Objects.requireNonNull(http, "http");
		this.mapUrl = (mapUrl == null || mapUrl.isEmpty()) ? MAP_DOWNLOAD_URL : mapUrl;
		this.gson = Objects.requireNonNull(gson, "gson");
	}

	private static long parseContentLength(@Nullable String s)
	{
		if (s == null)
		{
			return -1L;
		}
		try
		{
			return Long.parseLong(s.trim());
		}
		catch (Exception e)
		{
			return -1L;
		}
	}

	private static boolean sameNonEmpty(@Nullable String a, @Nullable String b)
	{
		if (a == null || a.trim().isEmpty())
		{
			return false;
		}
		if (b == null || b.trim().isEmpty())
		{
			return false;
		}
		return a.trim().equals(b.trim());
	}

	private static void sleepQuiet(long ms)
	{
		try
		{
			Thread.sleep(ms);
		}
		catch (InterruptedException ie)
		{
			Thread.currentThread().interrupt();
		}
	}

	public File getMapDirectory()
	{
		return new File(RuneLite.RUNELITE_DIR, "extendedworldmap");
	}

	public File getMapFile()
	{
		return new File(getMapDirectory(), MAP_FILE_NAME);
	}

	private File getMetaFile()
	{
		return new File(getMapDirectory(), MAP_FILE_NAME + ".meta");
	}

	@Nullable
	private LocalMeta readLocalMeta()
	{
		File meta = getMetaFile();
		if (!meta.isFile() || meta.length() <= 0)
		{
			return null;
		}

		try
		{
			byte[] bytes = Files.readAllBytes(meta.toPath());
			String json = new String(bytes, StandardCharsets.UTF_8);
			return gson.fromJson(json, LocalMeta.class);
		}
		catch (Throwable ignore)
		{
			return null;
		}
	}

	private void writeLocalMeta(RemoteInfo info) throws IOException
	{
		LocalMeta lm = new LocalMeta(info.etag, info.lastModified, info.sizeBytes);
		String json = gson.toJson(lm);
		Files.write(getMetaFile().toPath(), json.getBytes(StandardCharsets.UTF_8));
	}

	@Nullable
	private RemoteInfo fetchRemoteInfoHead()
	{
		Request req = new Request.Builder()
			.url(mapUrl)
			.head()
			.build();

		try (Response resp = http.newCall(req).execute())
		{
			if (!resp.isSuccessful())
			{
				return null;
			}

			String etag = resp.header(HDR_ETAG);
			String lastMod = resp.header(HDR_LAST_MODIFIED);

			long size = -1L;
			String cl = resp.header(HDR_CONTENT_LENGTH);
			size = parseContentLength(cl);

			return new RemoteInfo(etag, lastMod, size > 0 ? size : -1L);
		}
		catch (IOException e)
		{
			return null;
		}
	}

	private boolean shouldDownload(RemoteInfo remote, @Nullable LocalMeta local, File mapFile)
	{
		if (!mapFile.isFile() || mapFile.length() <= 0)
		{
			return true;
		}

		if (local == null)
		{
			return true;
		}

		if (remote != null)
		{
			if (sameNonEmpty(remote.etag, local.etag))
			{
				return false;
			}

			if (sameNonEmpty(remote.lastModified, local.lastModified))
			{
				return false;
			}

			if (remote.sizeBytes > 0 && local.sizeBytes > 0 && remote.sizeBytes == local.sizeBytes)
			{
				return (remote.etag != null && !remote.etag.trim().isEmpty())
					|| (remote.lastModified != null && !remote.lastModified.trim().isEmpty());
			}
		}

		return true;
	}

	public void ensureMapUpToDate(@Nullable ProgressListener progressListener) throws IOException
	{
		synchronized (MAP_UPDATE_LOCK)
		{
			File dir = getMapDirectory();
			if (!dir.isDirectory() && !dir.mkdirs())
			{
				throw new IOException("Failed to create map directory: " + dir.getAbsolutePath());
			}

			File target = getMapFile();
			File temp = new File(dir, MAP_FILE_NAME + ".part");

			RemoteInfo remoteHead = fetchRemoteInfoHead();
			LocalMeta localMeta = readLocalMeta();

			boolean needDownload = shouldDownload(remoteHead, localMeta, target);

			if (!needDownload)
			{
				return;
			}

			Request.Builder rb = new Request.Builder()
				.url(mapUrl)
				.get();

			if (localMeta != null)
			{
				if (localMeta.etag != null && !localMeta.etag.trim().isEmpty())
				{
					rb.header(HDR_IF_NONE_MATCH, localMeta.etag.trim());
				}
				if (localMeta.lastModified != null && !localMeta.lastModified.trim().isEmpty())
				{
					rb.header(HDR_IF_MODIFIED_SINCE, localMeta.lastModified.trim());
				}
			}

			Request req = rb.build();

			try (Response resp = http.newCall(req).execute())
			{
				if (resp.code() == 304)
				{
					if (remoteHead != null)
					{
						try
						{
							if (remoteHead.sizeBytes <= 0)
							{
								remoteHead.sizeBytes = target.length();
							}
							writeLocalMeta(remoteHead);
						}
						catch (Throwable ignore)
						{
						}
					}
					return;
				}

				if (!resp.isSuccessful())
				{
					throw new IOException("Map download failed: HTTP " + resp.code());
				}

				ResponseBody body = resp.body();
				if (body == null)
				{
					throw new IOException("Map download failed: empty response body");
				}

				String etag = resp.header(HDR_ETAG);
				String lastMod = resp.header(HDR_LAST_MODIFIED);

				long total = -1L;
				if (remoteHead != null && remoteHead.sizeBytes > 0)
				{
					total = remoteHead.sizeBytes;
				}
				else
				{
					long cl = body.contentLength();
					total = cl > 0 ? cl : -1L;
				}

				try (BufferedInputStream in = new BufferedInputStream(body.byteStream());
					 BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(temp)))
				{
					byte[] buf = new byte[1024 * 128];
					long readTotal = 0L;

					int n;
					while ((n = in.read(buf)) >= 0)
					{
						if (n == 0)
						{
							continue;
						}

						out.write(buf, 0, n);
						readTotal += n;

						if (progressListener != null)
						{
							progressListener.onProgress(readTotal, total);
						}
					}
				}
				catch (IOException e)
				{
					try
					{
						Files.deleteIfExists(temp.toPath());
					}
					catch (IOException ignore)
					{
					}
					throw e;
				}

				IOException last = null;
				for (int attempt = 0; attempt < 12; attempt++)
				{
					try
					{
						try
						{
							Files.move(temp.toPath(), target.toPath(),
								StandardCopyOption.REPLACE_EXISTING,
								StandardCopyOption.ATOMIC_MOVE);
						}
						catch (IOException e)
						{
							Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
						}

						last = null;
						break;
					}
					catch (IOException e)
					{
						last = e;
						sleepQuiet(150L + (attempt * 75L));
					}
				}

				if (last != null)
				{
					throw last;
				}

				long finalSize = target.length();
				long metaSize = (total > 0) ? total : finalSize;

				RemoteInfo saved = new RemoteInfo(etag, lastMod, metaSize);
				try
				{
					writeLocalMeta(saved);
				}
				catch (Throwable ignore)
				{
				}
			}
		}
	}

	public interface ProgressListener
	{
		void onProgress(long bytesDownloaded, long totalBytes);
	}

	private static final class RemoteInfo
	{
		@Nullable
		String etag;
		@Nullable
		String lastModified;
		long sizeBytes;

		RemoteInfo()
		{
		}

		RemoteInfo(@Nullable String etag, @Nullable String lastModified, long sizeBytes)
		{
			this.etag = etag;
			this.lastModified = lastModified;
			this.sizeBytes = sizeBytes;
		}
	}

	private static final class LocalMeta
	{
		@Nullable
		String etag;
		@Nullable
		String lastModified;
		long sizeBytes;

		LocalMeta(@Nullable String etag, @Nullable String lastModified, long sizeBytes)
		{
			this.etag = etag;
			this.lastModified = lastModified;
			this.sizeBytes = sizeBytes;
		}
	}
}
