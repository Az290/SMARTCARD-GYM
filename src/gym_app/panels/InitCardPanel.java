package gym_app.panels;

import gym_app.MainFrame;
import gym_app.components.GymButton;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Màn hình khởi tạo thẻ lần đầu
 * Bắt buộc nhập SĐT để dùng cho việc mở khóa thẻ khi quên PIN
 */
public class InitCardPanel extends JPanel {

    private MainFrame mainFrame;
    
    private JTextField txtPhone;
    private JLabel lblCardId;
    private JLabel lblError;

    public InitCardPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initUI();
    }

    private void initUI() {
        setLayout(new GridBagLayout());
        setBackground(new Color(30, 30, 45));

        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(new Color(40, 40, 55));
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 200, 180), 2),
                new EmptyBorder(40, 50, 40, 50)
        ));
        container.setPreferredSize(new Dimension(450, 480));

        // Icon
        JLabel icon = new JLabel("");
        icon.setFont(new Font("Segoe UI", Font.PLAIN, 50));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Title
        JLabel title = new JLabel("KÍCH HOẠT THẺ MỚI");
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(new Color(0, 200, 180));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Subtitle
        JLabel subtitle = new JLabel("<html><center>Chào mừng bạn đến với POWER GYM!</center></html>");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitle.setForeground(Color.GRAY);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Card ID display
        JPanel cardIdPanel = createCardIdPanel();

        // Phone input
        JLabel lblPhone = new JLabel(" Nhập số điện thoại:");
        lblPhone.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblPhone.setForeground(Color.WHITE);
        lblPhone.setAlignmentX(Component.CENTER_ALIGNMENT);

        txtPhone = new JTextField(15);
        txtPhone.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        txtPhone.setHorizontalAlignment(JTextField.CENTER);
        txtPhone.setBackground(new Color(60, 60, 75));
        txtPhone.setForeground(Color.WHITE);
        txtPhone.setCaretColor(Color.WHITE);
        txtPhone.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 120)),
                new EmptyBorder(8, 12, 8, 12)
        ));
        txtPhone.setMaximumSize(new Dimension(200, 38));
        txtPhone.setAlignmentX(Component.CENTER_ALIGNMENT);
        txtPhone.addActionListener(e -> doActivate());

        // Error label
        lblError = new JLabel(" ");
        lblError.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblError.setForeground(new Color(231, 76, 60));
        lblError.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Warning
        JLabel warning = new JLabel("<html><center>⚠️ SĐT này dùng để <b>mở khóa thẻ</b> khi quên PIN</center></html>");
        warning.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        warning.setForeground(new Color(241, 196, 15));
        warning.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Button
        GymButton btnActivate = GymButton.success("KÍCH HOẠT THẺ");
        btnActivate.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnActivate.setMaximumSize(new Dimension(250, 48));
        btnActivate.addActionListener(e -> doActivate());

        // Layout
        container.add(icon);
        container.add(Box.createVerticalStrut(10));
        container.add(title);
        container.add(Box.createVerticalStrut(5));
        container.add(subtitle);
        container.add(Box.createVerticalStrut(25));
        container.add(cardIdPanel);
        container.add(Box.createVerticalStrut(25));
        container.add(lblPhone);
        container.add(Box.createVerticalStrut(8));
        container.add(txtPhone);
        container.add(Box.createVerticalStrut(8));
        container.add(lblError);
        container.add(Box.createVerticalStrut(10));
        container.add(warning);
        container.add(Box.createVerticalStrut(20));
        container.add(btnActivate);

        add(container);
    }

    private JPanel createCardIdPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(50, 50, 70));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 200, 180), 2),
                new EmptyBorder(12, 20, 12, 20)
        ));
        panel.setMaximumSize(new Dimension(280, 70));
        panel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblTitle = new JLabel("️ Mã thẻ của bạn");
        lblTitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblTitle.setForeground(Color.GRAY);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblCardId = new JLabel("GYM000000");
        lblCardId.setFont(new Font("Consolas", Font.BOLD, 22));
        lblCardId.setForeground(new Color(0, 200, 180));
        lblCardId.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(lblTitle);
        panel.add(Box.createVerticalStrut(3));
        panel.add(lblCardId);

        return panel;
    }

    private void doActivate() {
        String phone = txtPhone.getText().trim();

        // Validate phone
        if (!phone.matches("\\d{10,11}")) {
            showError("Số điện thoại phải có 10-11 chữ số!");
            txtPhone.requestFocus();
            return;
        }

        // Lưu SĐT vào thẻ
        mainFrame.getCardService().setRecoveryPhone(phone);
        
        System.out.println("[InitCard] ✅ Đã lưu SĐT recovery: " + phone);

        // Thông báo thành công
        JOptionPane.showMessageDialog(this,
                "<html><center>" +
                "<h2>KÍCH HOẠT THÀNH CÔNG!</h2>" +
                "<p>Mã thẻ: <b>" + mainFrame.getCardService().getCardId() + "</b></p>" +
                "<p>SĐT: <b>" + phone + "</b></p>" +
                "<br>" +
                "<p>PIN mặc định: <b style='color:green; font-size:20px'>123456</b></p>" +
                "<p style='color:orange'>⚠️ Bạn sẽ phải đổi PIN khi đăng nhập!</p>" +
                "</center></html>",
                "Thành công",
                JOptionPane.INFORMATION_MESSAGE
        );

        // Reset form và chuyển sang Login
        resetForm();
        mainFrame.onCardInitialized(phone);
    }

    private void showError(String msg) {
        lblError.setText("❌ " + msg);
    }

    private void resetForm() {
        txtPhone.setText("");
        lblError.setText(" ");
    }

    /**
     * Được gọi khi panel hiển thị
     */
    public void onShow() {
        resetForm();
        
        // Hiển thị Card ID
        String cardId = mainFrame.getCardService().getCardId();
        lblCardId.setText(cardId != null ? cardId : "---");
        
        txtPhone.requestFocus();
    }
}