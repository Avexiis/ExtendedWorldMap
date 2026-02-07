package com.ewm.ui;

import java.awt.BorderLayout;
import java.awt.Color;
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
	static final int EXPANDED_WIDTH = 170;
	static final int COLLAPSED_WIDTH = 22;

	private static final Color SIDEBAR_BG = new Color(30, 30, 30);

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

		setOpaque(true);
		setBackground(SIDEBAR_BG);

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

		collapsedPanel.setOpaque(true);
		collapsedPanel.setBackground(SIDEBAR_BG);

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

		expandedPanel.setOpaque(true);
		expandedPanel.setBackground(SIDEBAR_BG);

		expandedPanel.setBorder(new EmptyBorder(6, 8, 6, 8));

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);

		JLabel title = new JLabel("Controls");
		title.setForeground(new Color(220, 220, 220));
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

		JCheckBox showPlayer = new JCheckBox("Show Player", panel.isShowPlayer());
		JCheckBox showGrid = new JCheckBox("Show Region Grid", panel.isShowGrid());
		JCheckBox showMarkers = new JCheckBox("Show Ground Markers", panel.isShowGroundMarkers());
		JCheckBox trackPlayer = new JCheckBox("Track Player", panel.isTrackPlayer());

		JButton jumpToPlayer = new JButton("Jump To Player");

		JComboBox<String> planeBox = new JComboBox<>(new String[]{
			"Ground", "Floor 1", "Floor 2", "Floor 3"
		});
		planeBox.setSelectedIndex(panel.getCurrentPlane());

		showPlayer.setAlignmentX(Component.LEFT_ALIGNMENT);
		showGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
		showMarkers.setAlignmentX(Component.LEFT_ALIGNMENT);
		trackPlayer.setAlignmentX(Component.LEFT_ALIGNMENT);

		jumpToPlayer.setAlignmentX(Component.LEFT_ALIGNMENT);
		planeBox.setAlignmentX(Component.LEFT_ALIGNMENT);

		showPlayer.addActionListener(e -> panel.setShowPlayer(showPlayer.isSelected()));
		showGrid.addActionListener(e -> panel.setShowGrid(showGrid.isSelected()));
		showMarkers.addActionListener(e -> panel.setShowGroundMarkers(showMarkers.isSelected()));

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
		content.add(showMarkers);
		content.add(trackPlayer);

		content.add(Box.createVerticalStrut(8));
		content.add(jumpToPlayer);
		content.add(Box.createVerticalStrut(10));

		JPanel floorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		floorRow.setOpaque(false);
		JLabel floorLabel = new JLabel("Floor:");
		floorLabel.setForeground(new Color(220, 220, 220));
		floorRow.add(floorLabel);
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
