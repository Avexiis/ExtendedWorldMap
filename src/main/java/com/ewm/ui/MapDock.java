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
package com.ewm.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.LineBorder;
import net.runelite.client.config.ConfigManager;

public class MapDock extends JDialog
{
	private static final int BORDER_PX = 5;

	private static final int SAFE_INIT_W = 386;
	private static final int SAFE_INIT_H = 251;

	private static final int MIN_W = 340;
	private static final int MIN_H = 260;

	private static final String CFG_GROUP = "extendedworldmap";
	private static final String KEY_DOCK_X = "dock_x";
	private static final String KEY_DOCK_Y = "dock_y";
	private static final String KEY_DOCK_W = "dock_w";
	private static final String KEY_DOCK_H = "dock_h";

	private final MapPanel panel;
	private final PanelSidebar sidebar;
	private final Overlay overlay = new Overlay();
	private final boolean showTip = true;

	@Nullable
	private final ConfigManager configManager;

	private final Timer saveBoundsDebounce;

	public MapDock(MapPanel sharedPanel, Component ownerForLocation, @Nullable ConfigManager configManager)
	{
		super(SwingUtilities.getWindowAncestor(ownerForLocation), "Extended World Map (Docked)", ModalityType.MODELESS);
		setUndecorated(true);
		setAlwaysOnTop(false);
		getRootPane().setBorder(new LineBorder(new Color(90, 90, 90), BORDER_PX, false));
		setLayout(new BorderLayout());

		this.panel = sharedPanel;
		this.sidebar = new PanelSidebar(panel, false);
		this.configManager = configManager;

		JPanel content = new JPanel(new BorderLayout());
		content.add(panel, BorderLayout.CENTER);
		content.add(sidebar, BorderLayout.EAST);
		add(content, BorderLayout.CENTER);

		getRootPane().setGlassPane(overlay);
		overlay.setVisible(true);

		saveBoundsDebounce = new Timer(250, e -> saveDockBoundsNow());
		saveBoundsDebounce.setRepeats(false);

		applyInitialDockBounds(ownerForLocation);

		enableEdgeDragResize();
		installAutoSaveListeners();

		panel.loadMapIfNeeded();
		panel.setDockShiftDragEnabled(true);

		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				panel.setTrackPlayer(false);
				saveDockBoundsNow();
			}

