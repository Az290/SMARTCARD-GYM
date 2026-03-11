package gym_app.panels;

import gym_app.MainFrame;
import gym_app.SmartCardService;
import gym_app.components.*;
import gym_app.DatabaseService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.ImageWriteParam;
import javax.imageio.IIOImage;

/**
 * Trang chủ sau khi đăng nhập
 *
 * v2.1: - Avatar chuẩn hóa 48x48 pixels - Xác thực PIN khi upload avatar
 */
public class DashboardPanel extends JPanel {

    private MainFrame mainFrame;

    // Components
    private UserCard userCard;
    private JPanel contentPanel;
    private JLabel lblWelcome;
    private JPanel packageSummary;
    private JPanel quickActions;

    //  MỚI: Chuẩn hóa avatar
    private static final int AVATAR_SIZE = SmartCardService.AVATAR_STANDARD_SIZE; // 48
    private static final float AVATAR_QUALITY = 0.7f;

    public DashboardPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBackground(new Color(30, 30, 45));

        // === LEFT: Side Menu ===
        SideMenu sideMenu = new SideMenu(mainFrame);
        add(sideMenu, BorderLayout.WEST);

        // === CENTER: Main Content ===
        JPanel centerPanel = new JPanel(new BorderLayout(20, 20));
        centerPanel.setBackground(new Color(30, 30, 45));
        centerPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Header
        JPanel header = createHeader();
        centerPanel.add(header, BorderLayout.NORTH);

