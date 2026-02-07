package com.ewm.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class IOUtil
{
	private IOUtil()
	{
	}

	public static byte[] readFully(InputStream in) throws IOException
	{
		byte[] buf = new byte[8192];
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int n;
		while ((n = in.read(buf, 0, buf.length)) >= 0)
		{
			if (n == 0)
			{
				int b = in.read();
				if (b < 0)
				{
					break;
				}
				out.write(b);
			}
			else
			{
				out.write(buf, 0, n);
			}
		}
		return out.toByteArray();
	}
}
