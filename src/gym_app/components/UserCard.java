package gym_app.components;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Card hiển thị thông tin user
 */
public class UserCard extends JPanel {

    private JLabel lblAvatar;
    private JLabel lblName;
    private JLabel lblCardId;
    private JLabel lblPhone;
    private JLabel lblBalance;
    private JLabel lblStatus;

    private byte[] avatarData;

    public UserCard() {
        initUI();
    }

    private void initUI() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(new Color(35, 35, 50));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        // Avatar
        lblAvatar = new JLabel();
        lblAvatar.setPreferredSize(new Dimension(150, 150));
        lblAvatar.setMaximumSize(new Dimension(150, 150));
        lblAvatar.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblAvatar.setBorder(BorderFactory.createLineBorder(new Color(0, 200, 180), 3));
        setDefaultAvatar();

        // Thông tin
        lblName = createLabel("Chưa đăng nhập", 18, Font.BOLD);
        lblCardId = createLabel("Mã thẻ: ", 12, Font.PLAIN);
        lblPhone = createLabel("SĐT: ", 12, Font.PLAIN);
        lblBalance = createLabel("0 VNĐ", 16, Font.BOLD);
        lblBalance.setForeground(new Color(46, 204, 113));
        lblStatus = createLabel("⏳ Chưa check-in", 12, Font.PLAIN);
        lblStatus.setForeground(new Color(241, 196, 15));

        add(lblAvatar);
        add(Box.createVerticalStrut(15));
        add(lblName);
        add(Box.createVerticalStrut(8));
        add(lblCardId);
        add(Box.createVerticalStrut(5));
        add(lblPhone);
        add(Box.createVerticalStrut(15));
        add(lblBalance);
        add(Box.createVerticalStrut(10));
        add(lblStatus);
    }

    private JLabel createLabel(String text, int size, int style) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", style, size));
        lbl.setForeground(Color.WHITE);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        return lbl;
    }

    private void setDefaultAvatar() {
        BufferedImage img = new BufferedImage(150, 150, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Gradient background
        GradientPaint gp = new GradientPaint(0, 0, new Color(60, 60, 80), 
                                              150, 150, new Color(40, 40, 60));
        g.setPaint(gp);
        g.fillRect(0, 0, 150, 150);

        // User icon
        g.setColor(new Color(100, 100, 130));
        g.fillOval(50, 25, 50, 50);
        g.fillRoundRect(35, 85, 80, 55, 20, 20);

        g.dispose();
        lblAvatar.setIcon(new ImageIcon(img));
    }

public void setAvatar(byte[] data) {
    if (data != null && data.length > 0) {
        try {
            this.avatarData = data;
            
            System.out.println("[UserCard] 🖼️ Setting avatar: " + 
                String.format("%.1f KB", data.length / 1024.0));
            
            // ✅ data ĐÃ LÀ PLAINTEXT (đã giải mã từ SmartCardService)
            ImageIcon icon = new ImageIcon(data);
            Image scaled = icon.getImage().getScaledInstance(150, 150, Image.SCALE_SMOOTH);
            lblAvatar.setIcon(new ImageIcon(scaled));
            
            System.out.println("[UserCard] ✅ Avatar displayed successfully");
        } catch (Exception e) {
            System.out.println("[UserCard] ❌ Error displaying avatar: " + e.getMessage());
            e.printStackTrace();
            setDefaultAvatar();
        }
    } else {
        System.out.println("[UserCard] ℹ️ No avatar data, using default");
        setDefaultAvatar();
    }
}


    public void setUserInfo(String cardId, String name, String phone) {
        lblName.setText(name != null ? name : "Chưa có tên");
        lblCardId.setText("🏷️ " + (cardId != null ? cardId : "---"));
        lblPhone.setText("📱 " + hidePhone(phone));
    }

   public void setBalance(long balance) {
    if (balance >= 0) {
        lblBalance.setText("💰 " + String.format("%,d VNĐ", balance));
    } else {
        lblBalance.setText("💰 --- VNĐ");
    }
}
    public void setStatus(String status) {
        lblStatus.setText(status);
    }

    private String hidePhone(String phone) {
        if (phone != null && phone.length() >= 10) {
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
        }
        return phone != null ? phone : "---";
    }
}