        // Content với scroll
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(30, 30, 45));

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(30, 30, 45));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // === RIGHT: User Info ===
        JPanel rightPanel = createRightPanel();
        add(rightPanel, BorderLayout.EAST);

        // Load content
        loadDashboardContent();
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(30, 30, 45));
        header.setPreferredSize(new Dimension(0, 60));

        lblWelcome = new JLabel(" Xin chào!");
        lblWelcome.setFont(new Font("Segoe UI", Font.BOLD, 28));
        lblWelcome.setForeground(Color.WHITE);

        JLabel lblDate = new JLabel(java.time.LocalDate.now().toString());
        lblDate.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblDate.setForeground(Color.GRAY);

        header.add(lblWelcome, BorderLayout.WEST);
        header.add(lblDate, BorderLayout.EAST);

        return header;
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(35, 35, 50));
        panel.setPreferredSize(new Dimension(280, 0));
        panel.setBorder(new EmptyBorder(20, 15, 20, 15));

        // User Card
        userCard = new UserCard();
        panel.add(userCard);

        panel.add(Box.createVerticalStrut(20));

        // Quick buttons
        GymButton btnUploadAvatar = new GymButton(" Đổi ảnh đại diện", new Color(100, 100, 130));
        btnUploadAvatar.setMaximumSize(new Dimension(250, 40));
        btnUploadAvatar.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnUploadAvatar.addActionListener(e -> uploadAvatar());

        GymButton btnEditProfile = GymButton.info("️ Sửa thông tin");
        btnEditProfile.setMaximumSize(new Dimension(250, 40));
        btnEditProfile.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnEditProfile.addActionListener(e -> mainFrame.showScreen(MainFrame.SCREEN_PROFILE));

        panel.add(btnUploadAvatar);
        panel.add(Box.createVerticalStrut(10));
        panel.add(btnEditProfile);

        return panel;
    }

    private void loadDashboardContent() {
        contentPanel.removeAll();

        // Quick Actions
        contentPanel.add(createQuickActionsPanel());
        contentPanel.add(Box.createVerticalStrut(20));

        // Active Packages
        contentPanel.add(createActivePackagesPanel());
        contentPanel.add(Box.createVerticalStrut(20));

        // Recent Transactions
        contentPanel.add(createRecentTransactionsPanel());

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private JPanel createQuickActionsPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 4, 15, 0));
        panel.setBackground(new Color(30, 30, 45));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(createQuickActionCard("", "Nạp tiền", new Color(46, 204, 113),
                () -> mainFrame.showScreen(MainFrame.SCREEN_TOPUP)));

        panel.add(createQuickActionCard("", "Mua gói tập", new Color(52, 152, 219),
                () -> mainFrame.showScreen(MainFrame.SCREEN_BUY_PACKAGE)));

        panel.add(createQuickActionCard("", "Check-in", new Color(155, 89, 182),
                () -> mainFrame.showScreen(MainFrame.SCREEN_CHECKIN)));

        panel.add(createQuickActionCard("", "Lịch sử", new Color(241, 196, 15),
                () -> mainFrame.showScreen(MainFrame.SCREEN_HISTORY)));

        return panel;
    }

    private JPanel createQuickActionCard(String icon, String text, Color color, Runnable action) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(40, 40, 55));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 2),
                new EmptyBorder(20, 15, 20, 15)
        ));
        card.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JLabel lblIcon = new JLabel(icon);
        lblIcon.setFont(new Font("Segoe UI", Font.PLAIN, 32));
        lblIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblText = new JLabel(text);
        lblText.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblText.setForeground(Color.WHITE);
        lblText.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(lblIcon);
        card.add(Box.createVerticalStrut(10));
        card.add(lblText);

        card.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                action.run();
            }

            public void mouseEntered(java.awt.event.MouseEvent e) {
                card.setBackground(new Color(50, 50, 70));
            }

            public void mouseExited(java.awt.event.MouseEvent e) {
                card.setBackground(new Color(40, 40, 55));
            }
        });

        return card;
    }

    private JPanel createActivePackagesPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(40, 40, 55));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 80)),
                new EmptyBorder(15, 20, 15, 20)
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel(" GÓI TẬP ĐANG SỬ DỤNG");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(new Color(0, 200, 180));

        panel.add(title);
        panel.add(Box.createVerticalStrut(15));

        String cardId = mainFrame.getCurrentCardId();
        if (cardId != null) {
            List<DatabaseService.MemberPackageInfo> packages
                    = mainFrame.getDbService().getActiveMemberPackages(cardId);

            if (packages.isEmpty()) {
                JLabel noPackage = new JLabel("Bạn chưa có gói tập nào. Hãy mua gói ngay!");
                noPackage.setForeground(Color.GRAY);
                panel.add(noPackage);
            } else {
                for (DatabaseService.MemberPackageInfo pkg : packages) {
                    panel.add(createPackageRow(pkg));
                    panel.add(Box.createVerticalStrut(8));
                }
            }
        } else {
            JLabel noData = new JLabel("Chưa có dữ liệu");
            noData.setForeground(Color.GRAY);
            panel.add(noData);
        }

        return panel;
    }

    private JPanel createPackageRow(DatabaseService.MemberPackageInfo pkg) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(new Color(50, 50, 65));
        row.setBorder(new EmptyBorder(10, 15, 10, 15));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        JLabel name = new JLabel(" " + pkg.packageName);
        name.setFont(new Font("Segoe UI", Font.BOLD, 14));
        name.setForeground(Color.WHITE);

        String statusText;
        Color statusColor;
        if (pkg.expireDate != null) {
            long daysLeft = (pkg.expireDate.getTime() - System.currentTimeMillis()) / (1000 * 60 * 60 * 24);
            statusText = "Còn " + daysLeft + " ngày";
            statusColor = daysLeft > 7 ? new Color(46, 204, 113) : new Color(241, 196, 15);
        } else if (pkg.remainingSessions != null) {
            statusText = "Còn " + pkg.remainingSessions + " buổi";
            statusColor = pkg.remainingSessions > 3 ? new Color(46, 204, 113) : new Color(241, 196, 15);
        } else {
            statusText = "Không giới hạn";
            statusColor = new Color(46, 204, 113);
        }

        JLabel status = new JLabel(statusText);
        status.setFont(new Font("Segoe UI", Font.BOLD, 12));
        status.setForeground(statusColor);

        row.add(name, BorderLayout.WEST);
        row.add(status, BorderLayout.EAST);

        return row;
    }

    private JPanel createRecentTransactionsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(40, 40, 55));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 80)),
                new EmptyBorder(15, 20, 15, 20)
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel(" GIAO DỊCH GẦN ĐÂY");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(new Color(0, 200, 180));

        panel.add(title);
        panel.add(Box.createVerticalStrut(15));

        String cardId = mainFrame.getCurrentCardId();
        if (cardId != null) {
            List<DatabaseService.TransactionInfo> transactions
                    = mainFrame.getDbService().getTransactionHistory(cardId, 5);

            if (transactions.isEmpty()) {
                JLabel noTrans = new JLabel("Chưa có giao dịch nào");
                noTrans.setForeground(Color.GRAY);
                panel.add(noTrans);
            } else {
                for (DatabaseService.TransactionInfo tx : transactions) {
                    panel.add(createTransactionRow(tx));
                    panel.add(Box.createVerticalStrut(5));
                }
            }
        }

        JButton btnViewAll = new JButton("Xem tất cả →");
        btnViewAll.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnViewAll.setForeground(new Color(52, 152, 219));
        btnViewAll.setContentAreaFilled(false);
        btnViewAll.setBorderPainted(false);
        btnViewAll.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnViewAll.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnViewAll.addActionListener(e -> mainFrame.showScreen(MainFrame.SCREEN_HISTORY));

        panel.add(Box.createVerticalStrut(10));
        panel.add(btnViewAll);

        return panel;
    }

    private JPanel createTransactionRow(DatabaseService.TransactionInfo tx) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(new Color(50, 50, 65));
        row.setBorder(new EmptyBorder(8, 12, 8, 12));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45)); // Tăng chiều cao một chút

        String icon;
        String desc;
        String amountText;
        Color amountColor;

        // Xử lý hiển thị dựa trên loại giao dịch
        if ("CHECKIN".equalsIgnoreCase(tx.type)) {
            icon = "🏃";
            desc = "Check-in vào phòng tập";
            amountText = "---"; // Hoặc "Miễn phí"
            amountColor = new Color(52, 152, 219); // Màu xanh dương nhạt
        } else if ("TOPUP".equalsIgnoreCase(tx.type)) {
            icon = "💰";
            desc = "Nạp tiền";
            amountText = "+" + String.format("%,d", tx.amount) + " VNĐ";
            amountColor = new Color(46, 204, 113); // Màu xanh lá
        } else { // BUY_PACKAGE
            icon = "🛒";
            // Hiển thị tên gói hoặc tên HLV nếu có
            if (tx.packageName != null) {
                desc = "Mua " + tx.packageName;
            } else if (tx.trainerName != null) {
                desc = "Thuê HLV " + tx.trainerName;
            } else {
                desc = "Mua gói tập";
            }
            amountText = "-" + String.format("%,d", tx.amount) + " VNĐ";
            amountColor = new Color(231, 76, 60); // Màu đỏ
        }

        JLabel left = new JLabel(icon + " " + desc);
        left.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        left.setForeground(Color.WHITE);

        JLabel right = new JLabel(amountText);
        right.setFont(new Font("Segoe UI", Font.BOLD, 13));
        right.setForeground(amountColor);

        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);

        return row;
    }

    /**
     * MỚI: Xác thực PIN trước khi thực hiện
     */
    private boolean confirmPIN() {
        if (!mainFrame.getCardService().needsPinReconfirmation()) {
            return true;
        }

        JPasswordField pinField = new JPasswordField(6);
        pinField.setFont(new Font("Consolas", Font.BOLD, 24));
        pinField.setHorizontalAlignment(JTextField.CENTER);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(new JLabel("<html><center> Nhập mã PIN để xác thực<br><small>(Thay đổi ảnh đại diện)</small></center></html>"), BorderLayout.NORTH);
        panel.add(pinField, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Xác thực PIN",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return false;
        }

        String pin = new String(pinField.getPassword());

        if (pin.length() != 6) {
            showError("PIN phải có 6 chữ số!");
            return false;
        }

        if (mainFrame.getCardService().reVerifyPIN(pin)) {
            return true;
        } else {
            showError("PIN không đúng!");
            return false;
        }
    }

    /**
     * SỬA: Upload avatar với chuẩn hóa 48x48 + xác thực PIN
     */
    private void uploadAvatar() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Image files", "jpg", "jpeg", "png", "gif"
        ));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.File file = chooser.getSelectedFile();
                BufferedImage originalImg = ImageIO.read(file);

                if (originalImg == null) {
                    showError("Không thể đọc file ảnh!");
                    return;
                }

                //  MỚI: XÁC THỰC PIN TRƯỚC KHI UPLOAD
                if (!confirmPIN()) {
                    return;
                }

                System.out.println("\n[Dashboard] ====== UPLOAD AVATAR =======");
                System.out.println("[Dashboard]  PIN verified, proceeding...");

                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                //  MỚI: Chuẩn hóa 48x48
                byte[] data = standardizeAvatar(originalImg);

                setCursor(Cursor.getDefaultCursor());

                if (data == null || data.length == 0) {
                    showError("Không thể xử lý ảnh!");
                    return;
                }

                System.out.println("[Dashboard] Standardized avatar: " + AVATAR_SIZE + "x" + AVATAR_SIZE
                        + ", " + String.format("%.1f KB", data.length / 1024.0));

                if (mainFrame.getCardService().uploadAvatar(data)) {
                    userCard.setAvatar(data);

                    System.out.println("[Dashboard]  Avatar uploaded successfully!");
                    System.out.println("[Dashboard] ====== HOÀN TẤT =======\n");

                    JOptionPane.showMessageDialog(this,
                            "<html><center>"
                            + "<h3> Cập nhật ảnh thành công!</h3>"
                            + "<p>Kích thước: <b>" + AVATAR_SIZE + "x" + AVATAR_SIZE + "</b></p>"
                            + "<p>Dung lượng: <b>" + String.format("%.1f KB", data.length / 1024.0) + "</b></p>"
                            + "<p style='color:#f1c40f'> Đã xác thực PIN</p>"
                            + "</center></html>",
                            "Thành công",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    showError("Upload avatar thất bại!");
                }

            } catch (Exception ex) {
                setCursor(Cursor.getDefaultCursor());
                ex.printStackTrace();
                showError("Lỗi tải ảnh: " + ex.getMessage());
            }
        }
    }

    /**
     * MỚI: Chuẩn hóa avatar về 48x48 pixels
     */
    private byte[] standardizeAvatar(BufferedImage original) {
        try {
            // Resize về 48x48
            BufferedImage resized = new BufferedImage(AVATAR_SIZE, AVATAR_SIZE, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(original, 0, 0, AVATAR_SIZE, AVATAR_SIZE, null);
            g.dispose();

            // Nén JPEG với quality 0.7
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();

            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(AVATAR_QUALITY);
            }

            writer.setOutput(ImageIO.createImageOutputStream(baos));
            writer.write(null, new IIOImage(resized, null, null), param);
            writer.dispose();

            return baos.toByteArray();

        } catch (Exception e) {
            System.out.println("[Dashboard]  Error standardizing avatar: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    // ==================== PUBLIC METHODS ====================
    public void setUserInfo(String cardId, String name, String phone) {
        lblWelcome.setText(" Xin chào, " + name + "!");
        userCard.setUserInfo(cardId, name, phone);

        if (mainFrame.getCardService().isPinVerified()) {
            userCard.setBalance(mainFrame.getCardService().getBalance());
            loadAvatarFromCard();
        } else {
            System.out.println("[Dashboard] ️ PIN not verified, skipping data load");
        }
    }

    public void refreshData() {
        if (!mainFrame.getCardService().isPinVerified()) {
            System.out.println("[Dashboard] ️ Cannot refresh - not logged in");
            return;
        }

        userCard.setBalance(mainFrame.getCardService().getBalance());
        loadAvatarFromCard();
        updateCheckinStatus();
        loadDashboardContent();
    }

    private void updateCheckinStatus() {
        String cardId = mainFrame.getCurrentCardId();
        if (cardId == null) {
            userCard.setStatus(" Chưa check-in");
            return;
        }

        java.sql.Connection conn = mainFrame.getDbService().getConnection();
        if (conn == null) {
            userCard.setStatus(" Chưa check-in");
            return;
        }

        try {
            String sql = "SELECT MAX(c.checkin_time) as last_time, "
                    + "SUM(CASE WHEN HOUR(c.checkin_time) >= 5 AND HOUR(c.checkin_time) < 14 THEN 1 ELSE 0 END) as morning, "
                    + "SUM(CASE WHEN HOUR(c.checkin_time) >= 14 THEN 1 ELSE 0 END) as afternoon "
                    + "FROM checkins c "
                    + "JOIN members m ON c.member_id = m.id "
                    + "WHERE m.card_id = ? AND DATE(c.checkin_time) = CURDATE()";

            java.sql.PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, cardId);
            java.sql.ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int morning = rs.getInt("morning");
                int afternoon = rs.getInt("afternoon");
                java.sql.Timestamp lastTime = rs.getTimestamp("last_time");

                if (morning + afternoon == 0) {
                    userCard.setStatus(" Chưa check-in");
                } else if (morning > 0 && afternoon > 0) {
                    userCard.setStatus(" Đã check-in 2 buổi");
                } else if (morning > 0) {
                    String timeStr = lastTime.toLocalDateTime().format(
                            java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                    );
                    userCard.setStatus(" Đã check-in sáng (" + timeStr + ")");
                } else {
                    String timeStr = lastTime.toLocalDateTime().format(
                            java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                    );
                    userCard.setStatus(" Đã check-in chiều (" + timeStr + ")");
                }
            } else {
                userCard.setStatus(" Chưa check-in");
            }

            rs.close();
            ps.close();
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            userCard.setStatus(" Chưa check-in");
        }
    }

    private void loadAvatarFromCard() {
        if (!mainFrame.getCardService().isPinVerified()) {
            System.out.println("[Dashboard] ️ Cannot load avatar - not logged in");
            return;
        }

        try {
            System.out.println("[Dashboard]  Loading avatar from card...");

            byte[] decryptedAvatar = mainFrame.getCardService().getAvatar();

            if (decryptedAvatar != null && decryptedAvatar.length > 0) {
                System.out.println("[Dashboard]  Received DECRYPTED avatar: "
                        + String.format("%.1f KB", decryptedAvatar.length / 1024.0));

                userCard.setAvatar(decryptedAvatar);
            } else {
                System.out.println("[Dashboard] ℹ️ No avatar on card");
                userCard.setAvatar(null);
            }
        } catch (Exception e) {
            System.out.println("[Dashboard]  Error loading avatar: " + e.getMessage());
            e.printStackTrace();
            userCard.setAvatar(null);
        }
    }
}
