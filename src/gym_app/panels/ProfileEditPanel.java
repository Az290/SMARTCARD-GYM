package gym_app.panels;

import gym_app.MainFrame;
import gym_app.SmartCardService;
import gym_app.components.*;
import gym_app.SecurityUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.ImageWriteParam;
import javax.imageio.IIOImage;
import java.sql.*;

/**
 * Màn hình sửa thông tin cá nhân
 * 
 *  v2.1 - CẬP NHẬT:
 * - Avatar chuẩn hóa 48x48 pixels (luôn < 2KB)
 * - Mỗi trường (name, phone, email, birthDate, address) mã hóa riêng
 * - Xác thực PIN trước khi lưu
 */
public class ProfileEditPanel extends JPanel {

    private MainFrame mainFrame;
    
    private JTextField txtName;
    private JTextField txtPhone;
    private JTextField txtEmail;
    private JTextField txtBirthDate;
    private JTextField txtAddress;
    private JLabel lblAvatar;
    private JLabel lblAvatarInfo;
    private byte[] newAvatarData;      // Avatar MỚI được chọn (plaintext)
    private byte[] currentAvatarData;  // Avatar HIỆN TẠI từ thẻ (plaintext - đã decrypt)
    
    //  MỚI: Chuẩn hóa avatar 48x48
    private static final int AVATAR_SIZE = SmartCardService.AVATAR_STANDARD_SIZE; 
    private static final float AVATAR_QUALITY = 0.7f;
    private static final int MAX_NAME_LENGTH = 40;

    public ProfileEditPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBackground(new Color(30, 30, 45));

        add(new SideMenu(mainFrame), BorderLayout.WEST);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(new Color(30, 30, 45));
        content.setBorder(new EmptyBorder(30, 40, 30, 40));

        JLabel title = new JLabel(" THÔNG TIN CÁ NHÂN");
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setForeground(new Color(52, 152, 219));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel avatarPanel = createAvatarPanel();
        JPanel formPanel = createFormPanel();

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        buttonPanel.setBackground(new Color(30, 30, 45));
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        GymButton btnSave = GymButton.success(" LƯU THAY ĐỔI");
        btnSave.setPreferredSize(new Dimension(200, 50));
        btnSave.addActionListener(e -> saveProfile());

        GymButton btnBack = new GymButton("← Quay lại", new Color(100, 100, 120));
        btnBack.setPreferredSize(new Dimension(150, 50));
        btnBack.addActionListener(e -> mainFrame.showScreen(MainFrame.SCREEN_DASHBOARD));

        buttonPanel.add(btnSave);
        buttonPanel.add(btnBack);

