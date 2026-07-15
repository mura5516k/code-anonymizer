package com.mura.codeanonymizer.ui;

import com.mura.codeanonymizer.core.mapping.MappingStore;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;

public class MainFrame extends JFrame {

    private MappingStore store;
    private final JLabel statusBar = new JLabel(" ");
    private MappingTab mappingTab;

    public MainFrame(MappingStore store) {
        super("コード匿名化ツール");
        this.store = store;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1000, 700));

        JTabbedPane tabbedPane = new JTabbedPane();
        AnonymizeTab anonymizeTab = new AnonymizeTab(this);
        RestoreTab restoreTab = new RestoreTab(this);
        mappingTab = new MappingTab(this);

        tabbedPane.addTab("匿名化", anonymizeTab);
        tabbedPane.addTab("復元", restoreTab);
        tabbedPane.addTab("マッピング", mappingTab);

        statusBar.setHorizontalAlignment(SwingConstants.LEFT);
        statusBar.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8));

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(tabbedPane, BorderLayout.CENTER);
        getContentPane().add(statusBar, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    public MappingStore getStore() {
        return store;
    }

    public void setStore(MappingStore newStore) {
        this.store = newStore;
        refreshMappingTable();
        setStatus("マッピングファイルを切り替えました: " + newStore.getPath());
    }

    public void setStatus(String message) {
        statusBar.setText(message);
    }

    public void refreshMappingTable() {
        if (mappingTab != null) {
            mappingTab.refresh();
        }
    }
}
