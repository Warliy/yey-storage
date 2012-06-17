/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * RepositoryTestPanel2.java
 *
 * Created on May 4, 2011, 6:27:37 PM
 */
package org.syncany.gui.wizard;

import java.util.ResourceBundle;

import org.syncany.config.Config;
import org.syncany.config.Profile;
import org.syncany.config.Repository;
import org.syncany.exceptions.NoRepositoryFoundException;
import org.syncany.exceptions.RepositoryFoundException;
import org.syncany.exceptions.StorageConnectException;
import org.syncany.gui.settings.SettingsPanel;
import org.syncany.util.StringUtil;

/**
 *
 * @author pheckel
 */
public class RepositoryTestPanel extends SettingsPanel {
    private Profile profile;
    private ResourceBundle resourceBundle;
    
    /** Creates new form RepositoryTestPanel2 */
    public RepositoryTestPanel(Profile profile) {
    	resourceBundle = Config.getInstance().getResourceBundle();
        initComponents();
	
        this.profile = profile;	
        this.scrDetails.setVisible(false);
        this.txtDetails.setText("");
    }
    
    public void doRepoAction(final boolean create, final TestListener callbackListener) {
        progress.setMinimum(0);
        progress.setMaximum(3);
        progress.setValue(1);

        // Go!
        new Thread(new Runnable() {
            @Override
            public void run() {
                Repository repository = profile.getRepository();

                if (create) {
                    try {                        
                        progress.setValue(1);
                        setStatus(resourceBundle.getString("reptest_create_remote_storage"));
                        repository.commit(true);

                        progress.setValue(2);
                        setStatus(resourceBundle.getString("reptest_testing_storage"));
                        repository.update();

                        progress.setValue(progress.getMaximum());
                        setStatus(resourceBundle.getString("reptest_created_status_ok"));

                        callbackListener.actionCompleted(true);
                    }
                    catch (StorageConnectException e) {
                        setStatus(resourceBundle.getString("reptest_connection_status_fail")+e.getMessage());
                        setError(e);
			
                        callbackListener.actionCompleted(false);
                    }
                    catch (RepositoryFoundException e) {
                        setStatus(resourceBundle.getString("reptest_is_initialised"));
                        setError(e);

                        callbackListener.actionCompleted(false);
                    }
                    catch (Exception e) {
                        setStatus(e.getMessage());
                        setError(e);

                        callbackListener.actionCompleted(false);
                    }
                }

                // Update
                else {
                    try {
                        setStatus(resourceBundle.getString("reptest_connection_to_cloud_storage"));
                        repository.update();

                        progress.setValue(progress.getMaximum());
                        setStatus(resourceBundle.getString("reptest_found"));
			
                        callbackListener.actionCompleted(true);
                    }
                    catch (StorageConnectException e) {
                        progress.setValue(progress.getMaximum());
                        setStatus(resourceBundle.getString("reptest_connection_status_fail") + e.getMessage());
                        setError(e);
			
                        callbackListener.actionCompleted(false);
                    }
                    catch (NoRepositoryFoundException e) {
                        setStatus(resourceBundle.getString("reptest_nothing_found") + e.getMessage());
                        setError(e);

                        callbackListener.actionCompleted(false);
                    }
                    catch (Exception e) {
                        setStatus(e.getMessage());
                        setError(e);
			
                        callbackListener.actionCompleted(false);                        
                    }
                }

            }
        }, "RepositoryTest").start();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
        // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
        private void initComponents() {

                lblProgress = new javax.swing.JLabel();
                progress = new javax.swing.JProgressBar();
                jLabel1 = new javax.swing.JLabel();
                jLabel2 = new javax.swing.JLabel();
                jLabel3 = new javax.swing.JLabel();
                scrDetails = new javax.swing.JScrollPane();
                txtDetails = new javax.swing.JTextArea();
                chkToggleDetails = new javax.swing.JCheckBox();

                lblProgress.setText(resourceBundle.getString("reptest_progress"));
                lblProgress.setName("lblProgress"); // NOI18N

                progress.setName("progress"); // NOI18N

                jLabel1.setFont(jLabel1.getFont().deriveFont(jLabel1.getFont().getStyle() | java.awt.Font.BOLD, jLabel1.getFont().getSize()+2));
                jLabel1.setText(resourceBundle.getString("reptest_init_remote_storage"));
                jLabel1.setName("jLabel1"); // NOI18N

                jLabel2.setText(resourceBundle.getString("reptest_message_part1"));
                jLabel2.setName("jLabel2"); // NOI18N

                jLabel3.setText(resourceBundle.getString("reptest_message_part2"));
                jLabel3.setName("jLabel3"); // NOI18N

                scrDetails.setName("scrDetails"); // NOI18N

                txtDetails.setColumns(20);
                txtDetails.setRows(5);
                txtDetails.setName("txtDetails"); // NOI18N
                scrDetails.setViewportView(txtDetails);

                chkToggleDetails.setText(resourceBundle.getString("reptest_details"));
                chkToggleDetails.setName("chkToggleDetails"); // NOI18N
                chkToggleDetails.addActionListener(new java.awt.event.ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                                chkToggleDetailsActionPerformed(evt);
                        }
                });

                javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
                this.setLayout(layout);
                layout.setHorizontalGroup(
                        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                        .addComponent(scrDetails, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 475, Short.MAX_VALUE)
                                        .addComponent(progress, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 475, Short.MAX_VALUE)
                                        .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jLabel2, javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jLabel3, javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(lblProgress, javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(chkToggleDetails, javax.swing.GroupLayout.Alignment.LEADING))
                                .addContainerGap())
                );
                layout.setVerticalGroup(
                        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(jLabel1)
                                .addGap(6, 6, 6)
                                .addComponent(jLabel2)
                                .addGap(4, 4, 4)
                                .addComponent(jLabel3)
                                .addGap(32, 32, 32)
                                .addComponent(progress, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(lblProgress)
                                .addGap(18, 18, 18)
                                .addComponent(chkToggleDetails)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(scrDetails, javax.swing.GroupLayout.PREFERRED_SIZE, 174, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap(22, Short.MAX_VALUE))
                );
        }// </editor-fold>//GEN-END:initComponents

    private void chkToggleDetailsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkToggleDetailsActionPerformed
        scrDetails.setVisible(chkToggleDetails.isSelected());
}//GEN-LAST:event_chkToggleDetailsActionPerformed

        // Variables declaration - do not modify//GEN-BEGIN:variables
        private javax.swing.JCheckBox chkToggleDetails;
        private javax.swing.JLabel jLabel1;
        private javax.swing.JLabel jLabel2;
        private javax.swing.JLabel jLabel3;
        private javax.swing.JLabel lblProgress;
        private javax.swing.JProgressBar progress;
        private javax.swing.JScrollPane scrDetails;
        private javax.swing.JTextArea txtDetails;
        // End of variables declaration//GEN-END:variables

    @Override
    public void load() {
        setStatus("");
    }

    @Override
    public void save() {
        // ..
    }
    
    private void setStatus(String s) {
        txtDetails.append(s);
        lblProgress.setText(s);
    }
    
    private void setError(Throwable e) {
        progress.setValue(progress.getMaximum());
        txtDetails.append(StringUtil.getStackTrace(e)+"\n");			
        e.printStackTrace(System.err);			
        
        chkToggleDetails.setSelected(true);
        scrDetails.setVisible(true);
    }
    
    public interface TestListener {
        public void actionCompleted(boolean success);
    }
}