			@Override
			public void windowClosed(WindowEvent e)
			{
				panel.setTrackPlayer(false);
				saveDockBoundsNow();
			}
		});
	}

	public void refreshGroundMarkers()
	{
		panel.reloadGroundMarkersAsync();
	}

	public void openWithinOwner()
	{
		Window owner = getOwner();
		if (owner != null)
		{
			clampInsideOwner();
		}

		setVisible(true);
		toFront();

		scheduleSaveDockBounds();
	}

	private void installAutoSaveListeners()
	{
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				scheduleSaveDockBounds();
			}

			@Override
			public void componentMoved(ComponentEvent e)
			{
				scheduleSaveDockBounds();
			}

			@Override
			public void componentShown(ComponentEvent e)
			{
				scheduleSaveDockBounds();
			}
		});
	}

	private void scheduleSaveDockBounds()
	{
		if (configManager == null)
		{
			return;
		}
		if (!isDisplayable())
		{
			return;
		}
		saveBoundsDebounce.restart();
	}

	private void saveDockBoundsNow()
	{
		if (configManager == null)
		{
			return;
		}
		if (!isDisplayable())
		{
			return;
		}

		Rectangle b = getBounds();

		int w = Math.max(MIN_W, b.width);
		int h = Math.max(MIN_H, b.height);
		int x = b.x;
		int y = b.y;

		configManager.setConfiguration(CFG_GROUP, KEY_DOCK_X, Integer.toString(x));
		configManager.setConfiguration(CFG_GROUP, KEY_DOCK_Y, Integer.toString(y));
		configManager.setConfiguration(CFG_GROUP, KEY_DOCK_W, Integer.toString(w));
		configManager.setConfiguration(CFG_GROUP, KEY_DOCK_H, Integer.toString(h));
	}

	@Nullable
	private Integer getIntCfg(String key)
	{
		if (configManager == null)
		{
			return null;
		}

		String v = configManager.getConfiguration(CFG_GROUP, key);
		if (v == null || v.trim().isEmpty())
		{
			return null;
		}

		try
		{
			return Integer.parseInt(v.trim());
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	private boolean tryRestoreDockBounds(Component ownerForLocation)
	{
		Integer x = getIntCfg(KEY_DOCK_X);
		Integer y = getIntCfg(KEY_DOCK_Y);
		Integer w = getIntCfg(KEY_DOCK_W);
		Integer h = getIntCfg(KEY_DOCK_H);

		if (x == null || y == null || w == null || h == null)
		{
			return false;
		}

		int rw = Math.max(MIN_W, Math.min(w, 10000));
		int rh = Math.max(MIN_H, Math.min(h, 10000));

		setSize(new Dimension(rw, rh));
		setLocation(x, y);

		Window owner = SwingUtilities.getWindowAncestor(ownerForLocation);
		if (owner != null)
		{
			clampInsideOwner();
		}

		return true;
	}

	private void applyInitialDockBounds(Component ownerForLocation)
	{
		if (tryRestoreDockBounds(ownerForLocation))
		{
			return;
		}

		Window owner = SwingUtilities.getWindowAncestor(ownerForLocation);
		if (owner == null)
		{
			setSize(new Dimension(SAFE_INIT_W, SAFE_INIT_H));
			setLocation(50, 50);
			return;
		}

		Rectangle safe = computeSafeInitialRect(owner);

		int w = safe.width;
		int h = safe.height;

		w = Math.max(MIN_W, Math.min(w, safe.width));
		h = Math.max(MIN_H, Math.min(h, safe.height));

		w = Math.min(w, safe.width);
		h = Math.min(h, safe.height);

		setSize(new Dimension(w, h));
		setLocation(safe.x, safe.y);

		clampInsideOwner();
	}

	private Rectangle computeSafeInitialRect(Window owner)
	{
		Rectangle ob = owner.getBounds();
		Insets in = owner.getInsets();

		int contentX = ob.x + Math.max(0, in.left);
		int contentY = ob.y + Math.max(0, in.top);

		int contentW = ob.width - Math.max(0, in.left) - Math.max(0, in.right);
		int contentH = ob.height - Math.max(0, in.top) - Math.max(0, in.bottom);

		int w = Math.min(SAFE_INIT_W, Math.max(1, contentW));
		int h = Math.min(SAFE_INIT_H, Math.max(1, contentH));

		return new Rectangle(contentX, contentY, w, h);
	}

	private void clampInsideOwner()
	{
		Window owner = getOwner();
		if (owner == null)
		{
			return;
		}

		Rectangle ob = owner.getBounds();
		Rectangle b = getBounds();

		if (b.width > ob.width)
		{
			b.width = ob.width;
		}
		if (b.height > ob.height)
		{
			b.height = ob.height;
		}
		if (b.x < ob.x)
		{
			b.x = ob.x;
		}
		if (b.y < ob.y)
		{
			b.y = ob.y;
		}
		if (b.x + b.width > ob.x + ob.width)
		{
			b.x = ob.x + ob.width - b.width;
		}
		if (b.y + b.height > ob.y + ob.height)
		{
			b.y = ob.y + ob.height - b.height;
		}

		setBounds(b);
	}

	private void enableEdgeDragResize()
	{
		final Cursor[] cursors = {
			Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR),
			Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR),
			Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR),
			Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR),
			Cursor.getDefaultCursor(),
			Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR),
			Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR),
			Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR),
			Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)
		};

		MouseAdapter ma = new MouseAdapter()
		{
			private Point dragStart;
			private Rectangle startBounds;
			private int edgeMask = 0;

			private int hitTest(int x, int y)
			{
				int w = getWidth();
				int h = getHeight();

				int n = y <= BORDER_PX ? 1 : 0;
				int s = y >= h - BORDER_PX ? 2 : 0;
				int wE = x <= BORDER_PX ? 4 : 0;
				int e = x >= w - BORDER_PX ? 8 : 0;
				return n | s | wE | e;
			}

			private Cursor cursorFor(int mask)
			{
				switch (mask)
				{
					case 1 | 4:
						return cursors[0];
					case 1:
						return cursors[1];
					case 1 | 8:
						return cursors[2];
					case 4:
						return cursors[3];
					case 0:
						return cursors[4];
					case 8:
						return cursors[5];
					case 2 | 4:
						return cursors[6];
					case 2:
						return cursors[7];
					case 2 | 8:
						return cursors[8];
					default:
						return cursors[4];
				}
			}

			private Point toDialog(MouseEvent e)
			{
				return SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), MapDock.this);
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				dragStart = e.getLocationOnScreen();
				startBounds = getBounds();
				Point p = toDialog(e);
				edgeMask = hitTest(p.x, p.y);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				scheduleSaveDockBounds();
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				if (dragStart == null || startBounds == null)
				{
					return;
				}

				Point scr = e.getLocationOnScreen();
				int dx = scr.x - dragStart.x;
				int dy = scr.y - dragStart.y;

				Rectangle b = new Rectangle(startBounds);

				if (edgeMask == 0 && e.isShiftDown())
				{
					b.x += dx;
					b.y += dy;
				}
				else
				{
					if ((edgeMask & 1) != 0)
					{
						b.y += dy;
						b.height -= dy;
					}
					if ((edgeMask & 2) != 0)
					{
						b.height += dy;
					}
					if ((edgeMask & 4) != 0)
					{
						b.x += dx;
						b.width -= dx;
					}
					if ((edgeMask & 8) != 0)
					{
						b.width += dx;
					}

					b.width = Math.max(MIN_W, b.width);
					b.height = Math.max(MIN_H, b.height);
				}

				setBounds(b);
				clampInsideOwner();
				repaint();

				scheduleSaveDockBounds();
			}

			@Override
			public void mouseMoved(MouseEvent e)
			{
				Point p = toDialog(e);
				setCursor(cursorFor(hitTest(p.x, p.y)));
			}
		};

		addMouseListener(ma);
		addMouseMotionListener(ma);

		panel.addMouseListener(ma);
		panel.addMouseMotionListener(ma);

		sidebar.addMouseListener(ma);
		sidebar.addMouseMotionListener(ma);
	}

	private final class Overlay extends JComponent
	{
		private static final int BTN_SIZE = 18;
		private static final int PAD = 8;
		private static final int GAP = 6;
		private static final int ARC = 6;

		Overlay()
		{
			setOpaque(false);

			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (hitCloseDock(e.getX(), e.getY()))
					{
						MapDock.this.dispose();
						return;
					}
					if (hitToggleSidebar(e.getX(), e.getY()))
					{
						sidebar.toggleExpanded();

						repaint();
						MapDock.this.revalidate();
						MapDock.this.repaint();

						scheduleSaveDockBounds();
					}
				}
			});

			addMouseMotionListener(new MouseAdapter()
			{
				@Override
				public void mouseMoved(MouseEvent e)
				{
					boolean hand = hitCloseDock(e.getX(), e.getY()) || hitToggleSidebar(e.getX(), e.getY());
					setCursor(hand ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
				}
			});
		}

		private int buttonRightEdge()
		{
			int sidebarW = sidebar.getCurrentWidth();
			return getWidth() - sidebarW;
		}

		private Rectangle toggleRect()
		{
			int right = buttonRightEdge();
			int x = right - PAD - BTN_SIZE;
			int y = PAD;
			return new Rectangle(x, y, BTN_SIZE, BTN_SIZE);
		}

		private Rectangle closeRect()
		{
			Rectangle t = toggleRect();
			int x = t.x - GAP - BTN_SIZE;
			int y = t.y;
			return new Rectangle(x, y, BTN_SIZE, BTN_SIZE);
		}

		private boolean hitToggleSidebar(int x, int y)
		{
			return toggleRect().contains(x, y);
		}

		private boolean hitCloseDock(int x, int y)
		{
			return closeRect().contains(x, y);
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			Graphics2D g = (Graphics2D) g0.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			Rectangle rClose = closeRect();
			Rectangle rToggle = toggleRect();

			g.setColor(new Color(0, 0, 0, 160));
			g.fillRoundRect(rClose.x, rClose.y, rClose.width, rClose.height, ARC, ARC);

			g.setColor(Color.WHITE);
			g.setStroke(new BasicStroke(2f));
			g.drawRoundRect(rClose.x, rClose.y, rClose.width, rClose.height, ARC, ARC);

			g.drawLine(rClose.x + 5, rClose.y + 5, rClose.x + rClose.width - 5, rClose.y + rClose.height - 5);
			g.drawLine(rClose.x + 5, rClose.y + rClose.height - 5, rClose.x + rClose.width - 5, rClose.y + 5);

			g.setColor(new Color(0, 0, 0, 160));
			g.fillRoundRect(rToggle.x, rToggle.y, rToggle.width, rToggle.height, ARC, ARC);

			g.setColor(Color.WHITE);
			g.setStroke(new BasicStroke(2f));
			g.drawRoundRect(rToggle.x, rToggle.y, rToggle.width, rToggle.height, ARC, ARC);

			int padX = 5;
			int gapY = 4;
			int lineLen = rToggle.width - (padX * 2);

			int cx = rToggle.x + rToggle.width / 2;
			int x1 = cx - lineLen / 2;
			int x2 = cx + lineLen / 2;

			int cy = rToggle.y + rToggle.height / 2;
			int yMid = cy;
			int yTop = cy - gapY;
			int yBot = cy + gapY;

			g.drawLine(x1, yTop, x2, yTop);
			g.drawLine(x1, yMid, x2, yMid);
			g.drawLine(x1, yBot, x2, yBot);

			if (showTip)
			{
				String tip = "Hold Shift to drag  -  Drag edges to resize  -  Click X to close";
				g.setFont(getFont().deriveFont(12f));

				FontMetrics tfm = g.getFontMetrics();
				int tipW = tfm.stringWidth(tip);
				int tipH = tfm.getAscent();
				int pad = 8;
				int x = pad;
				int y = getHeight() - pad;

				g.setColor(new Color(0, 0, 0, 160));
				g.fillRoundRect(x - 5, y - tipH - 6, tipW + 10, tipH + 10, 8, 8);

				g.setColor(new Color(255, 255, 255, 220));
				g.drawString(tip, x, y - 2);
			}

			g.dispose();
		}

		@Override
		public boolean contains(int x, int y)
		{
			return hitCloseDock(x, y) || hitToggleSidebar(x, y);
		}
	}
}
