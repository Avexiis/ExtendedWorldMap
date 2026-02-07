package com.ewm.ui;

import com.ewm.ExtendedWorldMapConfig;
import com.ewm.store.FileManager;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;
import net.runelite.api.Client;

public class Dock extends JDialog
{
	private static final int BORDER_PX = 5;

	private static final int SAFE_INIT_W = 514;
	private static final int SAFE_INIT_H = 334;

	private final MapPanel panel;
	private final PanelSidebar sidebar;
	private final Overlay overlay = new Overlay();
	private boolean showTip = true;

	public Dock(Client client, ExtendedWorldMapConfig cfg, FileManager mapFiles, Component ownerForLocation)
	{
		super(SwingUtilities.getWindowAncestor(ownerForLocation), "Extended World Map (Docked)", ModalityType.MODELESS);
		setUndecorated(true);
		setAlwaysOnTop(false);
		getRootPane().setBorder(new LineBorder(new Color(90, 90, 90), BORDER_PX, false));
		setLayout(new BorderLayout());

		panel = new MapPanel(client, cfg, mapFiles);
		sidebar = new PanelSidebar(panel, false);

		JPanel content = new JPanel(new BorderLayout());
		content.add(panel, BorderLayout.CENTER);
		content.add(sidebar, BorderLayout.EAST);

		add(content, BorderLayout.CENTER);

		getRootPane().setGlassPane(overlay);
		overlay.setVisible(true);

		applyInitialDockBounds(ownerForLocation);

		enableEdgeDragResize();

		panel.loadMap();
		panel.setDockShiftDragEnabled(true);
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
	}

	private void applyInitialDockBounds(Component ownerForLocation)
	{
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

		w = Math.max(340, Math.min(w, safe.width));
		h = Math.max(260, Math.min(h, safe.height));

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
				return SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), Dock.this);
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

					b.width = Math.max(340, b.width);
					b.height = Math.max(260, b.height);
				}

				setBounds(b);
				clampInsideOwner();
				repaint();
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
						Dock.this.dispose();
						return;
					}
					if (hitToggleSidebar(e.getX(), e.getY()))
					{
						sidebar.toggleExpanded();

						repaint();
						Dock.this.revalidate();
						Dock.this.repaint();
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

			//Close (X)
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

			//Hamburger icon
			int padX = 5; //left-right padding inside the button
			int gapY = 4; //vertical gap between lines
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