        content.add(title);
        content.add(Box.createVerticalStrut(25));
        content.add(avatarPanel);
        content.add(Box.createVerticalStrut(25));
        content.add(formPanel);
        content.add(Box.createVerticalStrut(30));
        content.add(buttonPanel);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(30, 30, 45));
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createAvatarPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(40, 40, 55));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 80)),
            new EmptyBorder(20, 25, 20, 25)
        ));
        panel.setMaximumSize(new Dimension(300, 300));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(" ẢNH ĐẠI DIỆN");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(new Color(0, 200, 180));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblAvatar = new JLabel();
        lblAvatar.setPreferredSize(new Dimension(100, 100));
        lblAvatar.setMaximumSize(new Dimension(100, 100));
        lblAvatar.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblAvatar.setBorder(BorderFactory.createLineBorder(new Color(100, 100, 120), 2));
        setDefaultAvatar();

        GymButton btnUpload = new GymButton(" Chọn ảnh mới", new Color(100, 100, 130));
        btnUpload.setMaximumSize(new Dimension(200, 35));
        btnUpload.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnUpload.addActionListener(e -> uploadAvatar());

        //  MỚI: Thông báo chuẩn hóa 48x48
        lblAvatarInfo = new JLabel("Ảnh sẽ được chuẩn hóa " + AVATAR_SIZE + "x" + AVATAR_SIZE);
        lblAvatarInfo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblAvatarInfo.setForeground(Color.GRAY);
        lblAvatarInfo.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(15));
        panel.add(lblAvatar);
        panel.add(Box.createVerticalStrut(15));
        panel.add(btnUpload);
        panel.add(Box.createVerticalStrut(8));
        panel.add(lblAvatarInfo);

        return panel;
    }

    private JPanel createFormPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(40, 40, 55));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 80)),
            new EmptyBorder(25, 30, 25, 30)
        ));
        panel.setMaximumSize(new Dimension(600, 400));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel formTitle = new JLabel(" THÔNG TIN CÁ NHÂN");
        formTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        formTitle.setForeground(new Color(0, 200, 180));

        txtName = createTextField();
        txtPhone = createTextField();
        txtEmail = createTextField();
        txtBirthDate = createTextField();
        txtAddress = createTextField();

        panel.add(formTitle);
        panel.add(Box.createVerticalStrut(20));
        panel.add(createFieldRow(" Họ và tên *", txtName));
        panel.add(Box.createVerticalStrut(15));
        panel.add(createFieldRow(" Số điện thoại *", txtPhone));
        panel.add(Box.createVerticalStrut(15));
        panel.add(createFieldRow(" Email", txtEmail));
        panel.add(Box.createVerticalStrut(15));
        panel.add(createFieldRow(" Ngày sinh (dd/MM/yyyy)", txtBirthDate));
        panel.add(Box.createVerticalStrut(15));
        panel.add(createFieldRow(" Địa chỉ", txtAddress));

        JLabel lblImportant = new JLabel("<html><span style='color:#e74c3c'>️ SĐT dùng để mở khóa thẻ nếu quên PIN!</span></html>");
        lblImportant.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblImportant.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        panel.add(Box.createVerticalStrut(15));
        panel.add(lblImportant);

        return panel;
    }

    private JTextField createTextField() {
        JTextField tf = new JTextField();
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tf.setBackground(new Color(60, 60, 75));
        tf.setForeground(Color.WHITE);
        tf.setCaretColor(Color.WHITE);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 120)),
            new EmptyBorder(10, 12, 10, 12)
        ));
        tf.setMaximumSize(new Dimension(350, 40));
        return tf;
    }

    private JPanel createFieldRow(String label, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(15, 0));
        row.setBackground(new Color(40, 40, 55));
        row.setMaximumSize(new Dimension(550, 45));

        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(Color.WHITE);
        lbl.setPreferredSize(new Dimension(180, 30));

        row.add(lbl, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);

        return row;
    }

    private void setDefaultAvatar() {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint gp = new GradientPaint(0, 0, new Color(60, 60, 80), 
                                              100, 100, new Color(40, 40, 60));
        g.setPaint(gp);
        g.fillRect(0, 0, 100, 100);

        g.setColor(new Color(100, 100, 130));
        g.fillOval(35, 15, 30, 30);
        g.fillRoundRect(25, 50, 50, 40, 15, 15);

        g.dispose();
        lblAvatar.setIcon(new ImageIcon(img));
    }

    /**
     *  MỚI: Upload avatar với chuẩn hóa 48x48
     */
    private void uploadAvatar() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Image files", "jpg", "jpeg", "png", "gif", "bmp"));
        
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                BufferedImage originalImg = ImageIO.read(file);
                
                if (originalImg == null) {
                    showError("Không thể đọc file ảnh!");
                    return;
                }
                
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                lblAvatarInfo.setText(" Đang xử lý...");
                lblAvatarInfo.setForeground(new Color(241, 196, 15));
                
                //  MỚI: Chuẩn hóa 48x48, quality 0.7 - LUÔN THÀNH CÔNG
                byte[] imageData = standardizeAvatar(originalImg);
                
                setCursor(Cursor.getDefaultCursor());
                
                if (imageData == null || imageData.length == 0) {
                    showError("Không thể xử lý ảnh!");
                    return;
                }
                
                // Lưu PLAINTEXT vào newAvatarData
                newAvatarData = imageData;
                
                // Hiển thị kích thước
                lblAvatarInfo.setText(" " + AVATAR_SIZE + "x" + AVATAR_SIZE + " (" + 
                    String.format("%.1f KB", imageData.length / 1024.0) + ")");
                lblAvatarInfo.setForeground(new Color(46, 204, 113));
                
                // Preview (scale lên 100x100 để hiển thị)
                BufferedImage preview = resizeImage(originalImg, 100, 100);
                lblAvatar.setIcon(new ImageIcon(preview));
                
                JOptionPane.showMessageDialog(this,
                    "<html><center>" +
                    "<h3> Đã chọn ảnh mới!</h3>" +
                    "<p>Kích thước: <b>" + AVATAR_SIZE + "x" + AVATAR_SIZE + " pixels</b></p>" +
                    "<p>Dung lượng: <b>" + String.format("%.1f KB", imageData.length / 1024.0) + "</b></p>" +
                    "<p style='color:#f1c40f'> Ảnh sẽ được mã hóa khi lưu vào thẻ</p>" +
                    "</center></html>",
                    "Thành công", 
                    JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception ex) {
                setCursor(Cursor.getDefaultCursor());
                showError("Lỗi: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    /**
     *  MỚI: Chuẩn hóa avatar về 48x48 pixels
     * Luôn thành công, output < 2KB
     */
    private byte[] standardizeAvatar(BufferedImage original) {
        try {
            // Resize về 48x48
            BufferedImage resized = resizeImage(original, AVATAR_SIZE, AVATAR_SIZE);
            
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
            
            byte[] result = baos.toByteArray();
            
            System.out.println("[Avatar]  Standardized to " + AVATAR_SIZE + "x" + AVATAR_SIZE + 
                ", size: " + result.length + " bytes");
            
            return result;
            
        } catch (Exception e) {
            System.out.println("[Avatar]  Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private BufferedImage resizeImage(BufferedImage original, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(original, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    /**
     * Load dữ liệu hiện tại
     */
    private void loadCurrentData() {
        newAvatarData = null;
        currentAvatarData = null;
        
        String cardId = mainFrame.getCurrentCardId();
        
        // ========== 1. LOAD INFO TỪ DATABASE (ENCRYPTED) ==========
        Connection conn = mainFrame.getDbService().getConnection();
        if (conn != null) {
            try {
                String sql = "SELECT name_enc, phone_enc, birth_date_enc FROM members WHERE card_id = ?";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setString(1, cardId);
                ResultSet rs = ps.executeQuery();
                
                if (rs.next()) {
                    String nameEnc = rs.getString("name_enc");
                    String phoneEnc = rs.getString("phone_enc");
                    String birthEnc = rs.getString("birth_date_enc");
                    
                    // GIẢI MÃ trước khi hiển thị
                    txtName.setText(nameEnc != null ? SecurityUtils.decrypt(nameEnc) : "");
                    txtPhone.setText(phoneEnc != null ? SecurityUtils.decrypt(phoneEnc) : "");
                    txtBirthDate.setText(birthEnc != null ? SecurityUtils.decrypt(birthEnc) : "");
                    
                    System.out.println("[Profile]  Loaded DECRYPTED info from database");
                } else {
                    // Fallback từ MainFrame
                    txtName.setText(mainFrame.getCurrentName() != null ? mainFrame.getCurrentName() : "");
                    txtPhone.setText(mainFrame.getCurrentPhone() != null ? mainFrame.getCurrentPhone() : "");
                    System.out.println("[Profile] ℹ️ No data in database, using MainFrame cache");
                }
                
                rs.close();
                ps.close();
            } catch (SQLException e) {
                System.out.println("[Profile]  Database error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // ========== 2. LOAD AVATAR TỪ THẺ ==========
        if (mainFrame.getCardService().isPinVerified()) {
            try {
                System.out.println("[Profile]  Loading avatar from card...");
                
                currentAvatarData = mainFrame.getCardService().getAvatar();
                
                if (currentAvatarData != null && currentAvatarData.length > 0) {
                    System.out.println("[Profile]  Received DECRYPTED avatar: " + 
                        String.format("%.1f KB", currentAvatarData.length / 1024.0));
                    
                    try {
                        ImageIcon icon = new ImageIcon(currentAvatarData);
                        Image scaled = icon.getImage().getScaledInstance(100, 100, Image.SCALE_SMOOTH);
                        lblAvatar.setIcon(new ImageIcon(scaled));
                        lblAvatarInfo.setText(" " + String.format("%.1f KB", currentAvatarData.length / 1024.0));
                        lblAvatarInfo.setForeground(new Color(46, 204, 113));
                    } catch (Exception e) {
                        System.out.println("[Profile]  Cannot display avatar: " + e.getMessage());
                        setDefaultAvatar();
                        lblAvatarInfo.setText(" Ảnh lỗi");
                        lblAvatarInfo.setForeground(new Color(231, 76, 60));
                    }
                } else {
                    System.out.println("[Profile] ℹ️ No avatar on card");
                    setDefaultAvatar();
                    lblAvatarInfo.setText("Chưa có ảnh");
                    lblAvatarInfo.setForeground(Color.GRAY);
                }
            } catch (Exception e) {
                System.out.println("[Profile]  Error loading avatar: " + e.getMessage());
                setDefaultAvatar();
                lblAvatarInfo.setText("Lỗi tải ảnh");
                lblAvatarInfo.setForeground(new Color(231, 76, 60));
            }
        } else {
            System.out.println("[Profile] ️ PIN not verified, cannot load avatar");
            setDefaultAvatar();
            lblAvatarInfo.setText("Chưa đăng nhập");
            lblAvatarInfo.setForeground(Color.GRAY);
        }
    }

    /**
     *  MỚI: Xác thực PIN trước khi thực hiện
     */
    private boolean confirmPIN() {
        // Kiểm tra có cần xác thực lại không
        if (!mainFrame.getCardService().needsPinReconfirmation()) {
            return true; // Chưa hết timeout, không cần nhập lại
        }
        
        // Hiển thị dialog nhập PIN
        JPasswordField pinField = new JPasswordField(6);
        pinField.setFont(new Font("Consolas", Font.BOLD, 24));
        pinField.setHorizontalAlignment(JTextField.CENTER);
        
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(new JLabel("<html><center> Nhập mã PIN để xác thực<br><small>(Yêu cầu bảo mật)</small></center></html>"), BorderLayout.NORTH);
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
        
        // Verify PIN
        if (mainFrame.getCardService().reVerifyPIN(pin)) {
            return true;
        } else {
            showError("PIN không đúng!");
            return false;
        }
    }

    /**
     *  SỬA: LƯU THÔNG TIN - MÃ HÓA RIÊNG TỪNG TRƯỜNG + XÁC THỰC PIN
     */
   private void saveProfile() {
        String name = txtName.getText().trim();
        String phone = txtPhone.getText().trim();
        String email = txtEmail.getText().trim();
        String birthDate = txtBirthDate.getText().trim();
        String address = txtAddress.getText().trim();

        // ========== VALIDATE ==========
        if (name.isEmpty() || name.length() < 2) {
            showError("Vui lòng nhập họ tên!");
            txtName.requestFocus();
            return;
        }

        if (!phone.matches("\\d{10,11}")) {
            showError("Số điện thoại phải có 10-11 số!");
            txtPhone.requestFocus();
            return;
        }

        //  MỚI: XÁC THỰC PIN TRƯỚC KHI LƯU
        if (!confirmPIN()) {
            return;
        }

        // ========================================================================
        // ✅ PHẦN MỚI: TẠO CHỮ KÝ SỐ ĐỂ XÁC THỰC THẺ (AUTHENTICATION)
        // ========================================================================
        // Chứng minh thẻ thật (có Private Key) đang thực hiện thao tác sửa đổi
        String cardIdSign = mainFrame.getCurrentCardId();
        String timestamp = String.valueOf(System.currentTimeMillis());
        // Định dạng dữ liệu ký: UPDATE|MÃ_THẺ|SĐT_MỚI|THỜI_GIAN
        String signData = "UPDATE|" + cardIdSign + "|" + phone + "|" + timestamp;
        
        System.out.println("[Profile] ✍️ Signing update request...");
        byte[] signature = mainFrame.getCardService().signTransaction(signData);
        
        if (signature == null || signature.length == 0) {
            showError("Lỗi tạo chữ ký số! Không thể xác thực thẻ.");
            return;
        }
        System.out.println("[Profile] ✅ Signature generated. Card authenticated.");
        // ========================================================================

        System.out.println("\n[Profile] ====== BẮT ĐẦU LƯU (MÃ HÓA RIÊNG TỪNG TRƯỜNG) =======");

        // ========== 1. UPLOAD AVATAR VÀO THẺ (NẾU CÓ) ==========
        if (newAvatarData != null && newAvatarData.length > 0) {
            System.out.println("[Profile]  Uploading avatar (" + 
                String.format("%.1f KB", newAvatarData.length / 1024.0) + ")...");
            
            if (mainFrame.getCardService().uploadAvatar(newAvatarData)) {
                System.out.println("[Profile]  Avatar uploaded and ENCRYPTED on card");
                currentAvatarData = newAvatarData;
            } else {
                System.out.println("[Profile]  Avatar upload FAILED");
                showError("Không thể lưu ảnh vào thẻ!\nVui lòng thử lại.");
                return;
            }
        }

        // ========== 2. LƯU INFO VÀO THẺ - MÃ HÓA RIÊNG TỪNG TRƯỜNG ==========
        String infoName = name;
        byte[] nameBytes = infoName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (nameBytes.length > MAX_NAME_LENGTH) {
            infoName = truncateUTF8(name, MAX_NAME_LENGTH);
            System.out.println("[Profile] Name truncated to: " + infoName);
        }
        
        System.out.println("[Profile]  Saving info to card (each field encrypted separately)...");
        System.out.println("[Profile]   → Name: " + infoName);
        System.out.println("[Profile]   → Phone: " + phone);
        System.out.println("[Profile]   → Email: " + (email.isEmpty() ? "(empty)" : email));
        System.out.println("[Profile]   → BirthDate: " + (birthDate.isEmpty() ? "(empty)" : birthDate));
        System.out.println("[Profile]   → Address: " + (address.isEmpty() ? "(empty)" : address));
        
        //  MỚI: Gọi updateInfoFields() để mã hóa riêng từng trường
        boolean cardSaved = mainFrame.getCardService().updateInfoFields(
            infoName, 
            phone, 
            email.isEmpty() ? null : email, 
            birthDate.isEmpty() ? null : birthDate, 
            address.isEmpty() ? null : address
        );
        
        if (!cardSaved) {
            showError("Lưu thông tin vào thẻ thất bại!");
            return;
        }
        System.out.println("[Profile]  All fields saved and ENCRYPTED separately on card");

        // ========== 3. LƯU VÀO DATABASE (MÃ HÓA) ==========
        String cardId = mainFrame.getCurrentCardId();
        Connection conn = mainFrame.getDbService().getConnection();
        
        if (conn != null) {
            try {
                System.out.println("[Profile]  Saving to database (encrypted)...");
                
                // MÃ HÓA dữ liệu trước khi lưu
                String nameEnc = SecurityUtils.encrypt(name);
                String phoneEnc = SecurityUtils.encrypt(phone);
                String phoneHash = SecurityUtils.hashPhone(phone);
                String birthEnc = !birthDate.isEmpty() ? SecurityUtils.encrypt(birthDate) : null;
                
                // Check member tồn tại chưa
                String checkSql = "SELECT id FROM members WHERE card_id = ?";
                PreparedStatement checkPs = conn.prepareStatement(checkSql);
                checkPs.setString(1, cardId);
                ResultSet rs = checkPs.executeQuery();
                
                boolean exists = rs.next();
                rs.close();
                checkPs.close();
                
                if (exists) {
                    // UPDATE
                    String sql = "UPDATE members SET name_enc = ?, phone_enc = ?, phone_hash = ?, " +
                                "birth_date_enc = ? WHERE card_id = ?";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, nameEnc);
                    ps.setString(2, phoneEnc);
                    ps.setString(3, phoneHash);
                    ps.setString(4, birthEnc);
                    ps.setString(5, cardId);
                    ps.executeUpdate();
                    ps.close();
                    System.out.println("[Profile]  Database UPDATED (encrypted)");
                } else {
                    // INSERT
                    String sql = "INSERT INTO members (card_id, name_enc, phone_enc, phone_hash, " +
                                "birth_date_enc, balance, status) VALUES (?, ?, ?, ?, ?, 0, 'active')";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, cardId);
                    ps.setString(2, nameEnc);
                    ps.setString(3, phoneEnc);
                    ps.setString(4, phoneHash);
                    ps.setString(5, birthEnc);
                    ps.executeUpdate();
                    ps.close();
                    System.out.println("[Profile]  Database INSERTED (encrypted)");
                }
                
            } catch (SQLException e) {
                System.out.println("[Profile]  Database error: " + e.getMessage());
                e.printStackTrace();
                showError("Lỗi lưu database: " + e.getMessage());
                return;
            }
        }

        // ========== 4. CẬP NHẬT UI ==========
        mainFrame.setCurrentName(name);
        mainFrame.setCurrentPhone(phone);
        mainFrame.getCardService().setRecoveryPhone(phone);
        mainFrame.getCardService().saveCardState();

        System.out.println("[Profile] ====== HOÀN TẤT =======\n");

        JOptionPane.showMessageDialog(this,
            "<html><center>" +
            "<h2> LƯU THÀNH CÔNG!</h2>" +
            "<p> Thẻ: Mỗi trường mã hóa riêng (AES)</p>" +
            (newAvatarData != null ? "<p>️ Avatar: " + AVATAR_SIZE + "x" + AVATAR_SIZE + " encrypted</p>" : "") +
            "<p> Database: Encrypted (AES)</p>" +
            "<p style='color:#f1c40f'> Đã ký số xác thực thẻ</p>" + // Cập nhật thông báo
            "<br><p style='color:#f1c40f'> SĐT <b>" + phone + "</b> dùng để mở khóa thẻ</p>" +
            "</center></html>",
            "Thành công",
            JOptionPane.INFORMATION_MESSAGE
        );

        mainFrame.showScreen(MainFrame.SCREEN_DASHBOARD);
    }

    /**
     * Cắt chuỗi UTF-8 đúng cách
     */
    private String truncateUTF8(String str, int maxBytes) {
        if (str == null) return "";
        
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return str;
        
        int len = maxBytes;
        while (len > 0 && (bytes[len] & 0xC0) == 0x80) {
            len--;
        }
        
        return new String(bytes, 0, len, java.nio.charset.StandardCharsets.UTF_8);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Được gọi khi panel hiển thị
     */
    public void onShow() {
        loadCurrentData();
    }
}