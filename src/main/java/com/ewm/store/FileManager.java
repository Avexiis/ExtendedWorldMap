package com.ewm.store;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.LongConsumer;
import net.runelite.client.RuneLite;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class FileManager
{
	public static final String MAP_FILE_NAME = "atlas_bg.atlas";

	public static final String MAP_DOWNLOAD_URL = "https://github.com/Avexiis/ExtendedMapViewer_Resources/raw/refs/heads/main/atlas_bg.atlas";

	private final OkHttpClient http;
	private final String mapUrl;

	public FileManager(OkHttpClient http, String mapUrl)
	{
		this.http = http;
		this.mapUrl = mapUrl == null || mapUrl.isEmpty() ? MAP_DOWNLOAD_URL : mapUrl;
	}

	public File getMapDirectory()
	{
		return new File(RuneLite.RUNELITE_DIR, "extendedworldmap");
	}

	public File getMapFile()
	{
		return new File(getMapDirectory(), MAP_FILE_NAME);
	}

	public boolean isMapPresent()
	{
		File f = getMapFile();
		return f.isFile() && f.length() > 0;
	}

	public void ensureMapPresent(LongConsumer bytesDownloadedCallback) throws IOException
	{
		if (isMapPresent())
		{
			return;
		}

		File dir = getMapDirectory();
		if (!dir.isDirectory() && !dir.mkdirs())
		{
			throw new IOException("Failed to create map directory: " + dir.getAbsolutePath());
		}

		File target = getMapFile();
		File temp = new File(dir, MAP_FILE_NAME + ".part");

		Request req = new Request.Builder()
			.url(mapUrl)
			.get()
			.build();

		try (Response resp = http.newCall(req).execute())
		{
			if (!resp.isSuccessful())
			{
				throw new IOException("Map download failed: HTTP " + resp.code());
			}

			ResponseBody body = resp.body();
			if (body == null)
			{
				throw new IOException("Map download failed: empty response body");
			}

			long total = body.contentLength();

			try (InputStream in = new BufferedInputStream(body.byteStream());
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

					if (bytesDownloadedCallback != null)
					{
						bytesDownloadedCallback.accept(total > 0 ? readTotal : -readTotal);
					}
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

		try
		{
			Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e)
		{
			Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
