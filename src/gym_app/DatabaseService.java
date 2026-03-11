package gym_app;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseService {

    private Connection conn = null;

    public DatabaseService() {
        connectWithAutoCreate();
    }

    private void connectWithAutoCreate() {
        try {
            // Kết nối tới MySQL
            String rootUrl = "jdbc:mysql://localhost:3306/?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8";
            Connection tempConn = DriverManager.getConnection(rootUrl, "root", "");

            Statement stmt = tempConn.createStatement();
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS gym_smartcard CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            stmt.close();
            tempConn.close();

            // Kết nối vào gym_smartcard
            String dbUrl = "jdbc:mysql://localhost:3306/gym_smartcard?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8";

            conn = DriverManager.getConnection(dbUrl, "root", "");
            System.out.println("✅ Kết nối CSDL gym_smartcard thành công!");
        } catch (Exception e) {
            System.out.println("❌ Không kết nối được MySQL! Chạy ở chế độ offline.");
            e.printStackTrace();
        }
    }

    // ==================== MEMBERS ====================
    /**
     * Đăng ký thành viên mới
     */
    public boolean registerMember(String name, String phone, String cardId) {
        if (conn == null) {
            return false;
        }

        String sql = "INSERT INTO members (card_id, name_enc, phone_enc, phone_hash, balance, status) VALUES (?, ?, ?, ?, 0, 'active')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cardId);
            ps.setString(2, SecurityUtils.encrypt(name));
            ps.setString(3, SecurityUtils.encrypt(phone));
            ps.setString(4, SecurityUtils.hashPhone(phone));
            ps.executeUpdate();
            System.out.println("✅ Đã đăng ký member: " + cardId);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lấy thông tin member theo card_id
     */
    public MemberInfo getMemberByCardId(String cardId) {
        if (conn == null) {
            return null;
        }

        String sql = "SELECT * FROM members WHERE card_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cardId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                MemberInfo m = new MemberInfo();
                m.id = rs.getInt("id");
                m.cardId = rs.getString("card_id");
                m.name = SecurityUtils.decrypt(rs.getString("name_enc"));
                m.phone = SecurityUtils.decrypt(rs.getString("phone_enc"));
                m.balance = rs.getLong("balance");
                m.status = rs.getString("status");
                m.createdAt = rs.getTimestamp("created_at");
                return m;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Tìm member theo số điện thoại (dùng hash)
     */
    public MemberInfo getMemberByPhone(String phone) {
        if (conn == null) {
            return null;
        }

        String phoneHash = SecurityUtils.hashPhone(phone);
        String sql = "SELECT * FROM members WHERE phone_hash = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phoneHash);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                MemberInfo m = new MemberInfo();
                m.id = rs.getInt("id");
                m.cardId = rs.getString("card_id");
                m.name = SecurityUtils.decrypt(rs.getString("name_enc"));
                m.phone = SecurityUtils.decrypt(rs.getString("phone_enc"));
                m.balance = rs.getLong("balance");
                m.status = rs.getString("status");
                return m;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Cập nhật số dư
     */
    public void updateBalance(String cardId, long balance) {
        if (conn == null) {
            return;
        }

        String sql = "UPDATE members SET balance = ? WHERE card_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, balance);
            ps.setString(2, cardId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Cập nhật avatar path
     */
    public void updateAvatar(String cardId, String avatarPath) {
        if (conn == null) {
            return;
        }

        String sql = "UPDATE members SET avatar_path = ? WHERE card_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, avatarPath);
            ps.setString(2, cardId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ==================== TRANSACTIONS ====================
    /**
     * Ghi log giao dịch (nạp tiền hoặc mua gói)
     */
    public void logTransaction(String cardId, String type, long amount, String signature) {
        if (conn == null) {
            return;
        }

        String sql = "INSERT INTO transactions (member_id, type, amount, signature) "
                + "SELECT id, ?, ?, ? FROM members WHERE card_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setLong(2, amount);
            ps.setString(3, signature);
            ps.setString(4, cardId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Ghi log mua gói tập (có package_id, trainer_id)
     */
    public void logPackagePurchase(String cardId, int packageId, Integer trainerId, long amount, String signature) {
        if (conn == null) {
            return;
        }

        String sql = "INSERT INTO transactions (member_id, type, amount, package_id, trainer_id, signature) "
                + "SELECT id, 'BUY_PACKAGE', ?, ?, ?, ? FROM members WHERE card_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setInt(2, packageId);
            if (trainerId != null) {
                ps.setInt(3, trainerId);
            } else {
                ps.setNull(3, Types.TINYINT);
            }
            ps.setString(4, signature);
            ps.setString(5, cardId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Lấy lịch sử giao dịch
     */
   /**
     * Lấy lịch sử tổng hợp (Giao dịch + Check-in)
     */
    public List<TransactionInfo> getTransactionHistory(String cardId, int limit) {
        List<TransactionInfo> list = new ArrayList<>();
        if (conn == null) return list;

        // Dùng UNION ALL để gộp bảng transactions và checkins
        String sql = "SELECT * FROM (" +
                     // Phần 1: Lấy giao dịch (Nạp tiền / Mua gói)
                     "  SELECT t.id, t.type, t.amount, p.name as package_name, tr.name as trainer_name, t.trans_time " +
                     "  FROM transactions t " +
                     "  JOIN members m ON t.member_id = m.id " +
                     "  LEFT JOIN packages p ON t.package_id = p.id " +
                     "  LEFT JOIN trainers tr ON t.trainer_id = tr.id " +
                     "  WHERE m.card_id = ? " +
                     
                     "  UNION ALL " +
                     
                     // Phần 2: Lấy lịch sử Check-in (Gán type = 'CHECKIN', amount = 0)
                     "  SELECT c.id, 'CHECKIN' as type, 0 as amount, NULL as package_name, NULL as trainer_name, c.checkin_time as trans_time " +
                     "  FROM checkins c " +
                     "  JOIN members m ON c.member_id = m.id " +
                     "  WHERE m.card_id = ? " +
                     ") AS combined_history " +
                     "ORDER BY trans_time DESC LIMIT ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Set tham số cho cả 2 phần SELECT trong UNION
            ps.setString(1, cardId); // Cho transactions
            ps.setString(2, cardId); // Cho checkins
            ps.setInt(3, limit);     // Cho LIMIT

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                TransactionInfo t = new TransactionInfo();
                t.id = rs.getInt("id");
                t.type = rs.getString("type");
                t.amount = rs.getLong("amount");
                t.packageName = rs.getString("package_name");
                t.trainerName = rs.getString("trainer_name");
                t.transTime = rs.getTimestamp("trans_time");
                list.add(t);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // ==================== PACKAGES ====================
    /**
     * Lấy danh sách gói tập
     */
    public List<PackageInfo> getAllPackages() {
        List<PackageInfo> list = new ArrayList<>();
        if (conn == null) {
            return list;
        }

        String sql = "SELECT * FROM packages ORDER BY id";
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                PackageInfo p = new PackageInfo();
                p.id = rs.getInt("id");
                p.name = rs.getString("name");
                p.price = rs.getInt("price");
                p.durationDays = rs.getObject("duration_days") != null ? rs.getInt("duration_days") : null;
                p.sessions = rs.getObject("sessions") != null ? rs.getInt("sessions") : null;
                p.description = rs.getString("description");
                list.add(p);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // ==================== TRAINERS ====================
    /**
     * Lấy danh sách HLV đang hoạt động
     */
    public List<TrainerInfo> getAllActiveTrainers() {
        List<TrainerInfo> list = new ArrayList<>();
        if (conn == null) {
            return list;
        }

        String sql = "SELECT * FROM trainers WHERE is_active = 1 ORDER BY rating DESC";
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                TrainerInfo t = new TrainerInfo();
                t.id = rs.getInt("id");
                t.name = rs.getString("name");
                t.phone = rs.getString("phone");
                t.experienceYears = rs.getInt("experience_years");
                t.rating = rs.getDouble("rating");
                t.bio = rs.getString("bio");
                list.add(t);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Lấy giá PT theo trainer và loại gói
     */
    public int getTrainerPrice(int trainerId, String packageType) {
        if (conn == null) {
            return 0;
        }

        String sql = "SELECT price FROM trainer_prices WHERE trainer_id = ? AND package_type = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, trainerId);
            ps.setString(2, packageType);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("price");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // ==================== MEMBER PACKAGES ====================
    /**
     * Mua gói tập cho member
     */
    public boolean purchasePackage(String cardId, int packageId, Integer trainerId) {
        if (conn == null) {
            return false;
        }

        try {
            // Lấy thông tin gói
            PackageInfo pkg = getPackageById(packageId);
            if (pkg == null) {
                return false;
            }

            // Lấy member
            MemberInfo member = getMemberByCardId(cardId);
            if (member == null) {
                return false;
            }

            // Tính giá
            int totalPrice = pkg.price;
            if (trainerId != null && pkg.sessions != null) {
                // Gói PT - lấy giá theo trainer
                String pkgType = pkg.sessions == 10 ? "SESSION_10" : "SESSION_20";
                totalPrice = getTrainerPrice(trainerId, pkgType);
            }

            // Kiểm tra số dư
            if (member.balance < totalPrice) {
                System.out.println("❌ Số dư không đủ!");
                return false;
            }

            // Trừ tiền
            updateBalance(cardId, member.balance - totalPrice);

            // Tính ngày hết hạn
            Timestamp expireDate = null;
            if (pkg.durationDays != null) {
                expireDate = new Timestamp(System.currentTimeMillis() + (long) pkg.durationDays * 24 * 60 * 60 * 1000);
            }

            // Thêm vào member_packages
            String sql = "INSERT INTO member_packages (member_id, package_id, trainer_id, expire_date, remaining_sessions, is_active) "
                    + "VALUES (?, ?, ?, ?, ?, 1)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, member.id);
                ps.setInt(2, packageId);
                if (trainerId != null) {
                    ps.setInt(3, trainerId);
                } else {
                    ps.setNull(3, Types.TINYINT);
                }
                ps.setTimestamp(4, expireDate);
                if (pkg.sessions != null) {
                    ps.setInt(5, pkg.sessions);
                } else {
                    ps.setNull(5, Types.SMALLINT);
                }
                ps.executeUpdate();
            }

            // Log transaction
            logPackagePurchase(cardId, packageId, trainerId, totalPrice, "");

            System.out.println("✅ Mua gói thành công: " + pkg.name);
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lấy gói tập đang active của member
     */
    public List<MemberPackageInfo> getActiveMemberPackages(String cardId) {
        List<MemberPackageInfo> list = new ArrayList<>();
        if (conn == null) {
            return list;
        }

        String sql = "SELECT mp.*, p.name as package_name, p.duration_days, p.sessions, t.name as trainer_name "
                + "FROM member_packages mp "
                + "JOIN members m ON mp.member_id = m.id "
                + "JOIN packages p ON mp.package_id = p.id "
                + "LEFT JOIN trainers t ON mp.trainer_id = t.id "
                + "WHERE m.card_id = ? AND mp.is_active = 1 "
                + "AND (mp.expire_date IS NULL OR mp.expire_date > NOW()) "
                + "AND (mp.remaining_sessions IS NULL OR mp.remaining_sessions > 0)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cardId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                MemberPackageInfo mp = new MemberPackageInfo();
                mp.id = rs.getInt("id");
                mp.packageName = rs.getString("package_name");
                mp.trainerName = rs.getString("trainer_name");
                mp.expireDate = rs.getTimestamp("expire_date");
                mp.remainingSessions = rs.getObject("remaining_sessions") != null ? rs.getInt("remaining_sessions") : null;
                list.add(mp);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private PackageInfo getPackageById(int id) {
        if (conn == null) {
            return null;
        }

        String sql = "SELECT * FROM packages WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                PackageInfo p = new PackageInfo();
                p.id = rs.getInt("id");
                p.name = rs.getString("name");
                p.price = rs.getInt("price");
                p.durationDays = rs.getObject("duration_days") != null ? rs.getInt("duration_days") : null;
                p.sessions = rs.getObject("sessions") != null ? rs.getInt("sessions") : null;
                return p;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ==================== CHECK-IN ====================
    /**
     * Ghi nhận check-in
     */
    public boolean checkIn(String cardId) {
        if (conn == null) {
            return false;
        }

        // Kiểm tra có gói còn hạn không
        List<MemberPackageInfo> packages = getActiveMemberPackages(cardId);
        if (packages.isEmpty()) {
            System.out.println("❌ Không có gói tập còn hạn!");
            return false;
        }

        // Ghi check-in
        String sql = "INSERT INTO checkins (member_id) SELECT id FROM members WHERE card_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cardId);
            ps.executeUpdate();
            System.out.println("✅ Check-in thành công!");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Đếm số lần check-in trong tháng
     */
    public int getMonthlyCheckInCount(String cardId) {
        if (conn == null) {
            return 0;
        }

        String sql = "SELECT COUNT(*) as cnt FROM checkins c "
                + "JOIN members m ON c.member_id = m.id "
                + "WHERE m.card_id = ? AND MONTH(c.checkin_time) = MONTH(NOW()) AND YEAR(c.checkin_time) = YEAR(NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cardId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("cnt");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // ==================== UNLOCK REQUESTS ====================
    /**
     * Tạo yêu cầu mở khóa (Dành cho Khách hàng) Xác thực SĐT hash trước khi tạo
     * request
     */
    public boolean createUnlockRequest(String cardId, String phone) {
        if (conn == null) {
            return false;
        }
        try {
            // 1. Kiểm tra SĐT có khớp với thẻ không
            String phoneHash = SecurityUtils.hashPhone(phone);
            String verifySql = "SELECT id FROM members WHERE card_id = ? AND phone_hash = ?";

            try (PreparedStatement psVerify = conn.prepareStatement(verifySql)) {
                psVerify.setString(1, cardId);
                psVerify.setString(2, phoneHash);
                ResultSet rs = psVerify.executeQuery();
                if (!rs.next()) {
                    System.out.println("❌ SĐT không khớp với thẻ!");
                    return false;
                }
            }

            // 2. Kiểm tra xem đã có request PENDING chưa
            String checkSql = "SELECT id FROM unlock_requests WHERE card_id = ? AND status = 'PENDING'";
            try (PreparedStatement psCheck = conn.prepareStatement(checkSql)) {
                psCheck.setString(1, cardId);
                ResultSet rs = psCheck.executeQuery();
                if (rs.next()) {
                    System.out.println("⚠️ Đã có yêu cầu đang chờ duyệt.");
                    return true; // Coi như thành công vì đã có request
                }
            }

            // 3. Tạo request mới
            String insertSql = "INSERT INTO unlock_requests (card_id, phone, status) VALUES (?, ?, 'PENDING')";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, cardId);
                ps.setString(2, phone);
                return ps.executeUpdate() > 0;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Kiểm tra trạng thái yêu cầu mở khóa của thẻ
     *
     * @return "APPROVED", "PENDING", "REJECTED" hoặc null (nếu chưa có)
     */
    public String checkRequestStatus(String cardId) {
        if (conn == null) {
            return null;
        }
        try {
            String sql = "SELECT status FROM unlock_requests WHERE card_id = ? ORDER BY created_at DESC LIMIT 1";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, cardId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("status");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Xóa yêu cầu mở khóa theo Card ID (Dùng sau khi đã mở khóa thành công)
     */
    public void deleteRequestByCardId(String cardId) {
        if (conn == null) {
            return;
        }
        try {
            String sql = "DELETE FROM unlock_requests WHERE card_id = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, cardId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ==================== UTILITY ====================
    public Connection getConnection() {
        return conn;
    }

    public void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ==================== DATA CLASSES ====================
    public static class MemberInfo {

        public int id;
        public String cardId;
        public String name;
        public String phone;
        public long balance;
        public String status;
        public Timestamp createdAt;
    }

    public static class PackageInfo {

        public int id;
        public String name;
        public int price;
        public Integer durationDays;
        public Integer sessions;
        public String description;
    }

    public static class TrainerInfo {

        public int id;
        public String name;
        public String phone;
        public int experienceYears;
        public double rating;
        public String bio;
    }

    public static class TransactionInfo {

        public int id;
        public String type;
        public long amount;
        public String packageName;
        public String trainerName;
        public Timestamp transTime;
    }

    public static class MemberPackageInfo {

        public int id;
        public String packageName;
        public String trainerName;
        public Timestamp expireDate;
        public Integer remainingSessions;
    }
}
