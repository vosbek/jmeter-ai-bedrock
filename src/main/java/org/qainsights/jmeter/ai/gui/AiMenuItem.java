package org.qainsights.jmeter.ai.gui;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.MainFrame;
import org.apache.jmeter.gui.util.JMeterToolBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Objects;

public class AiMenuItem extends JMenuItem implements ActionListener{
    private static final Logger log = LoggerFactory.getLogger(AiMenuItem.class);
    private static final Action vtg = new AI();
    private AiChatPanel currentChatPanel;

    public AiMenuItem() {
        super(vtg);
        addActionListener(this);
        addToolbarIcon();
    }

    public static ImageIcon getButtonIcon(int pixelSize) {
        String sizedImage = String.format("/org/qainsights/jmeter/validatetg/validate-tg-icon-%2dx%2d.png", pixelSize, pixelSize);
        return new ImageIcon(Objects.requireNonNull(AiMenuItem.class.getResource(sizedImage)));
    }

    private void addToolbarIcon() {
        GuiPackage instance = GuiPackage.getInstance();
        if (instance != null) {
            final MainFrame mf = instance.getMainFrame();
            final ComponentFinder<JMeterToolBar> finder = new ComponentFinder<>(JMeterToolBar.class);
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    JMeterToolBar toolbar = null;
                    while (toolbar == null) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            log.debug("Couldn't add button to menubar", e);
                        }
                        log.debug("Searching for toolbar ... ");
                        toolbar = finder.findComponentIn(mf);
                    }
                    int pos = getPositionForIcon(toolbar.getComponents());
                    log.debug("validate rootPos: " + String.valueOf(pos));
                    Component toolbarButton = getToolbarButton();
                    toolbarButton.setSize(toolbar.getComponent(pos).getSize());
                    toolbar.add(toolbarButton, pos);
                }
            });
        }
    }

    private JButton getToolbarButton() {
        JButton button = new JButton(getButtonIcon(22));
        button.setToolTipText("Toggle AI Chat Panel");
        button.addActionListener(this);
        button.setActionCommand("toggle_ai_panel");
        return button;
    }

    private int getPositionForIcon(Component[] toolbarComponents) {
        int index = 0;
        for (Component item : toolbarComponents) {
            String itemClassName = item.getClass().getName();
            if(itemClassName.contains("javax.swing.JButton")) {
                String actionCommandText = ((JButton) item).getModel().getActionCommand();
                log.debug("Running for iteration: "+ index + ", " + actionCommandText);
                if (actionCommandText != null && actionCommandText.equals("start")){
                    break;
                }
            }
            index++;
        }
        return index;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        log.debug("Clicked" + e.getActionCommand());
        try {
            log.info("Calling AI panel");
            openAiChatPanel();
//            ActionRouter.getInstance().doActionNow(e);
        }
        catch (Exception err) {
            log.debug("Error while TG action performed: " + err);
        }
    }

    private void openAiChatPanel() {
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage != null) {
            MainFrame mainFrame = guiPackage.getMainFrame();
            
            if (currentChatPanel != null && currentChatPanel.isShowing()) {
                // Panel is currently shown, remove it
                mainFrame.getContentPane().remove(currentChatPanel);
            } else {
                // Panel is not shown, add it
                if (currentChatPanel == null) {
                    // Only create a new panel if one doesn't exist
                    currentChatPanel = new AiChatPanel();
                }
                mainFrame.getContentPane().add(currentChatPanel, BorderLayout.EAST);
            }
            
            mainFrame.revalidate();
            mainFrame.repaint();
        }
    }
}
