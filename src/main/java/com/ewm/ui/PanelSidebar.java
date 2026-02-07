package com.ewm.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

class PanelSidebar extends JPanel
{
	static final int EXPANDED_WIDTH = 160;
	static final int COLLAPSED_WIDTH = 22;

	private final MapPanel panel;
	private final boolean showInternalToggleButtons;

	private final JPanel expandedPanel = new JPanel();
	private final JPanel collapsedPanel = new JPanel();

	private boolean expanded = false;
	private boolean initialized = false;

	PanelSidebar(MapPanel panel)
	{
		this(panel, true);
	}

	PanelSidebar(MapPanel panel, boolean showInternalToggleButtons)
	{
		this.panel = panel;
		this.showInternalToggleButtons = showInternalToggleButtons;

		setLayout(new BorderLayout());
		setOpaque(false);

		buildExpanded();
		buildCollapsed();

		setExpanded(false);
	}

	private int effectiveCollapsedWidth()
	{
		return showInternalToggleButtons ? COLLAPSED_WIDTH : 0;
	}

	int getCurrentWidth()
	{
		return expanded ? EXPANDED_WIDTH : effectiveCollapsedWidth();
	}

	private void buildCollapsed()
	{
		collapsedPanel.setLayout(new BorderLayout());
		collapsedPanel.setOpaque(false);

		if (showInternalToggleButtons)
		{
			collapsedPanel.setBorder(new EmptyBorder(4, 2, 4, 2));

			JButton open = new JButton("<");
			open.setFocusable(false);
			open.setToolTipText("Show controls");
			open.addActionListener(e -> setExpanded(true));
			collapsedPanel.add(open, BorderLayout.CENTER);
		}
	}

	private void buildExpanded()
	{
		expandedPanel.setLayout(new BorderLayout());
		expandedPanel.setOpaque(false);
		expandedPanel.setBorder(new EmptyBorder(6, 8, 6, 8));

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);

		JLabel title = new JLabel("Controls");
		header.add(title, BorderLayout.WEST);

		if (showInternalToggleButtons)
		{
			JButton close = new JButton(">");
			close.setFocusable(false);
			close.setToolTipText("Hide controls");
			close.addActionListener(e -> setExpanded(false));
			header.add(close, BorderLayout.EAST);
		}
		else
		{
			JPanel spacer = new JPanel();
			spacer.setOpaque(false);
			header.add(spacer, BorderLayout.EAST);
		}

		JPanel content = new JPanel();
		content.setOpaque(false);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		JCheckBox showPlayer = new JCheckBox("Show Player", true);
		JCheckBox showGrid = new JCheckBox("Show Region Grid", false);
		JCheckBox showRxRy = new JCheckBox("Show Region Coords", false);
		JCheckBox showRegionId = new JCheckBox("Show Region ID", false);
		JCheckBox trackPlayer = new JCheckBox("Track Player", false);

		JButton jumpToPlayer = new JButton("Jump To Player");

		JComboBox<String> planeBox = new JComboBox<>(new String[]{
			"Ground", "Floor 1", "Floor 2", "Floor 3"
		});
		planeBox.setSelectedIndex(0);

		showPlayer.setAlignmentX(Component.LEFT_ALIGNMENT);
		showGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
		showRxRy.setAlignmentX(Component.LEFT_ALIGNMENT);
		showRegionId.setAlignmentX(Component.LEFT_ALIGNMENT);
		trackPlayer.setAlignmentX(Component.LEFT_ALIGNMENT);

		jumpToPlayer.setAlignmentX(Component.LEFT_ALIGNMENT);
		planeBox.setAlignmentX(Component.LEFT_ALIGNMENT);

		showPlayer.addActionListener(e -> panel.setShowPlayer(showPlayer.isSelected()));
		showGrid.addActionListener(e -> panel.setShowGrid(showGrid.isSelected()));

		showRxRy.addActionListener(e ->
		{
			if (showRxRy.isSelected())
			{
				showRegionId.setSelected(false);
			}
			panel.setShowRxRy(showRxRy.isSelected());
			panel.setShowRegionId(showRegionId.isSelected());
		});

		showRegionId.addActionListener(e ->
		{
			if (showRegionId.isSelected())
			{
				showRxRy.setSelected(false);
			}
			panel.setShowRegionId(showRegionId.isSelected());
			panel.setShowRxRy(showRxRy.isSelected());
		});

		trackPlayer.addActionListener(e -> panel.setTrackPlayer(trackPlayer.isSelected()));
		jumpToPlayer.addActionListener(e -> panel.focusPlayer());

		planeBox.addActionListener(e ->
		{
			int idx = planeBox.getSelectedIndex();
			if (idx < 0)
			{
				idx = 0;
			}
			panel.setPlane(idx);
		});

		content.add(showPlayer);
		content.add(showGrid);
		content.add(showRxRy);
		content.add(showRegionId);
		content.add(trackPlayer);

		content.add(Box.createVerticalStrut(8));
		content.add(jumpToPlayer);
		content.add(Box.createVerticalStrut(10));

		JPanel floorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		floorRow.setOpaque(false);
		floorRow.add(new JLabel("Floor:"));
		floorRow.add(planeBox);
		floorRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		content.add(floorRow);
		content.add(Box.createVerticalGlue());

		expandedPanel.add(header, BorderLayout.NORTH);
		expandedPanel.add(content, BorderLayout.CENTER);
	}

	void setExpanded(boolean expanded)
	{
		if (initialized && this.expanded == expanded)
		{
			return;
		}
		this.expanded = expanded;
		this.initialized = true;

		removeAll();

		if (expanded)
		{
			add(expandedPanel, BorderLayout.CENTER);
			setPreferredSize(new Dimension(EXPANDED_WIDTH, 0));
			setMinimumSize(new Dimension(EXPANDED_WIDTH, 0));
			setMaximumSize(new Dimension(EXPANDED_WIDTH, Integer.MAX_VALUE));
		}
		else
		{
			add(collapsedPanel, BorderLayout.CENTER);

			int cw = effectiveCollapsedWidth();
			setPreferredSize(new Dimension(cw, 0));
			setMinimumSize(new Dimension(cw, 0));
			setMaximumSize(new Dimension(cw, Integer.MAX_VALUE));
		}

		revalidate();
		repaint();

		SwingUtilities.invokeLater(() ->
		{
			Component p = getParent();
			if (p != null)
			{
				p.revalidate();
				p.repaint();
			}
		});
	}

	void toggleExpanded()
	{
		setExpanded(!expanded);
	}
}
