package io.github.jbellis.brokk.gui.dialogs;

import io.github.jbellis.brokk.Models;
import io.github.jbellis.brokk.agents.BuildAgent;
import io.github.jbellis.brokk.Project;
import io.github.jbellis.brokk.Project.DataRetentionPolicy;
import io.github.jbellis.brokk.gui.Chrome;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;

import io.github.jbellis.brokk.gui.components.BrowserLabel;

import java.io.IOException;
import java.util.Arrays; // Import the Arrays class

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashSet;

public class SettingsDialog extends JDialog {
    private static final Logger logger = LogManager.getLogger(SettingsDialog.class);

    private final Chrome chrome;
    private final JTabbedPane tabbedPane;
    private final JPanel projectPanel; // Keep a reference to enable/disable
    // Brokk Key field (Global)
    private JTextField brokkKeyField;
    // Global proxy selection
    private JRadioButton brokkProxyRadio;
    private JRadioButton localhostProxyRadio;
    // Project fields
    private JComboBox<Project.CpgRefresh> cpgRefreshComboBox; // ComboBox for CPG refresh
    private JTextField buildCleanCommandField;
    private JTextField allTestsCommandField;
    private JTextArea buildInstructionsArea;
    private DataRetentionPanel dataRetentionPanel; // Reference to the new panel
    // Model selection combo boxes (initialized in createModelsPanel)
    private JComboBox<String> architectModelComboBox;
    private JComboBox<String> codeModelComboBox;
    private JComboBox<Project.ReasoningLevel> codeReasoningComboBox;
    private JComboBox<String> askModelComboBox; // Added Ask model combo box
    private JComboBox<Project.ReasoningLevel> askReasoningComboBox;
    private JComboBox<String> editModelComboBox;
    private JComboBox<Project.ReasoningLevel> editReasoningComboBox;
    private JComboBox<String> searchModelComboBox;
    private JComboBox<Project.ReasoningLevel> searchReasoningComboBox;
    private JComboBox<Project.ReasoningLevel> architectReasoningComboBox;
    // Theme radio buttons (Global)
    private JRadioButton lightThemeRadio;
    private JRadioButton darkThemeRadio;
    // Project -> General tab specific fields
    private JTextArea styleGuideArea;
    private JTextArea commitFormatArea;
    private JComboBox<io.github.jbellis.brokk.analyzer.Language> languageComboBox; // Project language selector
    // Project -> Build tab specific fields
    private JList<String> excludedDirectoriesList;
    private DefaultListModel<String> excludedDirectoriesListModel;


