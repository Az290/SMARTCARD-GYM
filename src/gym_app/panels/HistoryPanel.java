package gym_app.panels;

import gym_app.MainFrame;
import gym_app.components.*;
import gym_app.DatabaseService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Màn hình lịch sử giao dịch
 */
public class HistoryPanel extends JPanel {

    private MainFrame mainFrame;

    private JTable transactionTable;
    private DefaultTableModel tableModel;
    private JLabel lblTotalTopup;
    private JLabel lblTotalSpent;
    private JLabel lblTransactionCount;
    private JComboBox<String> cboFilter;

    public HistoryPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBackground(new Color(30, 30, 45));

        // Side Menu
        add(new SideMenu(mainFrame), BorderLayout.WEST);

        // Main Content
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(new Color(30, 30, 45));
        content.setBorder(new EmptyBorder(30, 40, 30, 40));

        // Header
        JPanel headerPanel = createHeaderPanel();

        // Summary cards
        JPanel summaryPanel = createSummaryPanel();

        // Filter
        JPanel filterPanel = createFilterPanel();

        // Table
        JPanel tablePanel = createTablePanel();

        // Back button
        GymButton btnBack = new GymButton("← Quay lại", new Color(100, 100, 120));
        btnBack.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnBack.addActionListener(e -> mainFrame.showScreen(MainFrame.SCREEN_DASHBOARD));

