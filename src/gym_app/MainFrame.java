package gym_app;

import gym_app.panels.*;
import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    // Services
    private SmartCardService cardService;
    private DatabaseService dbService;

    // CardLayout
    private CardLayout cardLayout;
    private JPanel mainContainer;

    // Screen names - THÊM SCREEN_INIT
    public static final String SCREEN_INIT = "INIT";          // MỚI
    public static final String SCREEN_LOGIN = "LOGIN";
    public static final String SCREEN_DASHBOARD = "DASHBOARD";
    public static final String SCREEN_TOPUP = "TOPUP";
    public static final String SCREEN_PACKAGES = "PACKAGES";
    public static final String SCREEN_BUY_PACKAGE = "BUY_PACKAGE";
    public static final String SCREEN_PROFILE = "PROFILE";
    public static final String SCREEN_CHANGE_PIN = "CHANGE_PIN";
    public static final String SCREEN_UNBLOCK = "UNBLOCK";
    public static final String SCREEN_HISTORY = "HISTORY";
    public static final String SCREEN_CHECKIN = "CHECKIN";

    // User info
    private String currentCardId;
    private String currentName;
    private String currentPhone;

    // Panels - THÊM initCardPanel
    private InitCardPanel initCardPanel;                      // MỚI
    private LoginPanel loginPanel;
    private DashboardPanel dashboardPanel;
    private HistoryPanel historyPanel;
    private PackageListPanel packageListPanel;
    private BuyPackagePanel buyPackagePanel;
    private TopupPanel topupPanel;
    private CheckinPanel checkinPanel;
    private ProfileEditPanel profileEditPanel;
    private ChangePinPanel changePinPanel;
    private UnblockPinPanel unblockPinPanel;

    public MainFrame() {
        cardService = new SmartCardService();
        dbService = new DatabaseService();

        initUI();
        initPanels();
    }

    private void initUI() {
        setTitle("⚡ POWER GYM - HỆ THỐNG QUẢN LÝ THẺ THÀNH VIÊN");
        setSize(1300, 800);
        setMinimumSize(new Dimension(1100, 700));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);
        mainContainer.setBackground(new Color(30, 30, 45));

        add(mainContainer);
    }

    private void initPanels() {
        // ========== MỚI: Init Card Panel ==========
        initCardPanel = new InitCardPanel(this);
        mainContainer.add(initCardPanel, SCREEN_INIT);
        
        // Auth panels
        loginPanel = new LoginPanel(this);
        mainContainer.add(loginPanel, SCREEN_LOGIN);
        
        unblockPinPanel = new UnblockPinPanel(this);
        mainContainer.add(unblockPinPanel, SCREEN_UNBLOCK);

        // Change PIN panel
        changePinPanel = new ChangePinPanel(this);
        mainContainer.add(changePinPanel, SCREEN_CHANGE_PIN);

        // Main panels
        dashboardPanel = new DashboardPanel(this);
        mainContainer.add(dashboardPanel, SCREEN_DASHBOARD);

        topupPanel = new TopupPanel(this);
        mainContainer.add(topupPanel, SCREEN_TOPUP);
        
        packageListPanel = new PackageListPanel(this);
        mainContainer.add(packageListPanel, SCREEN_PACKAGES);
        
        buyPackagePanel = new BuyPackagePanel(this);
        mainContainer.add(buyPackagePanel, SCREEN_BUY_PACKAGE);
        
        profileEditPanel = new ProfileEditPanel(this);
        mainContainer.add(profileEditPanel, SCREEN_PROFILE);
        
        historyPanel = new HistoryPanel(this);
        mainContainer.add(historyPanel, SCREEN_HISTORY);
        
        checkinPanel = new CheckinPanel(this);
        mainContainer.add(checkinPanel, SCREEN_CHECKIN);

        // ========== MỚI: Kiểm tra thẻ đã init chưa ==========
        checkAndShowInitialScreen();
    }

    /**
     * MỚI: Kiểm tra thẻ đã có SĐT recovery chưa
     * - Nếu chưa → SCREEN_INIT (bắt buộc nhập SĐT)
     * - Nếu rồi → SCREEN_LOGIN
     */
    private void checkAndShowInitialScreen() {
        String recoveryPhone = cardService.getRecoveryPhone();
        
        if (recoveryPhone == null || recoveryPhone.isEmpty()) {
            System.out.println("[MainFrame] Thẻ mới chưa có SĐT → Hiển thị INIT");
            showScreen(SCREEN_INIT);
        } else {
            System.out.println("[MainFrame] Thẻ đã có SĐT: " + recoveryPhone + " → Hiển thị LOGIN");
            showScreen(SCREEN_LOGIN);
        }
    }

    public void showScreen(String screenName) {
        cardLayout.show(mainContainer, screenName);

        switch (screenName) {
            case SCREEN_INIT:                                  // MỚI
                initCardPanel.onShow();
                break;
            case SCREEN_LOGIN:
                loginPanel.onShow();
                break;
            case SCREEN_DASHBOARD:
                if (cardService.isPinVerified()) {
                    dashboardPanel.refreshData();
                }
                break;
            case SCREEN_HISTORY:
                historyPanel.loadHistory();
                break;
            case SCREEN_PACKAGES:
                packageListPanel.loadPackages();
                break;
            case SCREEN_BUY_PACKAGE:
                buyPackagePanel.onShow();
                break;
            case SCREEN_TOPUP:
                topupPanel.onShow();
                break;
            case SCREEN_CHECKIN:
                checkinPanel.onShow();
                break;
            case SCREEN_PROFILE:
                profileEditPanel.onShow();
                break;
            case SCREEN_CHANGE_PIN:
                changePinPanel.onShow();
                break;
        }
    }

    /**
     * MỚI: Được gọi từ InitCardPanel sau khi nhập SĐT thành công
     */
    public void onCardInitialized(String phone) {
        System.out.println("[MainFrame] Thẻ đã được khởi tạo với SĐT: " + phone);
        showScreen(SCREEN_LOGIN);
    }

    /**
     * Được gọi từ LoginPanel khi cần đổi PIN lần đầu
     */
    public void showChangePinFirstTime(String cardId, String name, String phone) {
        changePinPanel.setFirstTimeMode(cardId, name, phone);
        showScreen(SCREEN_CHANGE_PIN);
    }

    /**
     * Đăng nhập thành công
     */
    public void onLoginSuccess(String cardId, String name, String phone) {
        this.currentCardId = cardId;
        this.currentName = name;
        this.currentPhone = phone;
        
        cardService.setCardId(cardId);
        
        System.out.println("[MainFrame] Login success: " + cardId + ", " + name);
        
        dashboardPanel.setUserInfo(cardId, name, phone);
        showScreen(SCREEN_DASHBOARD);
    }

    /**
     * Đăng xuất (rút thẻ)
     */
    public void logout() {
        currentCardId = null;
        currentName = null;
        currentPhone = null;
        
        cardService.logout();
        changePinPanel.setNormalMode();
        
        // MỚI: Kiểm tra lại thẻ mới có cần init không
        checkAndShowInitialScreen();
    }

    // ==================== GETTERS ====================

    public SmartCardService getCardService() {
        return cardService;
    }

    public DatabaseService getDbService() {
        return dbService;
    }

    public String getCurrentCardId() {
        return currentCardId;
    }

    public String getCurrentName() {
        return currentName;
    }

    public String getCurrentPhone() {
        return currentPhone;
    }

    public void setCurrentName(String name) {
        this.currentName = name;
        if (dashboardPanel != null) {
            dashboardPanel.setUserInfo(currentCardId, name, currentPhone);
        }
    }

    public void setCurrentPhone(String phone) {
        this.currentPhone = phone;
        if (dashboardPanel != null) {
            dashboardPanel.setUserInfo(currentCardId, currentName, phone);
        }
    }
}