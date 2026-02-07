package com.ewm.ui;

import com.ewm.ExtendedWorldMapConfig;
import com.ewm.store.FileManager;
import com.ewm.io.IOUtil;
import com.ewm.store.MapReader;
import com.ewm.store.ImageCache;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;

class MapPanel extends JPanel
{
	private static final int GAME_REGION_SIZE = 64;
	private static final int IMAGE_REGION_SIZE = 256;
	private static final int PIXELS_PER_GAME_TILE = 4;

	private static final double MIN_ZOOM = 0.08;
	private static final double MAX_ZOOM = 4.00;

	private static final int MIN_RX = 15;
	private static final int MIN_RY = 19;
	private static final int MAX_RX = 65;
	private static final int MAX_RY = 196;

	private final Client client;
	private final ExtendedWorldMapConfig cfg;
	private final FileManager mapFiles;

	private final ImageCache tileCache;
	private final Set<String> inflight = ConcurrentHashMap.newKeySet();

	private final ExecutorService loader = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "ExtendedWorldMap-TileLoader");
		t.setDaemon(true);
		return t;
	});

	private MapReader map;
	private int colsFull;
	private int rowsFull;
	private int numPlanes = 1;
	private int currentPlane = 0;

	private BufferedImage[] previewQuarter;
	private ImageIcon hereIcon;

	private boolean showPlayer = true;
	private boolean showGrid = false;
	private boolean showRxRy = false;
	private boolean showRegionId = false;
	private boolean trackPlayer = false;
	private boolean dockShiftDragEnabled = false;

	private double zoom = 1.0;
	private double viewX = 0.0;
	private double viewY = 0.0;

	private int minRx;
	private int minRy;
	private int maxRx;
	private int maxRy;
	private int cols;
	private int rows;
	private int totalW;
	private int totalH;

	private final Timer repaintTimer = new Timer(66, e ->
	{
		if (trackPlayer)
		{
			centerOnPlayer(false);
		}
		repaint();
	});

	private int mouseX = -1;
	private int mouseY = -1;

	private Rectangle playerIconBounds = null;
	private String playerIconTooltip = null;

	MapPanel(Client client, ExtendedWorldMapConfig cfg, FileManager mapFiles)
	{
		this.client = client;
		this.cfg = cfg;
		this.mapFiles = mapFiles;
		this.tileCache = new ImageCache((long) cfg.cacheBudgetMB() * 1024L * 1024L);

		setBackground(Color.BLACK);
		setDoubleBuffered(true);

		setToolTipText("");
		ToolTipManager.sharedInstance().registerComponent(this);

		MouseAdapter mouse = new MouseAdapter()
		{
			private int lastX;
			private int lastY;
			private boolean dragging;

			@Override
			public void mousePressed(MouseEvent e)
			{
				requestFocusInWindow();
				lastX = e.getX();
				lastY = e.getY();
				dragging = true;
				setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				dragging = false;
				setCursor(Cursor.getDefaultCursor());
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				mouseX = -1;
				mouseY = -1;
				repaint();
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e)
			{
				mouseX = e.getX();
				mouseY = e.getY();

				double oldZoom = zoom;
				double factor = Math.pow(1.1, -e.getPreciseWheelRotation());
				zoom = clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);

				double mx = e.getX();
				double my = e.getY();
				double vx = viewX + mx / oldZoom;
				double vy = viewY + my / oldZoom;
				viewX = vx - mx / zoom;
				viewY = vy - my / zoom;

				clampViewLoose();
				repaint();
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				mouseX = e.getX();
				mouseY = e.getY();

				if (!dragging || (dockShiftDragEnabled && e.isShiftDown()))
				{
					return;
				}
				int dx = e.getX() - lastX;
				int dy = e.getY() - lastY;
				lastX = e.getX();
				lastY = e.getY();
				viewX -= dx / zoom;
				viewY -= dy / zoom;
				clampViewLoose();
				repaint();
			}

			@Override
			public void mouseMoved(MouseEvent e)
			{
				mouseX = e.getX();
				mouseY = e.getY();
				repaint();
			}
		};

		addMouseListener(mouse);
		addMouseMotionListener(mouse);
		addMouseWheelListener(mouse);

		repaintTimer.setCoalesce(true);
		repaintTimer.start();
	}

	private static double prefetchMarginLogical(double zoom)
	{
		double base = 128.0;
		return base * Math.max(0.8, Math.min(2.0, 1.0 / Math.sqrt(zoom)));
	}

	private static double clamp(double v, double lo, double hi)
	{
		return v < lo ? lo : (v > hi ? hi : v);
	}

	private static ImageIcon loadGifIcon(String path)
	{
		try (InputStream in = MapPanel.class.getResourceAsStream(path))
		{
			if (in == null)
			{
				return null;
			}
			byte[] bytes = IOUtil.readFully(in);
			return new ImageIcon(bytes);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	private static String capitalizeFirst(String s)
	{
		if (s == null)
		{
			return null;
		}
		String t = s.trim();
		if (t.isEmpty())
		{
			return null;
		}
		char c0 = t.charAt(0);
		char up = Character.toUpperCase(c0);
		if (t.length() == 1)
		{
			return String.valueOf(up);
		}
		return up + t.substring(1);
	}

	void setDockShiftDragEnabled(boolean b)
	{
		dockShiftDragEnabled = b;
	}

	void setShowPlayer(boolean b)
	{
		showPlayer = b;
		repaint();
	}

	void setShowGrid(boolean b)
	{
		showGrid = b;
		repaint();
	}

	void setShowRxRy(boolean b)
	{
		showRxRy = b;
		repaint();
	}

	void setShowRegionId(boolean b)
	{
		showRegionId = b;
		repaint();
	}

	void setTrackPlayer(boolean b)
	{
		trackPlayer = b;
		if (b)
		{
			centerOnPlayer(true);
		}
		repaint();
	}

	void setPlane(int plane)
	{
		int clamped = plane;
		if (clamped < 0)
		{
			clamped = 0;
		}
		int maxIndex = Math.max(1, numPlanes) - 1;
		if (clamped > maxIndex)
		{
			clamped = maxIndex;
		}
		if (clamped == this.currentPlane)
		{
			return;
		}
		this.currentPlane = clamped;
		repaint();
	}

	void loadMap()
	{
		final Window owner = getParentWindow();
		final LoadingDialog dlg = LoadingDialog.show(owner, "Preparing map...");
		dlg.setIndeterminate();
		dlg.followOwner(owner);

		loader.execute(() ->
		{
			try
			{
				dlg.setStatusText("First time use - downloading extended map...");
				mapFiles.ensureMapPresent(bytes ->
				{
					long abs = Math.abs(bytes);
					dlg.setBytesDownloaded(abs);
				});

				dlg.setStatusText("Opening map...");

				map = MapReader.open(mapFiles.getMapFile());
				numPlanes = Math.max(1, map.header().numLayers);

				minRx = MIN_RX;
				minRy = MIN_RY;
				maxRx = MAX_RX;
				maxRy = MAX_RY;

				cols = (maxRx - minRx + 1);
				rows = (maxRy - minRy + 1);
				totalW = cols * GAME_REGION_SIZE;
				totalH = rows * GAME_REGION_SIZE;

				colsFull = (int) Math.ceil((map.header().srcWidth) / (double) IMAGE_REGION_SIZE);
				rowsFull = (int) Math.ceil((map.header().srcHeight) / (double) IMAGE_REGION_SIZE);

				previewQuarter = new BufferedImage[numPlanes];
				for (int z = 0; z < numPlanes; z++)
				{
					previewQuarter[z] = buildPreview(LOD.QUARTER, z);
				}

				hereIcon = loadGifIcon("/extendedworldmap/You_are_here.gif");
			}
			catch (Throwable t)
			{
				t.printStackTrace();
			}
			finally
			{
				SwingUtilities.invokeLater(() ->
				{
					dlg.close();
					if (!centerOnPlayer(true))
					{
						zoom = clamp(1.0, MIN_ZOOM, MAX_ZOOM);
						centerCanvas();
					}
					repaint();
				});
			}
		});
	}

	private BufferedImage buildPreview(LOD lod, int planeZ) throws Exception
	{
		int subs = lod.subsample;
		int imgW = map.header().srcWidth / subs + ((map.header().srcWidth % subs) != 0 ? 1 : 0);
		int imgH = map.header().srcHeight / subs + ((map.header().srcHeight % subs) != 0 ? 1 : 0);

		int tilesX = (int) Math.ceil(imgW / (double) IMAGE_REGION_SIZE);
		int tilesY = (int) Math.ceil(imgH / (double) IMAGE_REGION_SIZE);

		BufferedImage out = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();

		for (int ty = 0; ty < tilesY; ty++)
		{
			for (int tx = 0; tx < tilesX; tx++)
			{
				BufferedImage tile = map.readTileImage(lod.subsample, planeZ, tx, ty);
				if (tile != null)
				{
					int x = tx * IMAGE_REGION_SIZE;
					int y = ty * IMAGE_REGION_SIZE;
					g.drawImage(tile, x, y, null);
				}
			}
		}

		g.dispose();
		return out;
	}

	@Override
	protected void paintComponent(Graphics g0)
	{
		super.paintComponent(g0);
		if (map == null)
		{
			return;
		}

		playerIconBounds = null;
		playerIconTooltip = null;

		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		AffineTransform old = g.getTransform();
		g.scale(zoom, zoom);
		g.translate(-viewX, -viewY);

		if (previewQuarter != null && currentPlane >= 0 && currentPlane < previewQuarter.length)
		{
			BufferedImage preview = previewQuarter[currentPlane];
			if (preview != null)
			{
				g.drawImage(preview, 0, 0, preview.getWidth(), preview.getHeight(), null);
			}
		}

		drawTiles(g, LOD.forZoom(zoom));
		drawGrid(g);
		drawLabels(g);

		g.setTransform(old);
		drawPlayerIcon(g);

		g.dispose();
	}

	@Override
	public String getToolTipText(MouseEvent event)
	{
		if (playerIconBounds == null || playerIconTooltip == null)
		{
			return null;
		}
		if (event == null)
		{
			return null;
		}
		return playerIconBounds.contains(event.getPoint()) ? playerIconTooltip : null;
	}

	private void drawTiles(Graphics2D g, LOD lod)
	{
		double vw = getWidth() / zoom;
		double vh = getHeight() / zoom;
		double margin = prefetchMarginLogical(zoom);

		int lx1 = (int) Math.floor(viewX - margin);
		int ly1 = (int) Math.floor(viewY - margin);
		int lx2 = (int) Math.ceil(viewX + vw + margin);
		int ly2 = (int) Math.ceil(viewY + vh + margin);

		lx1 = Math.max(0, lx1);
		ly1 = Math.max(0, ly1);
		lx2 = Math.min(totalW, lx2);
		ly2 = Math.min(totalH, ly2);

		int fullX1 = lx1 * PIXELS_PER_GAME_TILE;
		int fullY1 = ly1 * PIXELS_PER_GAME_TILE;
		int fullX2 = lx2 * PIXELS_PER_GAME_TILE;
		int fullY2 = ly2 * PIXELS_PER_GAME_TILE;

		int tX1 = Math.floorDiv(fullX1, IMAGE_REGION_SIZE * lod.subsample);
		int tY1 = Math.floorDiv(fullY1, IMAGE_REGION_SIZE * lod.subsample);
		int tX2 = Math.floorDiv(Math.max(fullX2 - 1, 0), IMAGE_REGION_SIZE * lod.subsample);
		int tY2 = Math.floorDiv(Math.max(fullY2 - 1, 0), IMAGE_REGION_SIZE * lod.subsample);

		List<int[]> order = new ArrayList<>();
		double cx = viewX + vw / 2.0;
		double cy = viewY + vh / 2.0;

		for (int ty = tY1; ty <= tY2; ty++)
		{
			for (int tx = tX1; tx <= tX2; tx++)
			{
				order.add(new int[]{tx, ty});
			}
		}

		order.sort(Comparator.comparingDouble(t ->
		{
			int tx = t[0];
			int ty = t[1];

			int px1 = (tx * IMAGE_REGION_SIZE * lod.subsample) / PIXELS_PER_GAME_TILE;
			int py1 = (ty * IMAGE_REGION_SIZE * lod.subsample) / PIXELS_PER_GAME_TILE;
			int px2 = ((tx * IMAGE_REGION_SIZE * lod.subsample) + IMAGE_REGION_SIZE * lod.subsample) / PIXELS_PER_GAME_TILE;
			int py2 = ((ty * IMAGE_REGION_SIZE * lod.subsample) + IMAGE_REGION_SIZE * lod.subsample) / PIXELS_PER_GAME_TILE;

			double mx = (px1 + px2) * 0.5;
			double my = (py1 + py2) * 0.5;
			double dx = mx - cx;
			double dy = my - cy;
			return dx * dx + dy * dy;
		}));

		for (int[] t : order)
		{
			int tx = t[0];
			int ty = t[1];

			BufferedImage tile;
			synchronized (tileCache)
			{
				tile = tileCache.get(lod.subsample, currentPlane, tx, ty);
			}

			if (tile == null)
			{
				requestTileAsync(lod, currentPlane, tx, ty);
				continue;
			}

			int sx1 = tx * IMAGE_REGION_SIZE;
			int sy1 = ty * IMAGE_REGION_SIZE;

			int dx1 = (sx1 * lod.subsample) / PIXELS_PER_GAME_TILE;
			int dy1 = (sy1 * lod.subsample) / PIXELS_PER_GAME_TILE;
			int dx2 = ((sx1 + tile.getWidth()) * lod.subsample) / PIXELS_PER_GAME_TILE;
			int dy2 = ((sy1 + tile.getHeight()) * lod.subsample) / PIXELS_PER_GAME_TILE;

			g.drawImage(tile, dx1, dy1, dx2, dy2, 0, 0, tile.getWidth(), tile.getHeight(), null);
		}
	}

	private void requestTileAsync(LOD lod, int plane, int tx, int ty)
	{
		String key = lod.subsample + ":" + plane + ":" + tx + ":" + ty;
		if (!inflight.add(key))
		{
			return;
		}

		loader.execute(() ->
		{
			try
			{
				BufferedImage img = map.readTileImage(lod.subsample, plane, tx, ty);
				if (img != null)
				{
					synchronized (tileCache)
					{
						tileCache.put(lod.subsample, plane, tx, ty, img);
					}
					SwingUtilities.invokeLater(this::repaint);
				}
			}
			catch (Throwable ignore)
			{
			}
			finally
			{
				inflight.remove(key);
			}
		});
	}

	private void drawGrid(Graphics2D g)
	{
		if (!showGrid)
		{
			return;
		}

		Shape clipOld = g.getClip();
		g.setClip(new Rectangle2D.Double(0, 0, totalW, totalH));
		g.setColor(new Color(255, 255, 255, 90));
		g.setStroke(new BasicStroke(0));

		int c0 = Math.max(0, (int) Math.floor(viewX / GAME_REGION_SIZE));
		int r0 = Math.max(0, (int) Math.floor(viewY / GAME_REGION_SIZE));
		int c1 = Math.min(cols, (int) Math.ceil((viewX + getWidth() / zoom) / GAME_REGION_SIZE));
		int r1 = Math.min(rows, (int) Math.ceil((viewY + getHeight() / zoom) / GAME_REGION_SIZE));

		for (int c = c0; c <= c1; c++)
		{
			int x = c * GAME_REGION_SIZE;
			g.drawLine(x, r0 * GAME_REGION_SIZE, x, r1 * GAME_REGION_SIZE);
		}
		for (int r = r0; r <= r1; r++)
		{
			int y = r * GAME_REGION_SIZE;
			g.drawLine(c0 * GAME_REGION_SIZE, y, c1 * GAME_REGION_SIZE, y);
		}

		g.setClip(clipOld);
	}

	private void drawLabels(Graphics2D g)
	{
		if (!showRxRy && !showRegionId)
		{
			return;
		}

		Shape clipOld = g.getClip();
		g.setClip(new Rectangle2D.Double(0, 0, totalW, totalH));
		g.setColor(new Color(255, 255, 0, 220));
		g.setFont(getFont().deriveFont(20f));
		FontMetrics fm = g.getFontMetrics();

		int c0 = Math.max(0, (int) Math.floor(viewX / GAME_REGION_SIZE));
		int r0 = Math.max(0, (int) Math.floor(viewY / GAME_REGION_SIZE));
		int c1 = Math.min(cols - 1, (int) Math.floor((viewX + getWidth() / zoom) / GAME_REGION_SIZE));
		int r1 = Math.min(rows - 1, (int) Math.floor((viewY + getHeight() / zoom) / GAME_REGION_SIZE));

		for (int r = r0; r <= r1; r++)
		{
			int worldRy = maxRy - r;
			int yCenter = r * GAME_REGION_SIZE + GAME_REGION_SIZE / 2;
			int yBaseline = yCenter + fm.getAscent() / 2;

			for (int c = c0; c <= c1; c++)
			{
				int worldRx = minRx + c;
				String text = showRxRy
					? (worldRx + "," + worldRy)
					: Integer.toString((worldRx << 8) | worldRy);

				int xCenter = c * GAME_REGION_SIZE + GAME_REGION_SIZE / 2;
				int tw = fm.stringWidth(text);
				g.drawString(text, xCenter - tw / 2, yBaseline);
			}
		}

		g.setClip(clipOld);
	}

	private void drawPlayerIcon(Graphics2D g)
	{
		if (!showPlayer || hereIcon == null || client == null || client.getLocalPlayer() == null)
		{
			return;
		}

		WorldPoint wp = client.getLocalPlayer().getWorldLocation();
		if (wp.getPlane() != currentPlane)
		{
			return;
		}

		int rX = wp.getX() >> 6;
		int rY = wp.getY() >> 6;
		int tX = wp.getX() & 63;
		int tY = wp.getY() & 63;

		if (rX < minRx || rX > maxRx || rY < minRy || rY > maxRy)
		{
			return;
		}

		double px = (rX - minRx) * 64.0 + tX;
		double py = totalH - ((rY - minRy + 1) * 64.0) + (63 - tY);

		double sx = (px - viewX) * zoom;
		double sy = (py - viewY) * zoom;

		Image raw = hereIcon.getImage();
		int iw = raw.getWidth(this);
		int ih = raw.getHeight(this);
		if (iw <= 0 || ih <= 0)
		{
			return;
		}

		double target = clamp(28.0 * (1.0 / Math.cbrt(zoom)), 16.0, 72.0);
		double scale = target / Math.max(iw, ih);
		int dw = (int) Math.round(iw * scale);
		int dh = (int) Math.round(ih * scale);

		double cx = sx + (zoom * 0.5);
		double cy = sy + (zoom * 0.5);

		int dx = (int) Math.round(cx - dw / 2.0);
		int dy = (int) Math.round(cy - dh / 2.0);

		g.drawImage(raw, dx, dy, dx + dw, dy + dh, 0, 0, iw, ih, this);
		Toolkit.getDefaultToolkit().sync();

		playerIconBounds = new Rectangle(dx, dy, dw, dh);

		String rawName = null;
		try
		{
			rawName = client.getLocalPlayer().getName();
		}
		catch (Throwable ignore)
		{
		}
		playerIconTooltip = capitalizeFirst(rawName);
	}

	void focusPlayer()
	{
		centerOnPlayer(true);
		repaint();
	}

	private boolean centerOnPlayer(boolean snap)
	{
		if (client == null || client.getLocalPlayer() == null)
		{
			return false;
		}

		WorldPoint wp = client.getLocalPlayer().getWorldLocation();

		int rX = wp.getX() >> 6;
		int rY = wp.getY() >> 6;
		int tX = wp.getX() & 63;
		int tY = wp.getY() & 63;

		if (rX < minRx || rX > maxRx || rY < minRy || rY > maxRy)
		{
			return false;
		}

		setPlane(wp.getPlane());

		if (snap)
		{
			zoom = clamp(1.0, MIN_ZOOM, MAX_ZOOM);
		}

		double px = (rX - minRx) * 64.0 + tX;
		double py = totalH - ((rY - minRy + 1) * 64.0) + (63 - tY);

		double vw = getWidth() / zoom;
		double vh = getHeight() / zoom;

		viewX = px - vw / 2.0;
		viewY = py - vh / 2.0;

		clampViewLoose();
		return true;
	}

	private void centerCanvas()
	{
		double vw = getWidth() / zoom;
		double vh = getHeight() / zoom;
		viewX = (totalW - vw) / 2.0;
		viewY = (totalH - vh) / 2.0;
		clampViewLoose();
	}

	private void clampViewLoose()
	{
		double vw = getWidth() / zoom;
		double vh = getHeight() / zoom;

		double marginX = vw * 0.5;
		double marginY = vh * 0.5;

		double minX = -marginX;
		double minY = -marginY;
		double maxX = totalW - vw + marginX;
		double maxY = totalH - vh + marginY;

		viewX = clamp(viewX, minX, Math.max(minX, maxX));
		viewY = clamp(viewY, minY, Math.max(minY, maxY));
	}

	private Window getParentWindow()
	{
		return SwingUtilities.getWindowAncestor(this);
	}

	private enum LOD
	{
		FULL(1), HALF(2), QUARTER(4);

		final int subsample;

		LOD(int s)
		{
			this.subsample = s;
		}

		static LOD forZoom(double zoom)
		{
			if (zoom >= 2.0)
			{
				return FULL;
			}
			if (zoom >= 0.8)
			{
				return HALF;
			}
			return QUARTER;
		}
	}

	private static final class LoadingDialog extends JDialog
	{
		private final JLabel label;
		private final JProgressBar bar;
		private long lastBytes = 0L;
		private long lastUpdateMs = 0L;

		private Window followedOwner;
		private ComponentAdapter followListener;

		private LoadingDialog(Window owner, String title)
		{
			super(owner, title, ModalityType.MODELESS);
			setUndecorated(true);
			getRootPane().putClientProperty("JComponent.sizeVariant", "small");

			JPanel p = new JPanel();
			p.setBorder(new EmptyBorder(10, 14, 10, 14));
			p.setBackground(new Color(30, 30, 30));
			p.setLayout(new java.awt.BorderLayout(8, 8));

			label = new JLabel(title, SwingConstants.CENTER);
			label.setForeground(new Color(220, 220, 220));
			p.add(label, java.awt.BorderLayout.NORTH);

			bar = new JProgressBar();
			bar.setForeground(new Color(66, 135, 245));
			bar.setBackground(new Color(20, 20, 20));
			bar.setBorderPainted(false);
			bar.setStringPainted(true);
			bar.setString("");
			p.add(bar, java.awt.BorderLayout.CENTER);

			setContentPane(p);
			pack();
			setSize(Math.max(getWidth(), 320), getHeight());

			if (owner != null)
			{
				setLocationRelativeTo(owner);
			}
		}

		static LoadingDialog show(Window owner, String title)
		{
			LoadingDialog d = new LoadingDialog(owner, title);
			SwingUtilities.invokeLater(() -> d.setVisible(true));
			return d;
		}

		void followOwner(Window owner)
		{
			SwingUtilities.invokeLater(() ->
			{
				stopFollowingOwner();

				if (owner == null)
				{
					return;
				}

				followedOwner = owner;

				followListener = new ComponentAdapter()
				{
					@Override
					public void componentResized(ComponentEvent e)
					{
						recenterIfVisible();
					}

					@Override
					public void componentMoved(ComponentEvent e)
					{
						recenterIfVisible();
					}

					@Override
					public void componentShown(ComponentEvent e)
					{
						recenterIfVisible();
					}

					@Override
					public void componentHidden(ComponentEvent e)
					{
						try
						{
							setVisible(false);
						}
						catch (Throwable ignore)
						{
						}
					}
				};

				owner.addComponentListener(followListener);
				recenterIfVisible();
			});
		}

		private void recenterIfVisible()
		{
			if (!isDisplayable() || !isVisible())
			{
				return;
			}
			if (followedOwner == null)
			{
				return;
			}
			setLocationRelativeTo(followedOwner);
		}

		private void stopFollowingOwner()
		{
			if (followedOwner != null && followListener != null)
			{
				try
				{
					followedOwner.removeComponentListener(followListener);
				}
				catch (Throwable ignore)
				{
				}
			}
			followedOwner = null;
			followListener = null;
		}

		void setIndeterminate()
		{
			SwingUtilities.invokeLater(() ->
			{
				bar.setIndeterminate(true);
				bar.setString("");
			});
		}

		void setStatusText(String text)
		{
			SwingUtilities.invokeLater(() -> label.setText(text));
		}

		void setBytesDownloaded(long bytesOrNegativeForUnknown)
		{
			SwingUtilities.invokeLater(() ->
			{
				long now = System.currentTimeMillis();
				long absBytes = Math.abs(bytesOrNegativeForUnknown);

				if (now - lastUpdateMs < 125 && absBytes - lastBytes < (512 * 1024))
				{
					return;
				}

				lastUpdateMs = now;
				lastBytes = absBytes;

				bar.setIndeterminate(false);
				bar.setMinimum(0);
				bar.setMaximum(1);
				bar.setValue(1);
				bar.setString(String.format("Downloaded %.1f MB / 59.6 MB", absBytes / (1024.0 * 1024.0)));
			});
		}

		void close()
		{
			SwingUtilities.invokeLater(() ->
			{
				try
				{
					stopFollowingOwner();
					setVisible(false);
					dispose();
				}
				catch (Throwable ignore)
				{
				}
			});
		}
	}
}