        // Layout
        content.add(headerPanel);
        content.add(Box.createVerticalStrut(20));
        content.add(summaryPanel);
        content.add(Box.createVerticalStrut(20));
        content.add(filterPanel);
        content.add(Box.createVerticalStrut(15));
        content.add(tablePanel);
        content.add(Box.createVerticalStrut(20));
        content.add(btnBack);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(30, 30, 45));
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(30, 30, 45));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        JLabel title = new JLabel(" LỊCH SỬ GIAO DỊCH");
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setForeground(new Color(241, 196, 15));

        GymButton btnRefresh = new GymButton(" Làm mới", new Color(52, 152, 219));
        btnRefresh.setPreferredSize(new Dimension(130, 40));
        btnRefresh.addActionListener(e -> loadHistory());

        panel.add(title, BorderLayout.WEST);
        panel.add(btnRefresh, BorderLayout.EAST);

        return panel;
    }

    private JPanel createSummaryPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 15, 0));
        panel.setBackground(new Color(30, 30, 45));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Khởi tạo labels trước
        lblTotalTopup = new JLabel("0 VNĐ");
        lblTotalTopup.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTotalTopup.setForeground(new Color(46, 204, 113));
        lblTotalTopup.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblTotalSpent = new JLabel("0 VNĐ");
        lblTotalSpent.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTotalSpent.setForeground(new Color(231, 76, 60));
        lblTotalSpent.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblTransactionCount = new JLabel("0");
        lblTransactionCount.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTransactionCount.setForeground(new Color(52, 152, 219));
        lblTransactionCount.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Tạo cards với labels đã khởi tạo
        JPanel topupCard = createSummaryCard(" Tổng nạp", lblTotalTopup, new Color(46, 204, 113));
        JPanel spentCard = createSummaryCard(" Tổng chi", lblTotalSpent, new Color(231, 76, 60));
        JPanel countCard = createSummaryCard(" Số giao dịch", lblTransactionCount, new Color(52, 152, 219));

        panel.add(topupCard);
        panel.add(spentCard);
        panel.add(countCard);

        return panel;
    }

    /**
     * Tạo summary card với label được truyền vào
     */
    private JPanel createSummaryCard(String title, JLabel valueLabel, Color color) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(40, 40, 55));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 2),
                new EmptyBorder(15, 20, 15, 20)
        ));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblTitle.setForeground(Color.GRAY);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(Box.createVerticalGlue());
        card.add(lblTitle);
        card.add(Box.createVerticalStrut(8));
        card.add(valueLabel);
        card.add(Box.createVerticalGlue());

        return card;
    }

    private JPanel createFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        panel.setBackground(new Color(30, 30, 45));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblFilter = new JLabel(" Lọc theo:");
        lblFilter.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblFilter.setForeground(Color.WHITE);

        cboFilter = new JComboBox<>(new String[]{
            "Tất cả",
            "Nạp tiền",
            "Mua gói tập",
            "Check-in",
            "7 ngày gần nhất",
            "30 ngày gần nhất"
        });
        cboFilter.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        cboFilter.setBackground(new Color(50, 50, 70));
        cboFilter.setForeground(Color.WHITE);
        cboFilter.setPreferredSize(new Dimension(200, 35));
        cboFilter.addActionListener(e -> applyFilter());

        // Export button
        GymButton btnExport = new GymButton(" Xuất CSV", new Color(100, 100, 130));
        btnExport.setPreferredSize(new Dimension(120, 35));
        btnExport.addActionListener(e -> exportToCSV());

        panel.add(lblFilter);
        panel.add(cboFilter);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(btnExport);

        return panel;
    }

    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(40, 40, 55));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 80)),
                new EmptyBorder(10, 10, 10, 10)
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
        panel.setPreferredSize(new Dimension(0, 350));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Table model
        String[] columns = {"#", "Loại", "Số tiền", "Chi tiết", "Thời gian"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        transactionTable = new JTable(tableModel);
        transactionTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        transactionTable.setRowHeight(40);
        transactionTable.setBackground(new Color(50, 50, 65));
        transactionTable.setForeground(Color.WHITE);
        transactionTable.setGridColor(new Color(70, 70, 90));
        transactionTable.setSelectionBackground(new Color(0, 150, 136));
        transactionTable.setSelectionForeground(Color.WHITE);
        transactionTable.setShowVerticalLines(false);

        // Header style
        JTableHeader header = transactionTable.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 14));
        header.setBackground(new Color(60, 60, 80));
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(0, 45));
        header.setReorderingAllowed(false);

        // Column widths
        transactionTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        transactionTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        transactionTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        transactionTable.getColumnModel().getColumn(3).setPreferredWidth(200);
        transactionTable.getColumnModel().getColumn(4).setPreferredWidth(180);

        // Center align for # column
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        transactionTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);

        // Custom renderer for amount column
        transactionTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                String amountStr = value != null ? value.toString() : "";
                if (amountStr.startsWith("+")) {
                    c.setForeground(new Color(46, 204, 113)); // Green for topup
                } else if (amountStr.startsWith("-")) {
                    c.setForeground(new Color(231, 76, 60)); // Red for spent
                } else {
                    c.setForeground(Color.WHITE);
                }

                if (isSelected) {
                    c.setForeground(Color.WHITE);
                }

                setHorizontalAlignment(SwingConstants.RIGHT);
                setBackground(isSelected ? new Color(0, 150, 136) : new Color(50, 50, 65));
                return c;
            }
        });

        // Custom renderer for type column
        transactionTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setForeground(Color.WHITE);
                setBackground(isSelected ? new Color(0, 150, 136) : new Color(50, 50, 65));
                return c;
            }
        });

        // Default renderer for other columns
        DefaultTableCellRenderer defaultRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setForeground(Color.WHITE);
                setBackground(isSelected ? new Color(0, 150, 136) : new Color(50, 50, 65));
                return c;
            }
        };
        transactionTable.getColumnModel().getColumn(3).setCellRenderer(defaultRenderer);
        transactionTable.getColumnModel().getColumn(4).setCellRenderer(defaultRenderer);

        JScrollPane tableScroll = new JScrollPane(transactionTable);
        tableScroll.setBorder(null);
        tableScroll.getViewport().setBackground(new Color(50, 50, 65));

        // Empty message
        JLabel emptyLabel = new JLabel("Chưa có giao dịch nào", SwingConstants.CENTER);
        emptyLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        emptyLabel.setForeground(Color.GRAY);

        panel.add(tableScroll, BorderLayout.CENTER);

        return panel;
    }

    public void loadHistory() {
        tableModel.setRowCount(0);

        String cardId = mainFrame.getCurrentCardId();
        if (cardId == null) {
            return; // ... (Giữ nguyên đoạn check null)
        }
        List<DatabaseService.TransactionInfo> transactions
                = mainFrame.getDbService().getTransactionHistory(cardId, 100);

        long totalTopup = 0;
        long totalSpent = 0;
        int count = 0;

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm");

        for (DatabaseService.TransactionInfo tx : transactions) {
            count++;
            String type = "";
            String amount = "";
            String detail = "";

            // Xử lý hiển thị dựa trên loại giao dịch
            if ("CHECKIN".equalsIgnoreCase(tx.type)) {
                type = "🏃 Check-in";
                amount = "---"; // Check-in không tốn tiền
                detail = "Vào phòng tập";
            } else if ("TOPUP".equalsIgnoreCase(tx.type)) {
                type = "💰 Nạp tiền";
                amount = "+" + String.format("%,d VNĐ", tx.amount);
                detail = "Nạp tiền vào tài khoản";
                totalTopup += tx.amount; // Cộng tổng nạp
            } else { // BUY_PACKAGE
                type = "🛒 Mua gói";
                amount = "-" + String.format("%,d VNĐ", tx.amount);
                detail = tx.packageName != null ? tx.packageName : "Mua gói tập";
                if (tx.trainerName != null) {
                    detail += " + HLV " + tx.trainerName;
                }
                totalSpent += tx.amount; // Cộng tổng chi
            }

            String time = tx.transTime != null ? sdf.format(tx.transTime) : "---";

            tableModel.addRow(new Object[]{count, type, amount, detail, time});
        }

        // Update summary labels
        lblTotalTopup.setText(String.format("%,d VNĐ", totalTopup));
        lblTotalSpent.setText(String.format("%,d VNĐ", totalSpent));
        lblTransactionCount.setText(String.valueOf(count));
    }

    private void applyFilter() {
        String filter = (String) cboFilter.getSelectedItem();
        if (filter == null) {
            return;
        }

        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        transactionTable.setRowSorter(sorter);

        RowFilter<DefaultTableModel, Object> rf = null;

        switch (filter) {
            case "Nạp tiền":
                rf = RowFilter.regexFilter(".*Nạp.*", 1);
                break;
            case "Mua gói tập":
                rf = RowFilter.regexFilter(".*Mua.*", 1);
                break;
            // Thêm case cho Check-in nếu bạn muốn lọc riêng (cần thêm item vào ComboBox trước)
            case "Check-in":
                rf = RowFilter.regexFilter(".*Check-in.*", 1);
                break;
            default:
                rf = null;
        }

        sorter.setRowFilter(rf);
        recalculateSummary(); // Tính lại tổng tiền sau khi lọc
    }

    private void recalculateSummary() {
        long totalTopup = 0;
        long totalSpent = 0;
        int count = 0;

        for (int i = 0; i < transactionTable.getRowCount(); i++) {
            int modelRow = transactionTable.convertRowIndexToModel(i);
            String type = (String) tableModel.getValueAt(modelRow, 1);
            String amountStr = (String) tableModel.getValueAt(modelRow, 2);

            // Parse amount
            long amount = parseAmount(amountStr);

            if (type.contains("Nạp")) {
                totalTopup += amount;
            } else {
                totalSpent += amount;
            }
            count++;
        }

        lblTotalTopup.setText(String.format("%,d VNĐ", totalTopup));
        lblTotalSpent.setText(String.format("%,d VNĐ", totalSpent));
        lblTransactionCount.setText(String.valueOf(count));
    }

    private long parseAmount(String amountStr) {
        try {
            // Remove +, -, VNĐ, spaces, commas
            String cleaned = amountStr.replaceAll("[+\\-VNĐ\\s,]", "");
            return Long.parseLong(cleaned);
        } catch (Exception e) {
            return 0;
        }
    }

    // Export to CSV
    private void exportToCSV() {
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "Không có dữ liệu để xuất!",
                    "Thông báo",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("lich_su_giao_dich_"
                + new SimpleDateFormat("yyyyMMdd").format(new java.util.Date()) + ".csv"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (java.io.PrintWriter pw = new java.io.PrintWriter(
                    new java.io.OutputStreamWriter(
                            new java.io.FileOutputStream(chooser.getSelectedFile()), "UTF-8"))) {

                // BOM for UTF-8 (để Excel đọc được tiếng Việt)
                pw.print('\ufeff');

                // Header
                pw.println("STT,Loại,Số tiền,Chi tiết,Thời gian");

                // Data
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < tableModel.getColumnCount(); j++) {
                        if (j > 0) {
                            sb.append(",");
                        }
                        Object val = tableModel.getValueAt(i, j);
                        String cell = val != null ? val.toString() : "";
                        // Escape commas and quotes
                        if (cell.contains(",") || cell.contains("\"")) {
                            cell = "\"" + cell.replace("\"", "\"\"") + "\"";
                        }
                        sb.append(cell);
                    }
                    pw.println(sb.toString());
                }

                JOptionPane.showMessageDialog(this,
                        " Xuất file thành công!\n" + chooser.getSelectedFile().getName(),
                        "Thành công",
                        JOptionPane.INFORMATION_MESSAGE
                );

            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this,
                        " Lỗi xuất file: " + ex.getMessage(),
                        "Lỗi",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }
}
