/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011 Philipp C. Heckel <philipp.heckel@gmail.com> 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.syncany.gui.settings;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.syncany.Constants;
import org.syncany.config.Config;
import org.syncany.config.Profile;
import org.syncany.exceptions.ConfigException;
import org.syncany.gui.settings.ActionTreeItem.ActionTreeItemEvent;
import org.syncany.gui.tray.Tray;
import org.syncany.gui.wizard.WizardDialog;
import org.syncany.util.FileUtil;

/**
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class SettingsDialog extends JFrame {

    private static final Config config = Config.getInstance();
    private ResourceBundle resourceBundle;

    /** Creates new form SettingsDialog */
    public SettingsDialog() {
        super();
        resourceBundle = Config.getInstance().getResourceBundle();
        initComponents();

        // Init dialog!
        initDialog();

        initTreeModelStatic();
        initTreeListeners();
        initTreeUI();

        initSettingsPanels();
    }

    private void initDialog() {
        // Set some stuff
        setResizable(false);
        setLocationRelativeTo(null); // center
        getRootPane().setDefaultButton(btnOkay);

        // Load logos and buttons
        lblTopImage.setText("");
        lblTopImage.setIcon(new ImageIcon(config.getResDir() + File.separator + "settings-top.png"));

        lblDonate.setText("");
        lblDonate.setIcon(new ImageIcon(config.getResDir() + File.separator + "paypal-donate.png"));

        // Add listeners
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                btnCancelActionPerformed(null);
            }
        });
    }

    private void initTreeModelStatic() {
        // Make model and pass it to tree
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();

        // - Application
        DefaultMutableTreeNode appNode =
                new DefaultMutableTreeNode(new ShowPanelTreeItem(resourceBundle.getString("sd_app_settings"), "application", new ApplicationPanel()));

        root.add(appNode);

        // - Application / Plugins
        appNode.add(new DefaultMutableTreeNode(new ShowPanelTreeItem(resourceBundle.getString("sd_plugins"), "plugins", new PluginsPanel())));

        /*appNode.add(new DefaultMutableTreeNode(new ShowPanelTreeItem("Proxy Configuration", "application", new ProxyPanel())));
        appNode.add(new DefaultMutableTreeNode(new ShowPanelTreeItem("Bandwidth Limits", "bandwidth", new BandwidthLimitPanel())));*/

        //
        // NOTE: Profiles are inserted after everything else, dynamically!
        //

        // - Create new profile
        final SettingsDialog dlg = this;

        root.add(new DefaultMutableTreeNode(new ActionTreeItem(resourceBundle.getString("sd_create_new_profile"), "profile", ActionTreeItemEvent.DOUBLE_CLICKED) {

            @Override
            public void doAction() {
                Profile profile = WizardDialog.showWizard(dlg);

                if (profile == null) {
                    System.err.println("Cancel clicked.");
                    return;
                }

                System.out.println("SUCCESS:" + profile);
                //System.out.println("connection: "+profile.getRepository().getConnection());
                config.getProfiles().add(profile);
                addProfileToTree(profile, true);
            }
        }));

        // Set it!
        tree.setModel(new DefaultTreeModel(root));
    }

    private void initTreeListeners() {
        tree.addTreeSelectionListener(new TreeSelectionListener() {

            @Override
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();

                if (selectedNode == null) {
                    return;
                }

                Object userObj = selectedNode.getUserObject();

                if (userObj instanceof ShowPanelTreeItem) {
                    scrMain.setViewportView(((ShowPanelTreeItem) userObj).getPanel());
                }
            }
        });

        tree.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());

                if (selPath == null) {
                    return;
                }

                // Get clicked node
                DefaultMutableTreeNode clickedNode = (DefaultMutableTreeNode) selPath.getLastPathComponent();
                Object userObj = clickedNode.getUserObject();

                if (userObj instanceof ActionTreeItem) {
                    ActionTreeItem action = (ActionTreeItem) userObj;

                    // React!
                    if (e.getClickCount() == 1 && action.getEvent() == ActionTreeItemEvent.CLICKED) {
                        action.doAction();
                    } else if (e.getClickCount() == 2 && action.getEvent() == ActionTreeItemEvent.DOUBLE_CLICKED) {
                        action.doAction();
                    }
                }
            }
        });
    }

    private void initTreeUI() {
        tree.setCellRenderer(new SettingsTreeCellRenderer());
        tree.setRootVisible(false);

        // Expand all
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private void initSettingsPanels() {
        for (SettingsPanel panel : getSettingsPanels()) {
            panel.load();
        }

        tree.setSelectionRow(0);
    }

    public void addProfileToTree(Profile profile, boolean selectRow) {
        // Panels
        ProfilePanel profilePanel = new ProfilePanel(profile);
        RepositoryPanel repoPanel = new RepositoryPanel(profile);
        FoldersPanel foldersPanel = new FoldersPanel(profile);

        profilePanel.load();
        repoPanel.load();
        foldersPanel.load();

        // Add to tree
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();

        DefaultMutableTreeNode profileNode = new DefaultMutableTreeNode(
                new ShowPanelTreeItem(profile.getName(), "profile", profilePanel));

        profileNode.add(new DefaultMutableTreeNode(new ShowPanelTreeItem(resourceBundle.getString("sd_remote_storage"), "repository", repoPanel)));
        profileNode.add(new DefaultMutableTreeNode(new ShowPanelTreeItem(resourceBundle.getString("sd_sync_folders"), "folders", foldersPanel)));

        model.insertNodeInto(profileNode, root, root.getChildCount() - 1);

        if (selectRow) {
            int profileNodeIndex = tree.getRowForPath(new TreePath(profileNode.getPath()));

            tree.expandRow(profileNodeIndex);
            tree.setSelectionRow(profileNodeIndex);
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) throws ConfigException, InterruptedException {
        config.load();

        EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                SettingsDialog dialog = new SettingsDialog();
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {

                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        System.exit(0);
                    }
                });
                dialog.setVisible(true);
            }
        });
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);

        // (Re-)Load panels
        if (b) {
            initSettingsPanels();
        }
    }

    private List<DefaultMutableTreeNode> getAllNodes(DefaultMutableTreeNode root) {
        // node is visited exactly once
        List<DefaultMutableTreeNode> nodes = new ArrayList<DefaultMutableTreeNode>();

        if (root.getChildCount() >= 0) {
            for (Enumeration e = root.children(); e.hasMoreElements();) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();

                nodes.add(node);
                nodes.addAll(getAllNodes(node));
            }
        }

        return nodes;
    }

    private List<SettingsPanel> getSettingsPanels() {
        List<SettingsPanel> panels = new ArrayList<SettingsPanel>();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();

        for (DefaultMutableTreeNode node : getAllNodes(root)) {
            Object userObj = node.getUserObject();

            if (!(userObj instanceof ShowPanelTreeItem)) {
                continue;
            }

            ShowPanelTreeItem item = (ShowPanelTreeItem) userObj;
            panels.add(item.getPanel());
        }

        return panels;
    }

    /**
     * @see http://download.oracle.com/javase/tutorial/uiswing/examples/components/TreeIconDemo2Project/src/components/TreeIconDemo2.java
     */
    private class SettingsTreeCellRenderer extends DefaultTreeCellRenderer {

        public SettingsTreeCellRenderer() {
            super();

            setBorderSelectionColor(null);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            Component origComp = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (!(value instanceof DefaultMutableTreeNode)) {
                return origComp;
            }

            Object userObj = ((DefaultMutableTreeNode) value).getUserObject();

            if (!(userObj instanceof TreeItem)) {
                return origComp;
            }

            setIcon(new ImageIcon(config.getResDir() + File.separator + resourceBundle.getString("sd_settings") + "-" + ((TreeItem) userObj).getIconFilename() + ".png"));
            return this;
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
        // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
        private void initComponents() {

                pnlTop = new javax.swing.JPanel();
                lblTopImage = new javax.swing.JLabel();
                treeScrollPane = new javax.swing.JScrollPane();
                tree = new javax.swing.JTree();
                pnlBottom = new javax.swing.JPanel();
                btnCancel = new javax.swing.JButton();
                btnOkay = new javax.swing.JButton();
                lblDonate = new javax.swing.JLabel();
                scrMain = new javax.swing.JScrollPane();

                setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
                setTitle(resourceBundle.getString("sd_form_title")); // NOI18N
                setName("Form"); // NOI18N

                pnlTop.setName("pnlTop"); // NOI18N

                lblTopImage.setText(resourceBundle.getString("sd_top_image")); // NOI18N
                lblTopImage.setName("lblTopImage"); // NOI18N

                javax.swing.GroupLayout pnlTopLayout = new javax.swing.GroupLayout(pnlTop);
                pnlTop.setLayout(pnlTopLayout);
                pnlTopLayout.setHorizontalGroup(
                        pnlTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(lblTopImage, javax.swing.GroupLayout.DEFAULT_SIZE, 767, Short.MAX_VALUE)
                );
                pnlTopLayout.setVerticalGroup(
                        pnlTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(lblTopImage, javax.swing.GroupLayout.DEFAULT_SIZE, 88, Short.MAX_VALUE)
                );

                treeScrollPane.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
                treeScrollPane.setViewportBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
                treeScrollPane.setName("treeScrollPane"); // NOI18N

                tree.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
                tree.setFont(tree.getFont().deriveFont(tree.getFont().getSize()+1f));
                tree.setName("tree"); // NOI18N
                tree.setRootVisible(false);
                tree.setRowHeight(22);
                treeScrollPane.setViewportView(tree);

                pnlBottom.setName("pnlBottom"); // NOI18N

                javax.swing.ActionMap actionMap = org.jdesktop.application.Application.getInstance().getContext().getActionMap(SettingsDialog.class, this);
                btnCancel.setAction(actionMap.get("cancelClicked")); // NOI18N
                btnCancel.setText(resourceBundle.getString("sd_cancel")); // NOI18N
                btnCancel.setName("btnCancel"); // NOI18N
                btnCancel.addActionListener(new java.awt.event.ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                                btnCancelActionPerformed(evt);
                        }
                });

                btnOkay.setAction(actionMap.get("okayClicked")); // NOI18N
                btnOkay.setText(resourceBundle.getString("sd_ok")); // NOI18N
                btnOkay.setName("btnOkay"); // NOI18N
                btnOkay.addActionListener(new java.awt.event.ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                                btnOkayActionPerformed(evt);
                        }
                });

                lblDonate.setText(resourceBundle.getString("sd_donate")); // NOI18N
                lblDonate.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
                lblDonate.setName("lblDonate"); // NOI18N
                lblDonate.addMouseListener(new java.awt.event.MouseAdapter() {
                        public void mouseClicked(java.awt.event.MouseEvent evt) {
                                lblDonateMouseClicked(evt);
                        }
                });

                javax.swing.GroupLayout pnlBottomLayout = new javax.swing.GroupLayout(pnlBottom);
                pnlBottom.setLayout(pnlBottomLayout);
                pnlBottomLayout.setHorizontalGroup(
                        pnlBottomLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnlBottomLayout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(lblDonate, javax.swing.GroupLayout.PREFERRED_SIZE, 121, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 418, Short.MAX_VALUE)
                                .addComponent(btnOkay, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btnCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap())
                );
                pnlBottomLayout.setVerticalGroup(
                        pnlBottomLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(pnlBottomLayout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(pnlBottomLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                        .addComponent(btnCancel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, pnlBottomLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                .addComponent(btnOkay, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                .addComponent(lblDonate, javax.swing.GroupLayout.DEFAULT_SIZE, 21, Short.MAX_VALUE)))
                                .addContainerGap(14, Short.MAX_VALUE))
                );

                scrMain.setBorder(null);
                scrMain.setViewportBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
                scrMain.setName("scrMain"); // NOI18N

                javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
                getContentPane().setLayout(layout);
                layout.setHorizontalGroup(
                        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(treeScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 217, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(scrMain, javax.swing.GroupLayout.DEFAULT_SIZE, 544, Short.MAX_VALUE))
                        .addComponent(pnlBottom, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(pnlTop, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                );
                layout.setVerticalGroup(
                        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addComponent(pnlTop, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                        .addComponent(treeScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 424, Short.MAX_VALUE)
                                        .addComponent(scrMain, javax.swing.GroupLayout.DEFAULT_SIZE, 424, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(pnlBottom, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                );

                pack();
        }// </editor-fold>//GEN-END:initComponents

    private void lblDonateMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lblDonateMouseClicked
        FileUtil.browsePage(Constants.APPLICATION_URL);
    }//GEN-LAST:event_lblDonateMouseClicked

    private void btnOkayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOkayActionPerformed
        for (SettingsPanel pnl : getSettingsPanels()) {
            pnl.save();
        }

        // Save config
        try {
            config.save();
        }
        catch (ConfigException ex) {
            Logger.getLogger(SettingsDialog.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        // Update settings dialog
        new Thread(new Runnable() {
            @Override
            public void run() {
               for (Profile p : config.getProfiles().list()) {                    
                    p.setActive(p.isEnabled());
                }
            }
        },"UpdateProfiles").start();
                
        // Update tray menu
        Tray.getInstance().updateUI();
        
        setVisible(false);
    }//GEN-LAST:event_btnOkayActionPerformed

    private void btnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCancelActionPerformed
        setVisible(false);
    }//GEN-LAST:event_btnCancelActionPerformed
        // Variables declaration - do not modify//GEN-BEGIN:variables
        private javax.swing.JButton btnCancel;
        private javax.swing.JButton btnOkay;
        private javax.swing.JLabel lblDonate;
        private javax.swing.JLabel lblTopImage;
        private javax.swing.JPanel pnlBottom;
        private javax.swing.JPanel pnlTop;
        private javax.swing.JScrollPane scrMain;
        private javax.swing.JTree tree;
        private javax.swing.JScrollPane treeScrollPane;
        // End of variables declaration//GEN-END:variables
}