    public SettingsDialog(Frame owner, Chrome chrome) {
        super(owner, "Settings", true); // Modal dialog
        this.chrome = chrome;
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setSize(600, 440);
        setLocationRelativeTo(owner);

        tabbedPane = new JTabbedPane(JTabbedPane.LEFT); // Tabs on the left

        // Global Settings Panel
        var globalPanel = createGlobalPanel();
        tabbedPane.addTab("Global", null, globalPanel, "Global application settings");

        // Project Settings Panel
        projectPanel = createProjectPanel();
        tabbedPane.addTab("Project", null, projectPanel, "Settings specific to the current project");

        // Enable/disable project-dependent tab and its contents based on whether a project is open
        var projectIsOpen = chrome.getProject() != null;

        projectPanel.setEnabled(projectIsOpen);
        // Iterating through components of projectPanel's main content (which is the subTabbedPane or a JLabel)
        Component projectActualContent = projectPanel.getComponent(0); // Assuming first child is the main content
        if (projectActualContent != null) {
            projectActualContent.setEnabled(projectIsOpen);
            if (projectActualContent instanceof Container) {
                for (Component comp : ((Container) projectActualContent).getComponents()) {
                    comp.setEnabled(projectIsOpen);
                    if (comp instanceof JTabbedPane) { // If it's the sub-tabbed pane, enable/disable its tabs too
                        JTabbedPane subTabs = (JTabbedPane) comp;
                        for (int i = 0; i < subTabs.getTabCount(); i++) {
                            subTabs.setEnabledAt(i, projectIsOpen);
                            // And the components within those tabs
                            Component tabContent = subTabs.getComponentAt(i);
                            if (tabContent != null) {
                                tabContent.setEnabled(projectIsOpen);
                                if (tabContent instanceof Container) {
                                    for (Component innerComp : ((Container)tabContent).getComponents()) {
                                        innerComp.setEnabled(projectIsOpen);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        tabbedPane.setEnabledAt(tabbedPane.indexOfComponent(projectPanel), projectIsOpen); // Enable/disable the Project tab itself

        // Buttons Panel
        var buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        var okButton = new JButton("OK");
        var cancelButton = new JButton("Cancel");
        var applyButton = new JButton("Apply"); // Added Apply button

        okButton.addActionListener(e -> {
            applySettings();
            dispose();
        });
        cancelButton.addActionListener(e -> dispose());
        applyButton.addActionListener(e -> applySettings());

        // Add Escape key binding to close the dialog (like Cancel)
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "cancel");
        getRootPane().getActionMap().put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispose();
            }
        });

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(applyButton);

        // Layout
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(tabbedPane, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);
    }

    private JTabbedPane createGlobalPanel() {
        var globalSubTabbedPane = new JTabbedPane(JTabbedPane.TOP);

        // Service Tab
        var servicePanel = createServicePanel();
        globalSubTabbedPane.addTab("Service", null, servicePanel, "Service configuration");

        // Appearance Tab
        var appearancePanel = createAppearancePanel();
        globalSubTabbedPane.addTab("Appearance", null, appearancePanel, "Theme settings");

        // Models Tab
        var project = chrome.getProject(); // Models settings depend on project context
        var modelsPanel = createModelsPanel(project);
        globalSubTabbedPane.addTab("Models", null, modelsPanel, "Model selection and configuration");

        // Enable/disable components within the Models panel based on project state
        // The Models tab itself (within globalSubTabbedPane) remains enabled.
        boolean projectIsOpen = (project != null);
        modelsPanel.setEnabled(projectIsOpen);
        for (Component comp : modelsPanel.getComponents()) {
            comp.setEnabled(projectIsOpen);
            // If a component is a container (like a sub-panel for reasoning), recursively enable/disable its children.
            // This is particularly important if createModelsPanel returns a panel that itself contains other panels/components.
            if (comp instanceof Container) {
                setEnabledRecursive((Container) comp, projectIsOpen);
            }
        }
        // Special handling for JComboBox renderers if they show "Off" when disabled
        if (architectReasoningComboBox != null) updateReasoningComboBox(architectModelComboBox, architectReasoningComboBox, chrome.getContextManager().getModels());
        if (codeReasoningComboBox != null) updateReasoningComboBox(codeModelComboBox, codeReasoningComboBox, chrome.getContextManager().getModels());
        if (askReasoningComboBox != null) updateReasoningComboBox(askModelComboBox, askReasoningComboBox, chrome.getContextManager().getModels());
        if (editReasoningComboBox != null) updateReasoningComboBox(editModelComboBox, editReasoningComboBox, chrome.getContextManager().getModels());
        if (searchReasoningComboBox != null) updateReasoningComboBox(searchModelComboBox, searchReasoningComboBox, chrome.getContextManager().getModels());


        return globalSubTabbedPane;
    }

    private void setEnabledRecursive(Container container, boolean enabled) {
        for (Component c : container.getComponents()) {
            c.setEnabled(enabled);
            if (c instanceof Container) {
                setEnabledRecursive((Container) c, enabled);
            }
        }
    }


    private JPanel createServicePanel() {
        var servicePanel = new JPanel(new GridBagLayout());
        servicePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Padding for content
        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;
        int row = 0;

        // Brokk Key Input
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        servicePanel.add(new JLabel("Brokk Key:"), gbc);

        brokkKeyField = new JTextField(20);
        brokkKeyField.setText(Project.getBrokkKey());
        gbc.gridx = 1;
        gbc.gridy = row++;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        servicePanel.add(brokkKeyField, gbc);

        // Balance Display
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        servicePanel.add(new JLabel("Balance:"), gbc);

        var balanceField = new JTextField("Loading...");
        balanceField.setEditable(false);
        balanceField.setColumns(8);
        var balanceDisplayPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        balanceDisplayPanel.add(balanceField);
        var topUpUrl = Models.TOP_UP_URL;
        var topUpLabel = new BrowserLabel(topUpUrl, "Top Up");
        balanceDisplayPanel.add(topUpLabel);

        gbc.gridx = 1;
        gbc.gridy = row++;
        gbc.weightx = 0.0; // Don't let balance field grow
        gbc.fill = GridBagConstraints.NONE;
        servicePanel.add(balanceDisplayPanel, gbc);

        var contextManager = chrome.getContextManager();
        var models = contextManager.getModels();
        contextManager.submitBackgroundTask("Fetching user balance", () -> {
            try {
                float balance = models.getUserBalance();
                SwingUtilities.invokeLater(() -> balanceField.setText(String.format("$%.2f", balance)));
            } catch (java.io.IOException e) {
                logger.debug("Failed to fetch user balance", e);
                SwingUtilities.invokeLater(() -> balanceField.setText("Error"));
            }
        });

        // Sign-up/login link
        var signupUrl = "https://brokk.ai";
        var signupLabel = new BrowserLabel(signupUrl, "Sign up or get your key at " + signupUrl);
        signupLabel.setFont(signupLabel.getFont().deriveFont(Font.ITALIC));
        gbc.gridx = 1;
        gbc.gridy = row++;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(2, 5, 8, 5); // Extra bottom margin for spacing
        servicePanel.add(signupLabel, gbc);
        gbc.insets = new Insets(2, 5, 2, 5); // Reset insets

        // LLM Proxy Setting
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        servicePanel.add(new JLabel("LLM Proxy:"), gbc);

        brokkProxyRadio = new JRadioButton("Brokk");
        localhostProxyRadio = new JRadioButton("Localhost");
        var proxyGroup = new ButtonGroup();
        proxyGroup.add(brokkProxyRadio);
        proxyGroup.add(localhostProxyRadio);

        if (Project.getLlmProxySetting() == Project.LlmProxySetting.BROKK) {
            brokkProxyRadio.setSelected(true);
        } else {
            localhostProxyRadio.setSelected(true);
        }

        gbc.gridx = 1;
        gbc.gridy = row++;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        servicePanel.add(brokkProxyRadio, gbc);

        gbc.gridy = row++;
        servicePanel.add(localhostProxyRadio, gbc);

        var proxyInfoLabel = new JLabel("Brokk will look for a litellm proxy on localhost:4000");
        proxyInfoLabel.setFont(proxyInfoLabel.getFont().deriveFont(Font.ITALIC));
        gbc.insets = new Insets(0, 25, 2, 5); // Indent
        gbc.gridy = row++;
        servicePanel.add(proxyInfoLabel, gbc);

        var restartLabel = new JLabel("Restart required after changing proxy settings");
        restartLabel.setFont(restartLabel.getFont().deriveFont(Font.ITALIC));
        gbc.gridy = row++;
        servicePanel.add(restartLabel, gbc);
        gbc.insets = new Insets(2, 5, 2, 5); // Reset insets

        // Add vertical glue to push content up
        gbc.gridy = row;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        servicePanel.add(Box.createVerticalGlue(), gbc);
        return servicePanel;
    }

    private JPanel createAppearancePanel() {
        var appearancePanel = new JPanel(new GridBagLayout());
        appearancePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Padding for content
        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;
        int row = 0;

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        appearancePanel.add(new JLabel("Theme:"), gbc);

        lightThemeRadio = new JRadioButton("Light");
        darkThemeRadio = new JRadioButton("Dark");
        var themeGroup = new ButtonGroup();
        themeGroup.add(lightThemeRadio);
        themeGroup.add(darkThemeRadio);

        if (Project.getTheme().equals("dark")) {
            darkThemeRadio.setSelected(true);
        } else {
            lightThemeRadio.setSelected(true);
        }
        lightThemeRadio.putClientProperty("theme", false);
        darkThemeRadio.putClientProperty("theme", true);

        gbc.gridx = 1;
        gbc.gridy = row++;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        appearancePanel.add(lightThemeRadio, gbc);

        gbc.gridy = row++;
        appearancePanel.add(darkThemeRadio, gbc);

        // Add vertical glue to push content up
        gbc.gridy = row;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        appearancePanel.add(Box.createVerticalGlue(), gbc);
        return appearancePanel;
    }


    private JPanel createProjectPanel() {
        var project = chrome.getProject();
        // Name change for clarity, this is the panel returned for the "Project" tab
        var projectTabRootPanel = new JPanel(new BorderLayout());
        if (project == null) {
            projectTabRootPanel.add(new JLabel("No project is open."), BorderLayout.CENTER);
            return projectTabRootPanel;
        }

        // Create a sub-tabbed pane for Project settings: General, Build, Data Retention
        var subTabbedPane = new JTabbedPane(JTabbedPane.TOP);

        // ----- Build Tab -----
        var buildPanel = new JPanel(new GridBagLayout());
        buildPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        // Initialize Build fields for new BuildDetails
        buildCleanCommandField = new JTextField(); // used for Build/Lint Command
        allTestsCommandField = new JTextField(); // used for Test All Command
        // Set the text area for build instructions to be 10 lines tall
        buildInstructionsArea = new JTextArea(10, 20);
        buildInstructionsArea.setWrapStyleWord(true);
        buildInstructionsArea.setLineWrap(true);
        var instructionsScrollPane = new JScrollPane(buildInstructionsArea);
        instructionsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        instructionsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        var details = project.getBuildDetails();
        logger.debug("Initial Build Details: {}", details);
        // Build/Lint Command
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        buildPanel.add(new JLabel("Build/Lint Command:"), gbc);
        buildCleanCommandField.setText(details.buildLintCommand());
        gbc.gridx = 1;
        gbc.gridy = row++;
        gbc.weightx = 1.0;
        buildPanel.add(buildCleanCommandField, gbc);

        // Test All Command
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        buildPanel.add(new JLabel("Test All Command:"), gbc);
        allTestsCommandField.setText(details.testAllCommand());
        gbc.gridx = 1;
        gbc.gridy = row++;
        gbc.weightx = 1.0;
        buildPanel.add(allTestsCommandField, gbc);

        // Build Instructions
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        buildPanel.add(new JLabel("Build Instructions:"), gbc);
        buildInstructionsArea.setText(details.instructions());
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1.0;
        gbc.weighty = 0.5; // Share vertical space equally
        gbc.fill = GridBagConstraints.BOTH;
        buildPanel.add(instructionsScrollPane, gbc);
        row++; // Move to the next conceptual row

        // Excluded Directories
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0; // Label doesn't take vertical space
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.NONE;
        buildPanel.add(new JLabel("Excluded Directories:"), gbc);

        excludedDirectoriesListModel = new DefaultListModel<>();
        var sortedExcludedDirs = details.excludedDirectories().stream().sorted().toList();
        for (String dir : sortedExcludedDirs) {
            excludedDirectoriesListModel.addElement(dir);
        }
        excludedDirectoriesList = new JList<>(excludedDirectoriesListModel);
        excludedDirectoriesList.setVisibleRowCount(7);
        var excludedScrollPane = new JScrollPane(excludedDirectoriesList);

        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1.0;
        gbc.weighty = 0.5; // Share vertical space equally
        gbc.fill = GridBagConstraints.BOTH;
        buildPanel.add(excludedScrollPane, gbc);

        // Buttons for Excluded Directories
        var excludedButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        var addButton = new JButton("Add");
        var removeButton = new JButton("Remove");
        excludedButtonsPanel.add(addButton);
        excludedButtonsPanel.add(removeButton);

        gbc.gridx = 1;
        gbc.gridy = row + 1; // Position buttons below the list
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHWEST; // Align to the top-left of the cell
        gbc.insets = new Insets(2, 0, 2, 2); // Small top margin, align with list's left edge
        buildPanel.add(excludedButtonsPanel, gbc);
        gbc.insets = new Insets(2, 2, 2, 2); // Reset insets

        // Add button action
        addButton.addActionListener(e -> {
            String newDir = JOptionPane.showInputDialog(SettingsDialog.this,
                                                          "Enter directory to exclude (e.g., target/, build/):",
                                                          "Add Excluded Directory",
                                                          JOptionPane.PLAIN_MESSAGE);
            if (newDir != null && !newDir.trim().isEmpty()) {
                String trimmedNewDir = newDir.trim();
                // Add and then re-sort the model
                excludedDirectoriesListModel.addElement(trimmedNewDir);
                var elements = new java.util.ArrayList<String>();
                for (int i = 0; i < excludedDirectoriesListModel.getSize(); i++) {
                    elements.add(excludedDirectoriesListModel.getElementAt(i));
                }
                elements.sort(String::compareToIgnoreCase); // Or String::compareTo for case-sensitive
                excludedDirectoriesListModel.clear();
                for (String element : elements) {
                    excludedDirectoriesListModel.addElement(element);
                }
            }
        });

        // Remove button action
        removeButton.addActionListener(e -> {
            int[] selectedIndices = excludedDirectoriesList.getSelectedIndices();
            // Iterate backwards to avoid issues with index shifting after removal
            for (int i = selectedIndices.length - 1; i >= 0; i--) {
                excludedDirectoriesListModel.removeElementAt(selectedIndices[i]);
            }
        });

        row += 2; // Increment row counter, consumed one for list, one for buttons panel

        // ----- Other Tab -----
        var otherPanel = new JPanel(new GridBagLayout());
        otherPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        row = 0;

        // Code Intelligence Refresh
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        otherPanel.add(new JLabel("Code Intelligence Refresh:"), gbc);
        cpgRefreshComboBox = new JComboBox<>(new Project.CpgRefresh[]{Project.CpgRefresh.AUTO, Project.CpgRefresh.ON_RESTART});
        var currentRefresh = project.getCpgRefresh();
        cpgRefreshComboBox.setSelectedItem(currentRefresh == Project.CpgRefresh.UNSET ? Project.CpgRefresh.AUTO : currentRefresh);
        gbc.gridx = 1;
        gbc.gridy = row++; // Increment row after adding combo box
        gbc.weightx = 1.0;
        otherPanel.add(cpgRefreshComboBox, gbc);

        // --- Project Language ---
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        otherPanel.add(new JLabel("Code Intelligence:"), gbc);
        // Populate with all languages except NONE
        var availableLanguages = Arrays.stream(io.github.jbellis.brokk.analyzer.Language.values())
                .filter(lang -> lang != io.github.jbellis.brokk.analyzer.Language.NONE)
                .toArray(io.github.jbellis.brokk.analyzer.Language[]::new);
        languageComboBox = new JComboBox<>(availableLanguages);
        languageComboBox.setSelectedItem(project.getAnalyzerLanguage());
        gbc.gridx = 1;
        gbc.gridy = row++; // Increment row after adding combo box
        gbc.weightx = 1.0;
        otherPanel.add(languageComboBox, gbc);


        // --- Style Guide Editor ---
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.NORTHWEST; // Align label top-left
        gbc.fill = GridBagConstraints.NONE;
        otherPanel.add(new JLabel("Style Guide:"), gbc);

        styleGuideArea = new JTextArea(5, 40); // Reduced height
        styleGuideArea.setText(project.getStyleGuide()); // Load current style guide
        styleGuideArea.setWrapStyleWord(true);
        styleGuideArea.setLineWrap(true);
        var styleScrollPane = new JScrollPane(styleGuideArea);
        styleScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        styleScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        gbc.gridx = 1;
        gbc.gridy = row++; // Increment row after adding style guide
        gbc.weightx = 1.0;
        gbc.weighty = 1.0; // Allow style guide area to grow vertically
        gbc.fill = GridBagConstraints.BOTH; // Fill available space
        gbc.weighty = 0.5; // Give it half the vertical weight
        otherPanel.add(styleScrollPane, gbc);
        row++; // Move to next row

        // --- Style Guide Explanation ---
        gbc.gridx = 1;
        gbc.gridy = row++;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0; // No vertical weight for label
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        var styleGuideInfo = new JLabel("<html>The Style Guide is used by the Code Agent to help it conform to your project's style.</html>");
        styleGuideInfo.setFont(styleGuideInfo.getFont().deriveFont(Font.ITALIC, styleGuideInfo.getFont().getSize() * 0.9f));
        gbc.insets = new Insets(0, 2, 8, 2); // Add bottom margin
        otherPanel.add(styleGuideInfo, gbc);

        // --- Commit Message Format ---
        gbc.insets = new Insets(2, 2, 2, 2); // Reset insets
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.NORTHWEST; // Align label top-left
        gbc.fill = GridBagConstraints.NONE;
        otherPanel.add(new JLabel("Commit Format:"), gbc);

        commitFormatArea = new JTextArea(5, 40); // Same height as style guide initially
        commitFormatArea.setText(project.getCommitMessageFormat());
        commitFormatArea.setWrapStyleWord(true);
        commitFormatArea.setLineWrap(true);
        var commitFormatScrollPane = new JScrollPane(commitFormatArea);
        commitFormatScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        commitFormatScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Use a sub-panel to hold the text area and reset button together
        var commitFormatPanel = new JPanel(new BorderLayout(5, 0)); // 5px horizontal gap
        commitFormatPanel.add(commitFormatScrollPane, BorderLayout.CENTER);

        gbc.gridx = 1;
        gbc.gridy = row++; // Increment row
        gbc.weightx = 1.0;
        gbc.weighty = 0.5; // Give it the other half of vertical weight
        gbc.fill = GridBagConstraints.BOTH; // Fill available space
        otherPanel.add(commitFormatPanel, gbc); // Add the sub-panel

        // --- Commit Format Explanation ---
        gbc.gridx = 1;
        gbc.gridy = row++;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0; // No vertical weight for label
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        var commitFormatInfo = new JLabel("<html>This informs the LLM how to structure the commit message suggestions it makes.</html>");
        commitFormatInfo.setFont(commitFormatInfo.getFont().deriveFont(Font.ITALIC, commitFormatInfo.getFont().getSize() * 0.9f));
        gbc.insets = new Insets(0, 2, 2, 2); // Small bottom margin
        otherPanel.add(commitFormatInfo, gbc);

        // Reset weighty for subsequent components if any were added below
        gbc.weighty = 0.0;
        gbc.insets = new Insets(2, 2, 2, 2); // Reset insets


        // Add General tab first, then Build tab
        subTabbedPane.addTab("General", otherPanel); // Renamed from "Other"
        subTabbedPane.addTab("Build", buildPanel);

        // Add General tab first, then Build tab
        subTabbedPane.addTab("General", otherPanel); // Renamed from "Other"
        subTabbedPane.addTab("Build", buildPanel);

        // ----- Data Retention Tab -----
        dataRetentionPanel = new DataRetentionPanel(project); // Create the panel instance
        subTabbedPane.addTab("Data Retention", dataRetentionPanel); // Add the new tab

        projectTabRootPanel.add(subTabbedPane, BorderLayout.CENTER);
        return projectTabRootPanel;
    }

    /**
     * Static inner class to encapsulate the Data Retention settings UI and logic.
     */
    public static class DataRetentionPanel extends JPanel {
        private final Project project;
        private final ButtonGroup policyGroup;
        private final JRadioButton improveRadio;
        private final JRadioButton minimalRadio;
        private final JLabel infoLabel;

        public DataRetentionPanel(Project project) {
            super(new GridBagLayout());
            this.project = project;
            this.policyGroup = new ButtonGroup();

            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            var gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            int y = 0;

            // --- Improve Brokk Radio Button ---
            improveRadio = new JRadioButton(DataRetentionPolicy.IMPROVE_BROKK.getDisplayName());
            improveRadio.putClientProperty("policy", DataRetentionPolicy.IMPROVE_BROKK);
            policyGroup.add(improveRadio);
            gbc.gridx = 0;
            gbc.gridy = y++;
            gbc.insets = new Insets(5, 5, 0, 5); // Reduced bottom margin
            add(improveRadio, gbc);

            // --- Improve Brokk Description Label ---
            var improveDescLabel = new JLabel("<html>Allow Brokk and/or its partners to use requests from this project " + "to train models and improve the Brokk service.</html>");
            improveDescLabel.setFont(improveDescLabel.getFont().deriveFont(Font.ITALIC, improveDescLabel.getFont().getSize() * 0.9f));
            gbc.gridx = 0;
            gbc.gridy = y++;
            gbc.insets = new Insets(0, 25, 10, 5); // Indent left, add bottom margin
            add(improveDescLabel, gbc);

            // --- Minimal Radio Button ---
            minimalRadio = new JRadioButton(DataRetentionPolicy.MINIMAL.getDisplayName());
            minimalRadio.putClientProperty("policy", DataRetentionPolicy.MINIMAL);
            policyGroup.add(minimalRadio);
            gbc.gridx = 0;
            gbc.gridy = y++;
            gbc.insets = new Insets(5, 5, 0, 5); // Reduced bottom margin
            add(minimalRadio, gbc);

            // --- Minimal Description Label ---
            var minimalDescLabel = new JLabel("<html>Brokk will not share data from this project with anyone and " + "will restrict its use to the minimum necessary to provide the Brokk service.</html>");
            minimalDescLabel.setFont(minimalDescLabel.getFont().deriveFont(Font.ITALIC, minimalDescLabel.getFont().getSize() * 0.9f));
            gbc.gridx = 0;
            gbc.gridy = y++;
            gbc.insets = new Insets(0, 25, 10, 5); // Indent left, add bottom margin
            add(minimalDescLabel, gbc);

            // --- Informational Text ---
            // Adjust top inset for spacing after the description labels
            gbc.insets = new Insets(15, 5, 5, 5); // Add spacing above info text
            infoLabel = new JLabel("<html>Data retention policy affects which AI models are allowed. In particular, Deepseek models are not available under the Essential Use Only policy, since Deepseek will train on API requests independently of Brokk.</html>");
            infoLabel.setFont(infoLabel.getFont().deriveFont(infoLabel.getFont().getSize() * 0.9f));
            gbc.gridx = 0;
            gbc.gridy = y++;
            add(infoLabel, gbc);

            // Add vertical glue to push components to the top
            gbc.gridx = 0;
            gbc.gridy = y; // Use the final incremented y value
            gbc.weighty = 1.0; // Take up remaining vertical space
            gbc.fill = GridBagConstraints.VERTICAL;
            add(Box.createVerticalGlue(), gbc);

            loadPolicy();
        }

        /**
         * Loads the current policy from the project and selects the corresponding radio button.
         */
        public void loadPolicy() {
            var currentPolicy = project.getDataRetentionPolicy();
            if (currentPolicy == DataRetentionPolicy.IMPROVE_BROKK) {
                improveRadio.setSelected(true);
            } else if (currentPolicy == DataRetentionPolicy.MINIMAL) {
                minimalRadio.setSelected(true);
            } else {
                policyGroup.clearSelection(); // No selection for UNSET
            }
        }

        /**
         * Returns the currently selected policy, or UNSET if none is selected.
         */
        public DataRetentionPolicy getSelectedPolicy() {
            if (improveRadio.isSelected()) {
                return DataRetentionPolicy.IMPROVE_BROKK;
            } else if (minimalRadio.isSelected()) {
                return DataRetentionPolicy.MINIMAL;
            } else {
                return DataRetentionPolicy.UNSET;
            }
        }

        /**
         * Saves the currently selected policy back to the project if it has changed.
         */
        public void applyPolicy() {
            var selectedPolicy = getSelectedPolicy();
            // Only save if a valid policy is selected and it's different from the current one
            if (selectedPolicy != DataRetentionPolicy.UNSET && selectedPolicy != project.getDataRetentionPolicy()) {
                project.setDataRetentionPolicy(selectedPolicy);
            }
        }
    }

    /**
     * Creates the panel that lets the user pick per-role models and – where
     * supported – their “reasoning effort”.  The new layout keeps every row
     * on a tidy four-column grid and lets both the model and reasoning
     * combo-boxes grow with the window, so the dialog remains readable even
     * when long model names are present.
     *
     * If the project is null, a placeholder panel is returned.
     */
    /**
     * Builds the “Models” tab in a compact two-column grid:
     *
     *   Architect  [model-combo]
     *   Reasoning  [reasoning-combo]
     *               <explanation>
     *
     * Each model type gets its own block separated by extra vertical padding.
     * Labels are right-aligned; we omit the words “Model:” / “Reasoning:” and all
     * colons, per user request.  Combo-boxes keep their preferred size (no weightx).
     *
     * @param project The current project, or null if no project is open.
     * @return A JPanel for the Models tab.
     */
    private JPanel createModelsPanel(Project project) {
        if (project == null) {
            var placeholderPanel = new JPanel(new BorderLayout());
            placeholderPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            placeholderPanel.add(new JLabel("No project is open. Model settings are project-specific."), BorderLayout.CENTER);
            return placeholderPanel;
        }

        var panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        var gbc = new GridBagConstraints();
        gbc.insets  = new Insets(4, 4, 4, 4);
        gbc.anchor  = GridBagConstraints.EAST;      // right-align labels
        gbc.fill    = GridBagConstraints.NONE;      // combos keep preferred width
        gbc.weightx = 0.0;                          // “don’t weightx anything”

        var models          = chrome.getContextManager().getModels();
        var availableModels = models.getAvailableModels()
                .keySet()
                .stream()
                .sorted()
                .toArray(String[]::new);
        var reasoningLevels = Project.ReasoningLevel.values();

        // will (de)activate reasoning dropdowns when model changes
        Runnable updateReasoningState = () -> {
            updateReasoningComboBox(architectModelComboBox, architectReasoningComboBox, models);
            updateReasoningComboBox(codeModelComboBox,       codeReasoningComboBox,       models);
            updateReasoningComboBox(askModelComboBox,        askReasoningComboBox,        models);
            updateReasoningComboBox(editModelComboBox,       editReasoningComboBox,       models);
            updateReasoningComboBox(searchModelComboBox,     searchReasoningComboBox,     models);
        };

        int row = 0;   // running row counter

        /* ---------------- Architect -------------------------------------- */
        architectModelComboBox = new JComboBox<>(availableModels);
        architectModelComboBox.setSelectedItem(project.getArchitectModelName());
        architectModelComboBox.addActionListener(e -> updateReasoningState.run());

        architectReasoningComboBox = new JComboBox<>(reasoningLevels);
        architectReasoningComboBox.setSelectedItem(project.getArchitectReasoningLevel());

        row = addModelSection(panel, gbc, row,
                              "Architect",
                              architectModelComboBox,
                              architectReasoningComboBox,
                              "The Architect plans and executes multi-step projects, calling other agents and tools as needed");

        /* ---------------- Code ------------------------------------------- */
        codeModelComboBox = new JComboBox<>(availableModels);
        codeModelComboBox.setSelectedItem(project.getCodeModelName());
        codeModelComboBox.addActionListener(e -> updateReasoningState.run());

        codeReasoningComboBox = new JComboBox<>(reasoningLevels);
        codeReasoningComboBox.setSelectedItem(project.getCodeReasoningLevel());

        row = addModelSection(panel, gbc, row,
                              "Code",
                              codeModelComboBox,
                              codeReasoningComboBox,
                              "Used when invoking the Code Agent manually");

        /* ---------------- Ask -------------------------------------------- */
        askModelComboBox = new JComboBox<>(availableModels);
        askModelComboBox.setSelectedItem(project.getAskModelName());
        askModelComboBox.addActionListener(e -> updateReasoningState.run());

        askReasoningComboBox = new JComboBox<>(reasoningLevels);
        askReasoningComboBox.setSelectedItem(project.getAskReasoningLevel());

        row = addModelSection(panel, gbc, row,
                              "Ask",
                              askModelComboBox,
                              askReasoningComboBox,
                              "Answers questions about the current Workspace contents");

        /* ---------------- Edit ------------------------------------------- */
        editModelComboBox = new JComboBox<>(availableModels);
        editModelComboBox.setSelectedItem(project.getEditModelName());
        editModelComboBox.addActionListener(e -> updateReasoningState.run());

        editReasoningComboBox = new JComboBox<>(reasoningLevels);
        editReasoningComboBox.setSelectedItem(project.getEditReasoningLevel());

        row = addModelSection(panel, gbc, row,
                              "Edit",
                              editModelComboBox,
                              editReasoningComboBox,
                              "Used when invoking the Code Agent from the Architect; also used for Deep Scan");

        /* ---------------- Search ----------------------------------------- */
        searchModelComboBox = new JComboBox<>(availableModels);
        searchModelComboBox.setSelectedItem(project.getSearchModelName());
        searchModelComboBox.addActionListener(e -> updateReasoningState.run());

        searchReasoningComboBox = new JComboBox<>(reasoningLevels);
        searchReasoningComboBox.setSelectedItem(project.getSearchReasoningLevel());

        row = addModelSection(panel, gbc, row,
                              "Search",
                              searchModelComboBox,
                              searchReasoningComboBox,
                              "Searches the project for information described in natural language");

        /* push everything up */
        gbc.gridx     = 0;
        gbc.gridy     = row;
        gbc.gridwidth = 3; // Span all three columns
        gbc.weighty   = 1.0;
        gbc.fill      = GridBagConstraints.VERTICAL;
        panel.add(Box.createVerticalGlue(), gbc);

        SwingUtilities.invokeLater(updateReasoningState);   // initial enable/disable

        // Return the panel directly, without wrapping in a scroll pane
        return panel;
    }

    /**
     * Adds one “model / reasoning / explanation” block to the grid-bag panel and
     * returns the next free row index.
     *
     * Layout (three columns):
     *   <label>        [model-combo]        <italic explanation (spans 2 rows)>
     *   Reasoning      [reasoning-combo]
     * (A 10-pixel top-margin separates blocks.)
     */
    private int addModelSection(JPanel panel,
                                GridBagConstraints gbc,
                                int startRow,
                                String typeLabel,
                                JComboBox<String> modelCombo,
                                JComboBox<Project.ReasoningLevel> reasoningCombo,
                                String explanation)
    {
        /* ---------- model row ------------------------------------------------ */
        var savedInsets = gbc.insets;
        gbc.insets  = new Insets(startRow == 0 ? 4 : 14, 4, 4, 4);   // extra top-pad
        gbc.anchor  = GridBagConstraints.EAST;
        gbc.gridx   = 0;
        gbc.gridy   = startRow;
        panel.add(new JLabel(typeLabel), gbc);

        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx  = 1;
        panel.add(modelCombo, gbc);

        /* ---------- reasoning row ------------------------------------------- */
        gbc.insets  = new Insets(4, 4, 2, 4);
        gbc.anchor  = GridBagConstraints.EAST;
        gbc.gridx   = 0;
        gbc.gridy   = startRow + 1;
        panel.add(new JLabel("Reasoning"), gbc);

        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx  = 1;
        panel.add(reasoningCombo, gbc);
        gbc.insets = savedInsets;        // restore

        /* ---------- explanatory text (wraps & centers) ---------------------- */
        var tip = new JTextArea(explanation);
        tip.setEditable(false);
        tip.setFocusable(false);
        tip.setLineWrap(true);
        tip.setWrapStyleWord(true);
        tip.setOpaque(false);
        tip.setBorder(null);
        tip.setFont(tip.getFont()
                            .deriveFont(Font.ITALIC,
                                        tip.getFont().getSize() * 0.9f));

        gbc.insets     = new Insets(startRow == 0 ? 4 : 14, 10, 2, 4);
        gbc.gridx      = 2;                     // third column
        gbc.gridy      = startRow;              // top row of the block
        gbc.gridheight = 2;                     // span two rows
        gbc.fill       = GridBagConstraints.HORIZONTAL;
        gbc.weightx    = 1.0;
        gbc.anchor     = GridBagConstraints.NORTHWEST;
        panel.add(tip, gbc);

        /* ---------- restore defaults for caller ----------------------------- */
        gbc.insets     = savedInsets;
        gbc.gridheight = 1;
        gbc.weightx    = 0.0;
        gbc.fill       = GridBagConstraints.NONE;

        return startRow + 2;   // next free row
    }

    private void applySettings() {
        // Apply Global Settings

        // -- Apply Brokk Key --
        String currentBrokkKey = Project.getBrokkKey();
        String newBrokkKey = brokkKeyField.getText().trim();
        if (!newBrokkKey.equals(currentBrokkKey)) {
            if (!newBrokkKey.isEmpty()) {
                try {
                    Models.validateKey(newBrokkKey);
                } catch (IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(this,
                                                  "Invalid Brokk Key",
                                                  "Invalid Key",
                                                  JOptionPane.ERROR_MESSAGE);
                    return;
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this,
                                                  "Network error: " + ex.getMessage(),
                                                  "Unable to reach Brokk service",
                                                  JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            Project.setBrokkKey(newBrokkKey);
            logger.debug("Applied Brokk Key: {}", newBrokkKey.isEmpty() ? "<empty>" : "****");
        }

        // -- Apply LLM Proxy Setting --
        Project.LlmProxySetting proxySetting = brokkProxyRadio.isSelected()
                                               ? Project.LlmProxySetting.BROKK
                                               : Project.LlmProxySetting.LOCALHOST;
        Project.setLlmProxySetting(proxySetting);
        logger.debug("Applied LLM Proxy Setting: {}", proxySetting);


        // -- Apply Theme --
        if (darkThemeRadio != null && lightThemeRadio != null) { // Ensure they were initialized
            boolean newIsDark = darkThemeRadio.isSelected();
            String newTheme = newIsDark ? "dark" : "light";
            if (!newTheme.equals(Project.getTheme())) {
                chrome.switchTheme(newIsDark); // switchTheme calls Project.setTheme internally
                logger.debug("Applied Theme: {}", newTheme);
            }
        } else {
            // This case should ideally not be hit if panel construction is correct.
            logger.warn("Theme radio buttons not initialized, cannot apply theme settings.");
        }

        // Apply Project Settings (if project is open)
        // Model settings are also project-specific and handled below.
        var project = chrome.getProject();
        if (project != null) {
            // --- Apply settings from "Project" tab ---
            // (This checks tabbedPane.isEnabledAt(1) implicitly by project != null,
            // as the tab's enabled state is tied to project presence)

            // Get current details to compare against and preserve non-editable fields
            var currentDetails = project.getBuildDetails();

            // Read potentially edited values from Build tab
            var newBuildLint = buildCleanCommandField.getText();
            var newTestAll = allTestsCommandField.getText();
            var newInstructions = buildInstructionsArea.getText();

            // Create a list from the DefaultListModel for excluded directories
            var newExcludedDirs = new HashSet<String>();
            if (excludedDirectoriesListModel != null) { // Check if initialized (project open)
                for (int i = 0; i < excludedDirectoriesListModel.getSize(); i++) {
                    newExcludedDirs.add(excludedDirectoriesListModel.getElementAt(i));
                }
            }

            // Create a new BuildDetails record with updated fields
            var newDetails = new BuildAgent.BuildDetails(currentDetails.buildFiles(),
                                                        currentDetails.dependencies(),
                                                        newBuildLint,
                                                        newTestAll,
                                                        newInstructions,
                                                        newExcludedDirs);

            logger.debug("Applying Build Details: {}", newDetails);

            // Only save if details have actually changed
            if (!newDetails.equals(currentDetails)) {
                project.saveBuildDetails(newDetails);
            }

            // Apply CPG Refresh Setting
            var selectedRefresh = (Project.CpgRefresh) cpgRefreshComboBox.getSelectedItem();
            if (selectedRefresh != project.getCpgRefresh()) {
                project.setAnalyzerRefresh(selectedRefresh);
            }

            // Apply Project Language
            var selectedLanguage = (io.github.jbellis.brokk.analyzer.Language) languageComboBox.getSelectedItem();
            if (selectedLanguage != null && selectedLanguage != project.getAnalyzerLanguage()) {
                project.setAnalyzerLanguage(selectedLanguage); // This might trigger analyzer rebuild
            }

            // Apply Style Guide
            var currentStyleGuide = project.getStyleGuide();
            var newStyleGuide = styleGuideArea.getText(); // Get text from the text area
            if (!newStyleGuide.equals(currentStyleGuide)) {
                project.saveStyleGuide(newStyleGuide);
                logger.debug("Applied Style Guide changes.");
            }

            // Apply Commit Message Format
            var currentCommitFormat = project.getCommitMessageFormat();
            var newCommitFormat = commitFormatArea.getText(); // Get text from the text area
            // The setter handles checking for changes and null/blank/default values
            project.setCommitMessageFormat(newCommitFormat);
            if (!newCommitFormat.trim().equals(currentCommitFormat)
                && !newCommitFormat.trim().equals(Project.DEFAULT_COMMIT_MESSAGE_FORMAT)
                && !newCommitFormat.isBlank())
            {
                logger.debug("Applied Commit Message Format changes.");
            } else if (!newCommitFormat.trim().equals(currentCommitFormat)
                       && (newCommitFormat.isBlank() || newCommitFormat.trim().equals(Project.DEFAULT_COMMIT_MESSAGE_FORMAT)))
            {
                logger.debug("Reset Commit Message Format to default.");
            }


            // Apply Data Retention Policy
            if (dataRetentionPanel != null) { // Check if the panel was created
                var oldPolicy = project.getDataRetentionPolicy();
                dataRetentionPanel.applyPolicy();
                var newPolicy = project.getDataRetentionPolicy();
                // Refresh models if the policy changed, as it might affect availability
                if (oldPolicy != newPolicy && newPolicy != Project.DataRetentionPolicy.UNSET) {
                    // Submit as background task so it doesn't block the settings dialog closing
                    chrome.getContextManager().submitBackgroundTask("Refreshing models due to policy change", () -> {
                        chrome.getContextManager().getModels().reinit(project);
                    });
                }
            }

            // Apply Model Selections and Reasoning Levels
            applyModelAndReasoning(project, architectModelComboBox, architectReasoningComboBox,
                                   project::getArchitectModelName, project::setArchitectModelName,
                                   project::getArchitectReasoningLevel, project::setArchitectReasoningLevel);

            applyModelAndReasoning(project, codeModelComboBox, codeReasoningComboBox,
                                   project::getCodeModelName, project::setCodeModelName,
                                   project::getCodeReasoningLevel, project::setCodeReasoningLevel);

            applyModelAndReasoning(project, askModelComboBox, askReasoningComboBox,
                                   project::getAskModelName, project::setAskModelName,
                                   project::getAskReasoningLevel, project::setAskReasoningLevel);

            applyModelAndReasoning(project, editModelComboBox, editReasoningComboBox,
                                   project::getEditModelName, project::setEditModelName,
                                   project::getEditReasoningLevel, project::setEditReasoningLevel);

            applyModelAndReasoning(project, searchModelComboBox, searchReasoningComboBox,
                                   project::getSearchModelName, project::setSearchModelName,
                                   project::getSearchReasoningLevel, project::setSearchReasoningLevel);
        }
    }


    /**
     * Helper method to apply model name and reasoning level settings
     */
    private void applyModelAndReasoning(Project project,
                                        JComboBox<String> modelCombo,
                                        JComboBox<Project.ReasoningLevel> reasoningCombo,
                                        java.util.function.Supplier<String> currentModelGetter,
                                        java.util.function.Consumer<String> modelSetter,
                                        java.util.function.Supplier<Project.ReasoningLevel> currentReasoningGetter,
                                        java.util.function.Consumer<Project.ReasoningLevel> reasoningSetter)
    {
        if (modelCombo != null) { // Check if combo box was initialized
            String selectedModel = (String) modelCombo.getSelectedItem();
            if (selectedModel != null && !selectedModel.equals(currentModelGetter.get())) {
                modelSetter.accept(selectedModel);
            }
        }
        if (reasoningCombo != null) { // Check if combo box was initialized
            Project.ReasoningLevel selectedReasoning = (Project.ReasoningLevel) reasoningCombo.getSelectedItem();
            // Only save if the combo box is enabled (i.e., model supports reasoning)
            // and the selected value is different from the current setting.
            if (selectedReasoning != null && reasoningCombo.isEnabled() && selectedReasoning != currentReasoningGetter.get()) {
                reasoningSetter.accept(selectedReasoning);
            }
            // If the combo is disabled, we don't save anything, implicitly leaving it as DEFAULT.
        }
    }

    /**
     * Updates the enabled state and selection of a reasoning combo box based on the selected model.
     */
    private void updateReasoningComboBox(JComboBox<String> modelComboBox, JComboBox<Project.ReasoningLevel> reasoningComboBox, Models models) {
        if (modelComboBox == null || reasoningComboBox == null) return; // Not initialized yet

        String selectedModelName = (String) modelComboBox.getSelectedItem();
        boolean supportsReasoning = selectedModelName != null && models.supportsReasoningEffort(selectedModelName);

        reasoningComboBox.setEnabled(supportsReasoning);
        reasoningComboBox.setToolTipText(supportsReasoning ? "Select reasoning effort" : "Reasoning effort not supported by this model");

        if (!supportsReasoning) {
            // Set underlying item to DEFAULT, but render as "Off" when disabled
            reasoningComboBox.setSelectedItem(Project.ReasoningLevel.DEFAULT);
            reasoningComboBox.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    // For the selected item display in a disabled box (index == -1), show "Off"
                    // Otherwise, show normal enum values for the dropdown list itself.
                    if (index == -1 && !reasoningComboBox.isEnabled()) {
                        label.setText("Off");
                        label.setForeground(UIManager.getColor("ComboBox.disabledForeground")); // Use standard disabled color
                    } else if (value instanceof Project.ReasoningLevel level) {
                        label.setText(level.toString()); // Use standard enum toString for dropdown items
                    } else {
                        label.setText(value == null ? "" : value.toString()); // Handle null or unexpected types
                    }
                    return label;
                }
            });
        } else {
            // Restore default renderer when enabled - needed if switching from non-supported to supported model
            reasoningComboBox.setRenderer(new DefaultListCellRenderer());
            // The actual selection is handled by the initial load or retained from previous state
        }
    }

    /**
     * Displays a standalone, modal dialog forcing the user to choose a data retention policy.
     * This should be called when a project is opened and has no policy set.
     *
     * @param project The project requiring a policy.
     * @param owner   The parent frame for the dialog.
     */
    public static void showStandaloneDataRetentionDialog(Project project, Frame owner) {
        var dialog = new JDialog(owner, "Data Retention Policy Required", true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE); // Prevent closing without selection initially
        dialog.setSize(600, 400);
        dialog.setLocationRelativeTo(owner);

        var retentionPanel = new DataRetentionPanel(project);

        var contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        contentPanel.add(retentionPanel, BorderLayout.CENTER);

        // Add note about changing settings later
        var noteLabel = new JLabel("<html>(You can change this setting later under Project -> Data Retention in the main Settings dialog.)</html>");
        // Use deriveFont with PLAIN style to ensure it's not italic
        noteLabel.setFont(noteLabel.getFont().deriveFont(Font.PLAIN, noteLabel.getFont().getSize() * 0.9f));
        contentPanel.add(noteLabel, BorderLayout.NORTH);

        var okButton = new JButton("OK");
        var buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(okButton);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Shared logic for handling the OK button click or window close attempt
        Runnable confirmAndClose = () -> {
            var selectedPolicy = retentionPanel.getSelectedPolicy();
            if (selectedPolicy == DataRetentionPolicy.UNSET) {
                // If no policy is selected, show a warning and *do not* close the dialog.
                JOptionPane.showMessageDialog(dialog, "Please select a data retention policy to continue.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            } else {
                // Only apply and close if a valid policy is selected.
                retentionPanel.applyPolicy();
                dialog.dispose();
            }
        };

        okButton.addActionListener(e -> confirmAndClose.run());

        // Handle the window close ('X') button
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmAndClose.run(); // Run the same logic as the OK button
            }
        });

        dialog.setContentPane(contentPanel);
        // Request focus on the OK button to avoid initial focus on a radio button
        okButton.requestFocusInWindow();
        dialog.setVisible(true); // Show the modal dialog and block until closed
    }

    /**
     * Opens the Settings dialog, optionally pre‑selecting an inner tab (e.g. “Models”)
     * inside the Project settings.  If the requested tab is not found—or no project
     * is open—the dialog falls back to its default view.
     */
    public static void showSettingsDialog(Chrome chrome, String targetTabName) {
        var dialog = new SettingsDialog(chrome.getFrame(), chrome);

        if (targetTabName != null) {
            boolean tabSelected = false;

            // Determine if target is a Global sub-tab
            boolean isGlobalSubTab = "Service".equals(targetTabName) ||
                                     "Appearance".equals(targetTabName) ||
                                     "Models".equals(targetTabName);

            // Determine if target is a Project sub-tab
            boolean isProjectSubTab = "General".equals(targetTabName) ||
                                      "Build".equals(targetTabName) ||
                                      "Data Retention".equals(targetTabName);


            if (isGlobalSubTab) {
                // Select "Global" top-level tab first
                for (int i = 0; i < dialog.tabbedPane.getTabCount(); i++) {
                    if ("Global".equals(dialog.tabbedPane.getTitleAt(i))) {
                        if (dialog.tabbedPane.isEnabledAt(i)) {
                            dialog.tabbedPane.setSelectedIndex(i);
                            // Now select the sub-tab
                            Component globalTabContent = dialog.tabbedPane.getComponentAt(i);
                            if (globalTabContent instanceof JTabbedPane globalSubTabbedPane) {
                                for (int j = 0; j < globalSubTabbedPane.getTabCount(); j++) {
                                    if (targetTabName.equals(globalSubTabbedPane.getTitleAt(j))) {
                                        // The "Models" sub-tab's content might be disabled, but the tab itself is selectable.
                                        // Its content (the panel from createModelsPanel) handles its own enabled state.
                                        globalSubTabbedPane.setSelectedIndex(j);
                                        tabSelected = true;
                                        break;
                                    }
                                }
                            }
                        } else {
                             logger.warn("Top-level 'Global' tab is unexpectedly disabled.");
                        }
                        break;
                    }
                }
            } else if (isProjectSubTab && chrome.getProject() != null) {
                // Select "Project" top-level tab first
                for (int i = 0; i < dialog.tabbedPane.getTabCount(); i++) {
                    if ("Project".equals(dialog.tabbedPane.getTitleAt(i))) {
                        if (dialog.tabbedPane.isEnabledAt(i)) {
                            dialog.tabbedPane.setSelectedIndex(i);
                            // Now select the sub-tab within Project
                            // dialog.projectPanel is the root panel for the "Project" tab.
                            Component projectTabContent = dialog.projectPanel;
                            if (projectTabContent instanceof JPanel) {
                                for (var comp : ((JPanel) projectTabContent).getComponents()) {
                                    if (comp instanceof JTabbedPane subTabbedPane) {
                                        for (int j = 0; j < subTabbedPane.getTabCount(); j++) {
                                            if (targetTabName.equals(subTabbedPane.getTitleAt(j))) {
                                                if (subTabbedPane.isEnabledAt(j)) {
                                                    subTabbedPane.setSelectedIndex(j);
                                                    tabSelected = true;
                                                } else {
                                                    logger.warn("Project sub-tab '{}' is disabled.", targetTabName);
                                                }
                                                break;
                                            }
                                        }
                                        break; // Found the inner JTabbedPane
                                    }
                                }
                            }
                        } else {
                            logger.warn("Top-level 'Project' tab is disabled, cannot select sub-tab '{}'.", targetTabName);
                        }
                        break;
                    }
                }
            } else {
                // Try to select a top-level tab directly (e.g., if targetTabName is "Global" or "Project")
                for (int i = 0; i < dialog.tabbedPane.getTabCount(); i++) {
                    if (targetTabName.equals(dialog.tabbedPane.getTitleAt(i))) {
                        if (dialog.tabbedPane.isEnabledAt(i)) {
                            dialog.tabbedPane.setSelectedIndex(i);
                            tabSelected = true;
                        } else {
                            logger.warn("Target tab '{}' is disabled, cannot select.", targetTabName);
                        }
                        break;
                    }
                }
            }

            if (!tabSelected) {
                 logger.warn("Could not find or select target settings tab: {}", targetTabName);
            }
        }
        dialog.setVisible(true); // show the modal dialog
    }
}
