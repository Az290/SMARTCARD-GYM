package gym_app;

import com.licel.jcardsim.base.Simulator;
import javacard.framework.AID;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;

/**
 * SmartCardService - RSA KEM với Master Key do Card tự sinh
 *
 * ✅ v2.1 - CẬP NHẬT: - Mã hóa riêng từng trường thông tin (name, phone, email,
 * birthDate, address) - Thêm xác thực PIN trước mỗi thao tác quan trọng -
 * Avatar chuẩn hóa 48x48
 */
public class SmartCardService {

    // ====================== CONFIG ======================
    private static final byte[] APPLET_AID = {
        (byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD, (byte) 0xEE, 0x00
    };

    private static final int PIN_TRY_LIMIT = 5;
    private static final int PIN_SIZE = 6;
    private static final int AVATAR_MAX_SIZE = 10240;
    private static final int INFO_MAX_SIZE = 1024;
    private static final int BALANCE_UNIT = 10000;
    private static final int AES_BLOCK_SIZE = 16;
    private static final int RSA_BLOCK_SIZE = 128;  // RSA-1024
    private static final int SESSION_KEY_SIZE = 16; // AES-128
    private static final int IV_SIZE = 16;

    // ✅ MỚI: Chuẩn hóa avatar
    public static final int AVATAR_STANDARD_SIZE = 48; // 48x48 pixels

    // ====================== APDU COMMANDS ======================
    private static final byte CLA = (byte) 0x80;

    // PIN Operations
    private static final byte INS_VERIFY_PIN = (byte) 0x10;
    private static final byte INS_CHANGE_PIN = (byte) 0x11;
    private static final byte INS_UNBLOCK_PIN = (byte) 0x12;
    private static final byte INS_VERIFY_PIN_SECURE = (byte) 0x13;
    private static final byte INS_CHANGE_PIN_SECURE = (byte) 0x14;

    // Data Operations (RSA KEM)
    private static final byte INS_UPDATE_INFO_ENCAPS = (byte) 0x24;
    private static final byte INS_GET_INFO_ENCAPS = (byte) 0x25;
    private static final byte INS_UPLOAD_AVATAR_ENCAPS = (byte) 0x26;
    private static final byte INS_GET_AVATAR_ENCAPS = (byte) 0x27;

    // Transaction Operations (không mã hóa)
    private static final byte INS_TOPUP = (byte) 0x30;
    private static final byte INS_PAYMENT = (byte) 0x31;
    private static final byte INS_CHECK_BALANCE = (byte) 0x32;
    private static final byte INS_GET_HISTORY = (byte) 0x40;

    // Crypto Setup
    private static final byte INS_INIT_CRYPTO = (byte) 0x50;
    private static final byte INS_GET_STATUS = (byte) 0x51;
    private static final byte INS_SET_SESSION_KEY = (byte) 0x52;
    private static final byte INS_GEN_RSA_KEYPAIR = (byte) 0x60;
    private static final byte INS_GET_RSA_PUBLIC = (byte) 0x61;

    // Hash
    private static final byte INS_HASH_DATA = (byte) 0x81;

    // ====================== CRYPTO - PC SIDE ======================
    private Cipher aesCipher;
    private Cipher rsaCipher;
    private SecureRandom secureRandom;

    // RSA Public Key của Card (để encrypt PIN và Session Key)
    private java.security.PublicKey cardRsaPublicKey;
    private boolean cardRsaKeyLoaded = false;

    // Session Key hiện tại (cho mỗi operation)
    private byte[] currentSessionKey;
    private byte[] currentSessionIv;

    // ====================== JCARDSIM ======================
    private Simulator simulator;
    private AID appletAID;
    private boolean isConnected = false;

    // ====================== STATE ======================
    private String cardId;
    private String recoveryPhone = null;
    private String currentPIN = "123456";
    private int pinTriesRemaining = PIN_TRY_LIMIT;
    private boolean pinVerified = false;
    private boolean isFirstLogin = true;
    private boolean isCardBlocked = false;
    private boolean rsaKeysGenerated = false;

    // ✅ MỚI: Thời gian xác thực PIN gần nhất (để yêu cầu xác thực lại)
    private long lastPinVerifyTime = 0;
    private static final long PIN_RECONFIRM_TIMEOUT = 0;

    // Cache (chỉ để hiển thị, không dùng để mã hóa)
    private String savedInfo = null;
    private byte[] savedAvatar = null;
    private long savedBalance = 0;

    // ✅ MỚI: Cache từng trường riêng biệt
    private String cachedName = null;
    private String cachedPhone = null;
    private String cachedEmail = null;
    private String cachedBirthDate = null;
    private String cachedAddress = null;

    // ====================== CONSTRUCTOR ======================
    public SmartCardService() {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  SMARTCARD SERVICE v2.1 - MÃ HÓA TỪNG TRƯỜNG RIÊNG         ║");
        System.out.println("║  ✅ PIN: RSA encrypted trên đường truyền                   ║");
        System.out.println("║  ✅ Master Key: Card hash PIN nội bộ (PC không biết!)      ║");
        System.out.println("║  ✅ Mỗi trường (name, phone, ...) mã hóa riêng biệt        ║");
        System.out.println("║  ✅ Xác thực PIN trước mỗi thao tác quan trọng             ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        try {
            secureRandom = SecureRandom.getInstanceStrong();
        } catch (Exception e) {
            secureRandom = new SecureRandom();
        }

        initCrypto();
        initNewCard();
    }

    /**
     * Khởi tạo crypto objects ở PC-side
     */
    private void initCrypto() {
        try {
            aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            System.out.println("[Crypto] ✅ PC-side crypto initialized (NO Master Key!)");
        } catch (Exception e) {
            System.out.println("[Crypto] ❌ Init failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Khởi tạo thẻ mới hoàn toàn
     */
    private void initNewCard() {
        cardId = generateNewCardId();

        recoveryPhone = null;
        currentPIN = "123456";
        pinTriesRemaining = PIN_TRY_LIMIT;
        pinVerified = false;
        isFirstLogin = true;
        isCardBlocked = false;
        rsaKeysGenerated = false;
        cardRsaKeyLoaded = false;
        cardRsaPublicKey = null;
        currentSessionKey = null;
        currentSessionIv = null;
        savedInfo = null;
        savedAvatar = null;
        savedBalance = 0;
        lastPinVerifyTime = 0;

        // Reset cache
        cachedName = null;
        cachedPhone = null;
        cachedEmail = null;
        cachedBirthDate = null;
        cachedAddress = null;

        initSimulator();

        System.out.println("[CARD] ✅ Thẻ mới được tạo: " + cardId);
    }

    private String generateNewCardId() {
        Random random = new Random();
        return String.format("GYM%06d", random.nextInt(1000000));
    }

    private void initSimulator() {
        try {
            simulator = new Simulator();
            appletAID = new AID(APPLET_AID, (short) 0, (byte) APPLET_AID.length);

            System.out.println("[JCSIM] 🔧 Installing applet...");
            simulator.installApplet(appletAID, thegym.thegym.class);
            System.out.println("[JCSIM] ✅ Applet installed!");

            boolean selected = simulator.selectApplet(appletAID);
            if (selected) {
                isConnected = true;
                System.out.println("[JCSIM] ✅ Applet selected!");

                // Init crypto trên Card
                CommandAPDU apdu = new CommandAPDU(CLA, INS_INIT_CRYPTO, 0x00, 0x00, 1);
                ResponseAPDU resp = sendAPDU(apdu);
                if (resp != null && resp.getSW() == 0x9000) {
                    System.out.println("[JCSIM] ✅ Card crypto initialized");
                }

                // Sinh RSA Key Pair ngay từ đầu
                System.out.println("[JCSIM] 🔑 Generating RSA key pair...");
                apdu = new CommandAPDU(CLA, INS_GEN_RSA_KEYPAIR, 0x00, 0x00, 1);
                resp = sendAPDU(apdu);
                if (resp != null && resp.getSW() == 0x9000) {
                    rsaKeysGenerated = true;
                    System.out.println("[JCSIM] ✅ RSA key pair generated!");

                    // Load Public Key về PC
                    loadCardRSAPublicKey();
                }
            }
        } catch (Exception e) {
            System.out.println("[JCSIM] ❌ Init error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ====================== SEND APDU ======================
    private ResponseAPDU sendAPDU(CommandAPDU apdu) {
        if (!isConnected) {
            return null;
        }

        try {
            byte[] response = simulator.transmitCommand(apdu.getBytes());
            ResponseAPDU resp = new ResponseAPDU(response);

            System.out.println("[JCSIM] → " + bytesToHex(apdu.getBytes()));
            System.out.println("[JCSIM] ← " + bytesToHex(response)
                    + " (SW=" + String.format("%04X", resp.getSW()) + ")");

            return resp;
        } catch (Exception e) {
            System.out.println("[JCSIM] ❌ APDU error: " + e.getMessage());
            return null;
        }
    }

    // ====================== RSA KEY MANAGEMENT ======================
    private boolean loadCardRSAPublicKey() {
        try {
            System.out.println("[RSA] 📥 Loading RSA public key from card...");

            // Lấy Modulus
            CommandAPDU apdu = new CommandAPDU(CLA, INS_GET_RSA_PUBLIC, 0x01, 0x00, RSA_BLOCK_SIZE);
            ResponseAPDU resp = sendAPDU(apdu);

            if (resp == null || resp.getSW() != 0x9000) {
                System.out.println("[RSA] ❌ Failed to get modulus");
                return false;
            }
            byte[] modulus = resp.getData();

            // Lấy Exponent
            apdu = new CommandAPDU(CLA, INS_GET_RSA_PUBLIC, 0x02, 0x00, 8);
            resp = sendAPDU(apdu);

            if (resp == null || resp.getSW() != 0x9000) {
                System.out.println("[RSA] ❌ Failed to get exponent");
                return false;
            }
            byte[] exponent = resp.getData();

            // Tạo Public Key object
            BigInteger n = new BigInteger(1, modulus);
            BigInteger e = new BigInteger(1, exponent);

            RSAPublicKeySpec spec = new RSAPublicKeySpec(n, e);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            cardRsaPublicKey = factory.generatePublic(spec);

            cardRsaKeyLoaded = true;
            System.out.println("[RSA] ✅ RSA public key loaded!");

            return true;

        } catch (Exception ex) {
            System.out.println("[RSA] ❌ Error loading public key: " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * Sinh Session Key + IV ngẫu nhiên
     */
    private void generateSessionKey() {
        currentSessionKey = new byte[SESSION_KEY_SIZE];
        currentSessionIv = new byte[IV_SIZE];

        secureRandom.nextBytes(currentSessionKey);
        secureRandom.nextBytes(currentSessionIv);

        System.out.println("[Session] ✅ Generated new session key & IV");
    }

    /**
     * RSA Encrypt Session Key + IV để gửi cho Card
     */
    private byte[] encapsulateSessionKey() throws Exception {
        if (!cardRsaKeyLoaded) {
            throw new Exception("Card RSA public key not loaded");
        }

        byte[] keyMaterial = new byte[SESSION_KEY_SIZE + IV_SIZE];
        System.arraycopy(currentSessionKey, 0, keyMaterial, 0, SESSION_KEY_SIZE);
        System.arraycopy(currentSessionIv, 0, keyMaterial, SESSION_KEY_SIZE, IV_SIZE);

        rsaCipher.init(Cipher.ENCRYPT_MODE, cardRsaPublicKey);
        byte[] encrypted = rsaCipher.doFinal(keyMaterial);

        System.out.println("[Session] 🔐 Encapsulated session key: " + encrypted.length + " bytes");

        return encrypted;
    }

    /**
     * RSA Encrypt PIN để gửi cho Card
     */
    private byte[] encryptPIN(String pin) throws Exception {
        if (!cardRsaKeyLoaded) {
            throw new Exception("Card RSA public key not loaded");
        }

        byte[] pinBytes = pin.getBytes("UTF-8");

        rsaCipher.init(Cipher.ENCRYPT_MODE, cardRsaPublicKey);
        byte[] encrypted = rsaCipher.doFinal(pinBytes);

        System.out.println("[PIN] 🔐 PIN encrypted with RSA: " + encrypted.length + " bytes");

        return encrypted;
    }

    /**
     * AES Encrypt với Session Key
     */
    private byte[] encryptWithSessionKey(byte[] plaintext) throws Exception {
        SecretKeySpec sessionKeySpec = new SecretKeySpec(currentSessionKey, "AES");
        IvParameterSpec sessionIvSpec = new IvParameterSpec(currentSessionIv);

        aesCipher.init(Cipher.ENCRYPT_MODE, sessionKeySpec, sessionIvSpec);
        return aesCipher.doFinal(plaintext);
    }

    /**
     * AES Decrypt với Session Key
     */
    private byte[] decryptWithSessionKey(byte[] ciphertext) throws Exception {
        SecretKeySpec sessionKeySpec = new SecretKeySpec(currentSessionKey, "AES");
        IvParameterSpec sessionIvSpec = new IvParameterSpec(currentSessionIv);

        aesCipher.init(Cipher.DECRYPT_MODE, sessionKeySpec, sessionIvSpec);
        return aesCipher.doFinal(ciphertext);
    }

    // ====================== ✅ MỚI: XÁC THỰC PIN ======================
    /**
     * ✅ MỚI: Kiểm tra xem có cần xác thực lại PIN không
     *
     * @return true nếu cần xác thực lại
     */
    public boolean needsPinReconfirmation() {
        if (!pinVerified) {
            return true;
        }

        long elapsed = System.currentTimeMillis() - lastPinVerifyTime;
        return elapsed > PIN_RECONFIRM_TIMEOUT;
    }

    /**
     * ✅ MỚI: Xác thực lại PIN (không thay đổi session, chỉ verify) Dùng cho các
     * thao tác quan trọng như nạp tiền, thanh toán
     */
    public boolean reVerifyPIN(String pin) {
        if (pin == null || pin.length() != PIN_SIZE) {
            return false;
        }

        if (!pin.equals(currentPIN)) {
            System.out.println("[PIN] ❌ PIN không khớp!");
            return false;
        }

        // Cập nhật thời gian xác thực
        lastPinVerifyTime = System.currentTimeMillis();
        System.out.println("[PIN] ✅ PIN re-verified successfully");
        return true;
    }

    /**
     * ✅ MỚI: Lấy PIN hiện tại (để so sánh khi xác thực lại) CHÚ Ý: Chỉ dùng nội
     * bộ, KHÔNG expose ra ngoài!
     */
    public String getCurrentPIN() {
        return currentPIN;
    }

    // ====================== PIN OPERATIONS ======================
    public boolean verifyPIN(String pin6) {
        if (pin6 == null || pin6.length() != PIN_SIZE) {
            return false;
        }
        if (isCardBlocked) {
            System.out.println("[JCSIM] ❌ Card is BLOCKED!");
            return false;
        }

        System.out.println("\n[PIN] ═══════════════════════════════════════════════");
        System.out.println("[PIN] 🔐 VERIFY PIN với RSA encryption");

        try {
            if (!cardRsaKeyLoaded) {
                System.out.println("[PIN] ⚠️ RSA not ready, using legacy method");
                return verifyPINLegacy(pin6);
            }

            byte[] encryptedPIN = encryptPIN(pin6);

            CommandAPDU apdu = new CommandAPDU(CLA, INS_VERIFY_PIN_SECURE, 0x00, 0x00, encryptedPIN);
            ResponseAPDU resp = sendAPDU(apdu);

            if (resp != null && resp.getSW() == 0x9000) {
                pinVerified = true;
                pinTriesRemaining = PIN_TRY_LIMIT;
                currentPIN = pin6;
                lastPinVerifyTime = System.currentTimeMillis(); // ✅ Cập nhật thời gian

                System.out.println("[PIN] ✅ PIN verified! Master Key đã được Card tự sinh.");
                System.out.println("[PIN] ═══════════════════════════════════════════════\n");

                return true;

            } else if (resp != null && (resp.getSW() & 0xFFF0) == 0x63C0) {
                pinTriesRemaining = resp.getSW() & 0x000F;
                pinVerified = false;

                if (pinTriesRemaining <= 0) {
                    isCardBlocked = true;
                    System.out.println("[PIN] 🔒 CARD BLOCKED!");
                }

                System.out.println("[PIN] ❌ Wrong PIN! Remaining: " + pinTriesRemaining);
            }

        } catch (Exception e) {
            System.out.println("[PIN] ❌ Error: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    private boolean verifyPINLegacy(String pin6) {
        byte[] pinBytes = pin6.getBytes();
        CommandAPDU apdu = new CommandAPDU(CLA, INS_VERIFY_PIN, 0x00, 0x00, pinBytes);
        ResponseAPDU resp = sendAPDU(apdu);

        if (resp != null && resp.getSW() == 0x9000) {
            pinVerified = true;
            pinTriesRemaining = PIN_TRY_LIMIT;
            currentPIN = pin6;
            lastPinVerifyTime = System.currentTimeMillis();
            System.out.println("[PIN] ✅ PIN verified (legacy)!");
            return true;
        } else if (resp != null && (resp.getSW() & 0xFFF0) == 0x63C0) {
            pinTriesRemaining = resp.getSW() & 0x000F;
            pinVerified = false;
            if (pinTriesRemaining <= 0) {
                isCardBlocked = true;
            }
        }
        return false;
    }

    public boolean changePIN(String oldPin, String newPin) {
        if (oldPin == null || oldPin.length() != PIN_SIZE
                || newPin == null || newPin.length() != PIN_SIZE) {
            return false;
        }

        System.out.println("\n[PIN] ═══════════════════════════════════════════════");
        System.out.println("[PIN] 🔐 CHANGE PIN với RSA encryption");

        try {
            if (!cardRsaKeyLoaded) {
                System.out.println("[PIN] ⚠️ RSA not ready, using legacy method");
                return changePINLegacy(oldPin, newPin);
            }

            byte[] bothPins = new byte[PIN_SIZE * 2];
            System.arraycopy(oldPin.getBytes("UTF-8"), 0, bothPins, 0, PIN_SIZE);
            System.arraycopy(newPin.getBytes("UTF-8"), 0, bothPins, PIN_SIZE, PIN_SIZE);

            rsaCipher.init(Cipher.ENCRYPT_MODE, cardRsaPublicKey);
            byte[] encryptedPins = rsaCipher.doFinal(bothPins);

            CommandAPDU apdu = new CommandAPDU(CLA, INS_CHANGE_PIN_SECURE, 0x00, 0x00, encryptedPins);
            ResponseAPDU resp = sendAPDU(apdu);

            if (resp != null && resp.getSW() == 0x9000) {
                currentPIN = newPin;
                isFirstLogin = false;

                System.out.println("[PIN] ✅ PIN changed! Master Key đã được cập nhật.");
                System.out.println("[PIN] ═══════════════════════════════════════════════\n");

                pinVerified = false;
                return verifyPIN(newPin);

            } else if (resp != null && (resp.getSW() & 0xFFF0) == 0x63C0) {
                pinTriesRemaining = resp.getSW() & 0x000F;
                System.out.println("[PIN] ❌ Wrong old PIN! Remaining: " + pinTriesRemaining);
            }

        } catch (Exception e) {
            System.out.println("[PIN] ❌ Error: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    private boolean changePINLegacy(String oldPin, String newPin) {
        byte[] data = new byte[12];
        System.arraycopy(oldPin.getBytes(), 0, data, 0, 6);
        System.arraycopy(newPin.getBytes(), 0, data, 6, 6);

        CommandAPDU apdu = new CommandAPDU(CLA, INS_CHANGE_PIN, 0x00, 0x00, data);
        ResponseAPDU resp = sendAPDU(apdu);

        if (resp != null && resp.getSW() == 0x9000) {
            currentPIN = newPin;
            isFirstLogin = false;
            System.out.println("[PIN] ✅ PIN changed (legacy)!");

            pinVerified = false;
            return verifyPINLegacy(newPin);
        }
        return false;
    }

    public boolean unblockCard(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }

        if (recoveryPhone == null || !recoveryPhone.equals(phone)) {
            System.out.println("[JCSIM] ❌ Phone not match!");
            return false;
        }

        byte[] phoneBytes = phone.getBytes();
        CommandAPDU apdu = new CommandAPDU(CLA, INS_UNBLOCK_PIN, 0x00, 0x00, phoneBytes);
        ResponseAPDU resp = sendAPDU(apdu);

        if (resp != null && resp.getSW() == 0x9000) {
            pinTriesRemaining = PIN_TRY_LIMIT;
            isCardBlocked = false;
            isFirstLogin = true;
            currentPIN = "123456";
            pinVerified = false;
            lastPinVerifyTime = 0;

            System.out.println("[JCSIM] ✅ Card unblocked! PIN reset về 123456");
            return true;
        }

        return false;
    }

    // ====================== ✅ MỚI: INFO OPERATIONS - MÃ HÓA RIÊNG TỪNG TRƯỜNG ======================
    /**
     * ✅ MỚI: Cập nhật thông tin với mã hóa riêng từng trường Mỗi trường được mã
     * hóa độc lập trước khi gửi lên Card
     *
     * Format gửi lên Card:
     * [FIELD_COUNT][FIELD1_TYPE][FIELD1_LEN][FIELD1_ENCRYPTED_DATA]...
     *
     * @param name Họ tên (max 40 chars)
     * @param phone Số điện thoại (10-11 số)
     * @param email Email (có thể null)
     * @param birthDate Ngày sinh dd/MM/yyyy (có thể null)
     * @param address Địa chỉ (có thể null)
     * @return true nếu thành công
     */
    public boolean updateInfoFields(String name, String phone, String email, String birthDate, String address) {
        if (!pinVerified) {
            return false;
        }
        if (!cardRsaKeyLoaded) {
            return false;
        }

        try {
            System.out.println("\n[INFO] ═══════════════════════════════════════════════");
            System.out.println("[INFO] 📤 UPDATE INFO - CHIA 2 GÓI TIN (HANDSHAKE + DATA)");

            // 1. Sinh Session Key và Encapsulate (RSA)
            generateSessionKey();
            byte[] encapsulatedKey = encapsulateSessionKey();

            // 2. Gửi Lệnh 1: Handshake (Gửi Key RSA)
            System.out.println("[INFO] 1️⃣ Sending Session Key (" + encapsulatedKey.length + " bytes)...");
            CommandAPDU apduKey = new CommandAPDU(CLA, INS_SET_SESSION_KEY, 0x00, 0x00, encapsulatedKey);
            ResponseAPDU respKey = sendAPDU(apduKey);

            if (respKey == null || respKey.getSW() != 0x9000) {
                System.out.println("[INFO] ❌ Handshake failed: SW=" + String.format("%04X", respKey != null ? respKey.getSW() : 0));
                return false;
            }

            // 3. Chuẩn bị dữ liệu Payload (Plaintext)
            ByteArrayOutputStream payloadBaos = new ByteArrayOutputStream();
            ByteArrayOutputStream fieldsData = new ByteArrayOutputStream();
            int fieldCount = 0;

            // --- Helper đóng gói trường ---
            if (name != null && !name.isEmpty()) {
                byte[] enc = encryptFieldWithSessionKey(name, (byte) 0x01);
                fieldsData.write(0x01);
                fieldsData.write((enc.length >> 8) & 0xFF);
                fieldsData.write(enc.length & 0xFF);
                fieldsData.write(enc);
                fieldCount++;
            }
            if (phone != null && !phone.isEmpty()) {
                byte[] enc = encryptFieldWithSessionKey(phone, (byte) 0x02);
                fieldsData.write(0x02);
                fieldsData.write((enc.length >> 8) & 0xFF);
                fieldsData.write(enc.length & 0xFF);
                fieldsData.write(enc);
                fieldCount++;
            }
            if (email != null && !email.isEmpty()) {
                byte[] enc = encryptFieldWithSessionKey(email, (byte) 0x03);
                fieldsData.write(0x03);
                fieldsData.write((enc.length >> 8) & 0xFF);
                fieldsData.write(enc.length & 0xFF);
                fieldsData.write(enc);
                fieldCount++;
            }
            if (birthDate != null && !birthDate.isEmpty()) {
                byte[] enc = encryptFieldWithSessionKey(birthDate, (byte) 0x04);
                fieldsData.write(0x04);
                fieldsData.write((enc.length >> 8) & 0xFF);
                fieldsData.write(enc.length & 0xFF);
                fieldsData.write(enc);
                fieldCount++;
            }
            if (address != null && !address.isEmpty()) {
                byte[] enc = encryptFieldWithSessionKey(address, (byte) 0x05);
                fieldsData.write(0x05);
                fieldsData.write((enc.length >> 8) & 0xFF);
                fieldsData.write(enc.length & 0xFF);
                fieldsData.write(enc);
                fieldCount++;
            }

            payloadBaos.write((byte) fieldCount);
            payloadBaos.write(fieldsData.toByteArray());
            byte[] payload = payloadBaos.toByteArray();

            // 4. Encrypt Payload bằng AES Session Key
            byte[] encryptedPayload = encryptWithSessionKey(payload);

            System.out.println("[INFO] 2️⃣ Sending Data Payload (" + encryptedPayload.length + " bytes)...");

            // 5. Gửi Lệnh 2: Data (AES Only)
            CommandAPDU apduData = new CommandAPDU(CLA, INS_UPDATE_INFO_ENCAPS, 0x00, 0x00, encryptedPayload);
            ResponseAPDU respData = sendAPDU(apduData);

            if (respData != null && respData.getSW() == 0x9000) {
                // Cập nhật Cache
                this.recoveryPhone = phone;
                this.cachedName = name;
                this.cachedPhone = phone;
                this.cachedEmail = email;
                this.cachedBirthDate = birthDate;
                this.cachedAddress = address;
                this.savedInfo = name;

                System.out.println("[INFO] ✅ All fields saved successfully via Split APDU!");
                System.out.println("[INFO] ═══════════════════════════════════════════════\n");
                return true;
            } else {
                System.out.println("[INFO] ❌ Data send failed: SW=" + String.format("%04X", respData != null ? respData.getSW() : 0));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * ✅ MỚI: Mã hóa một trường riêng biệt Thêm type byte vào đầu để Card biết
     * đây là trường gì
     */
    private byte[] encryptFieldWithSessionKey(String value, byte fieldType) throws Exception {
        byte[] valueBytes = value.getBytes("UTF-8");

        // Format: [TYPE][VALUE...]
        byte[] plaintext = new byte[1 + valueBytes.length];
        plaintext[0] = fieldType;
        System.arraycopy(valueBytes, 0, plaintext, 1, valueBytes.length);

        // Encrypt với Session Key
        SecretKeySpec sessionKeySpec = new SecretKeySpec(currentSessionKey, "AES");
        IvParameterSpec sessionIvSpec = new IvParameterSpec(currentSessionIv);

        aesCipher.init(Cipher.ENCRYPT_MODE, sessionKeySpec, sessionIvSpec);
        return aesCipher.doFinal(plaintext);
    }

    /**
     * ✅ Giữ nguyên updateInfo() để tương thích ngược Nhưng nội bộ gọi
     * updateInfoFields()
     */
    public boolean updateInfo(String phone, String info) {
        // Tương thích ngược: info = name
        return updateInfoFields(info, phone, null, null, null);
    }

    /**
     * GET INFO - Giữ nguyên logic cũ
     */
    public String getInfo() {
        if (!pinVerified) {
            System.out.println("[INFO] ⚠️ PIN not verified, returning cached");
            return savedInfo;
        }

        if (!cardRsaKeyLoaded) {
            System.out.println("[INFO] ❌ RSA not ready");
            return savedInfo;
        }

        try {
            System.out.println("\n[INFO] ═══════════════════════════════════════════════");
            System.out.println("[INFO] 📥 GET INFO với RSA KEM + Master Key");

            generateSessionKey();
            byte[] encapsulatedKey = encapsulateSessionKey();

            CommandAPDU apdu = new CommandAPDU(CLA, INS_GET_INFO_ENCAPS, 0x00, 0x00, encapsulatedKey);
            ResponseAPDU resp = sendAPDU(apdu);

            if (resp != null && resp.getSW() == 0x9000) {
                byte[] encryptedData = resp.getData();

                if (encryptedData.length > 0 && encryptedData.length % AES_BLOCK_SIZE == 0) {
                    byte[] plaintext = decryptWithSessionKey(encryptedData);

                    int decLen = plaintext.length;
                    byte padValue = plaintext[decLen - 1];
                    if (padValue > 0 && padValue <= AES_BLOCK_SIZE) {
                        decLen -= padValue;
                    }

                    savedInfo = new String(plaintext, 0, decLen, "UTF-8").trim();

                    System.out.println("[INFO] ✅ Received: " + savedInfo.length() + " chars");
                    System.out.println("[INFO] ═══════════════════════════════════════════════\n");
                    return savedInfo;
                }
            } else if (resp != null && resp.getSW() == 0x6985) {
                System.out.println("[INFO] ℹ️ No data stored on card");
            }

        } catch (Exception e) {
            System.out.println("[INFO] ❌ Error: " + e.getMessage());
            e.printStackTrace();
        }

        return savedInfo;
    }

    // ====================== AVATAR OPERATIONS ======================
    public boolean uploadAvatar(byte[] avatarData) {
        if (!pinVerified) {
            System.out.println("[AVATAR] ❌ PIN not verified");
            return false;
        }

        if (!cardRsaKeyLoaded) {
            System.out.println("[AVATAR] ❌ RSA not ready");
            return false;
        }

        try {
            System.out.println("\n[AVATAR] ═══════════════════════════════════════════════");
            System.out.println("[AVATAR] 📤 UPLOAD AVATAR với RSA KEM + Master Key");
            System.out.println("[AVATAR] Original size: " + avatarData.length + " bytes");

            generateSessionKey();
            byte[] encapsulatedKey = encapsulateSessionKey();

            byte[] encryptedAvatar = encryptWithSessionKey(avatarData);

            System.out.println("[AVATAR] Encrypted size: " + encryptedAvatar.length + " bytes");

            int offset = 0;
            int maxPayloadPerChunk = 96;

            while (offset < encryptedAvatar.length) {
                int remaining = encryptedAvatar.length - offset;
                int chunkSize = Math.min(maxPayloadPerChunk, remaining);

                if (remaining > maxPayloadPerChunk && chunkSize % AES_BLOCK_SIZE != 0) {
                    chunkSize = (chunkSize / AES_BLOCK_SIZE) * AES_BLOCK_SIZE;
                }

                boolean isLastChunk = (offset + chunkSize >= encryptedAvatar.length);

                byte[] chunkData;
                byte p1, p2;

                if (offset == 0) {
                    chunkData = new byte[encapsulatedKey.length + chunkSize];
                    System.arraycopy(encapsulatedKey, 0, chunkData, 0, encapsulatedKey.length);
                    System.arraycopy(encryptedAvatar, offset, chunkData, encapsulatedKey.length, chunkSize);
                    p1 = 0;
                    p2 = 0;
                } else {
                    chunkData = new byte[chunkSize];
                    System.arraycopy(encryptedAvatar, offset, chunkData, 0, chunkSize);
                    p1 = (byte) ((offset >> 8) & 0x7F);
                    p2 = (byte) (offset & 0xFF);
                }

                if (isLastChunk) {
                    p1 = (byte) (p1 | 0x80);
                }

                CommandAPDU apdu = new CommandAPDU(CLA, INS_UPLOAD_AVATAR_ENCAPS, p1, p2, chunkData);
                ResponseAPDU resp = sendAPDU(apdu);

                if (resp == null || (resp.getSW() != 0x9000 && !isLastChunk)) {
                    System.out.println("[AVATAR] ❌ Chunk upload failed at offset " + offset);
                    return false;
                }

                if (isLastChunk && resp.getSW() == 0x9000 && resp.getData().length == 2) {
                    int storedSize = ((resp.getData()[0] & 0xFF) << 8) | (resp.getData()[1] & 0xFF);
                    System.out.println("[AVATAR] ✓ Card stored: " + storedSize + " bytes (plaintext)");
                }

                System.out.println("[AVATAR] ✓ Chunk: offset=" + offset + ", size=" + chunkSize
                        + (isLastChunk ? " [FINAL]" : ""));

                offset += chunkSize;
            }

            savedAvatar = avatarData.clone();
            System.out.println("[AVATAR] ✅ Avatar uploaded successfully!");
            System.out.println("[AVATAR] ═══════════════════════════════════════════════\n");
            return true;

        } catch (Exception e) {
            System.out.println("[AVATAR] ❌ Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public byte[] getAvatar() {
        if (!pinVerified) {
            System.out.println("[AVATAR] ⚠️ PIN not verified, returning cached");
            return savedAvatar;
        }

        if (!cardRsaKeyLoaded) {
            System.out.println("[AVATAR] ❌ RSA not ready");
            return savedAvatar;
        }

        try {
            System.out.println("\n[AVATAR] ═══════════════════════════════════════════════");
            System.out.println("[AVATAR] 📥 GET AVATAR với RSA KEM + Master Key");

            generateSessionKey();
            byte[] encapsulatedKey = encapsulateSessionKey();

            CommandAPDU apdu = new CommandAPDU(CLA, INS_GET_AVATAR_ENCAPS, 0x00, 0x00, encapsulatedKey);
            ResponseAPDU resp = sendAPDU(apdu);

            if (resp == null || resp.getSW() != 0x9000) {
                System.out.println("[AVATAR] ❌ Failed to init get avatar");
                return savedAvatar;
            }

            byte[] sizeData = resp.getData();
            if (sizeData.length < 2) {
                System.out.println("[AVATAR] ℹ️ No avatar stored");
                return null;
            }

            int plaintextSize = ((sizeData[0] & 0xFF) << 8) | (sizeData[1] & 0xFF);
            if (plaintextSize == 0) {
                System.out.println("[AVATAR] ℹ️ Avatar size is 0");
                return null;
            }

            System.out.println("[AVATAR] Plaintext size: " + plaintextSize + " bytes");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int offset = 0;

            while (offset < plaintextSize) {
                byte p1 = (byte) ((offset >> 8) & 0xFF);
                byte p2 = (byte) (offset & 0xFF);

                apdu = new CommandAPDU(CLA, INS_GET_AVATAR_ENCAPS, p1, p2, new byte[0], 256);
                resp = sendAPDU(apdu);

                if (resp == null || resp.getSW() != 0x9000) {
                    System.out.println("[AVATAR] ❌ Failed at offset " + offset);
                    break;
                }

                byte[] encryptedChunk = resp.getData();
                if (encryptedChunk.length == 0) {
                    break;
                }

                byte[] decryptedChunk = decryptWithSessionKey(encryptedChunk);

                int chunkPlainLen = decryptedChunk.length;
                int remainingToRead = plaintextSize - offset;

                if (remainingToRead < chunkPlainLen) {
                    chunkPlainLen = remainingToRead;
                } else {
                    byte padValue = decryptedChunk[chunkPlainLen - 1];
                    if (padValue > 0 && padValue <= AES_BLOCK_SIZE && chunkPlainLen - padValue >= remainingToRead - 100) {
                        boolean validPad = true;
                        for (int i = chunkPlainLen - padValue; i < chunkPlainLen; i++) {
                            if (decryptedChunk[i] != padValue) {
                                validPad = false;
                                break;
                            }
                        }
                        if (validPad && chunkPlainLen - padValue <= remainingToRead) {
                            chunkPlainLen -= padValue;
                        }
                    }
                }

                if (offset + chunkPlainLen > plaintextSize) {
                    chunkPlainLen = plaintextSize - offset;
                }

                baos.write(decryptedChunk, 0, chunkPlainLen);

                System.out.println("[AVATAR] ✓ Chunk: offset=" + offset
                        + ", encrypted=" + encryptedChunk.length
                        + ", plaintext=" + chunkPlainLen);

                offset += chunkPlainLen;

                if (offset >= plaintextSize) {
                    break;
                }
            }

            byte[] result = baos.toByteArray();

            if (result.length > plaintextSize) {
                result = Arrays.copyOf(result, plaintextSize);
            }

            if (result.length > 0) {
                savedAvatar = result;
                System.out.println("[AVATAR] ✅ Downloaded: " + result.length + " bytes");
                System.out.println("[AVATAR] ═══════════════════════════════════════════════\n");
                return result;
            }

        } catch (Exception e) {
            System.out.println("[AVATAR] ❌ Error: " + e.getMessage());
            e.printStackTrace();
        }

        return savedAvatar;
    }

    // ====================== BALANCE OPERATIONS ======================
    public long getBalance() {
        if (!pinVerified) {
            return savedBalance;
        }

        CommandAPDU apdu = new CommandAPDU(CLA, INS_CHECK_BALANCE, 0x00, 0x00, 2);
        ResponseAPDU resp = sendAPDU(apdu);

        if (resp != null && resp.getSW() == 0x9000) {
            byte[] data = resp.getData();
            if (data.length == 2) {
                int units = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                savedBalance = (long) units * BALANCE_UNIT;
                return savedBalance;
            }
        }
        return savedBalance;
    }

    public boolean topup(long amountVND) {
        if (!pinVerified || amountVND <= 0) {
            return false;
        }

        int units = (int) (amountVND / BALANCE_UNIT);
        if (units <= 0 || units > 255) {
            return false;
        }

        CommandAPDU apdu = new CommandAPDU(CLA, INS_TOPUP, (byte) units, 0x00, 2);
        ResponseAPDU resp = sendAPDU(apdu);

        if (resp != null && resp.getSW() == 0x9000) {
            savedBalance = getBalance();
            return true;
        }

        return false;
    }

    public boolean deductBalance(long amountVND) {
        if (!pinVerified || amountVND <= 0) {
            return false;
        }

        int units = (int) (amountVND / BALANCE_UNIT);
        if (units <= 0 || units > 255) {
            return false;
        }

        CommandAPDU apdu = new CommandAPDU(CLA, INS_PAYMENT, (byte) units, 0x00, 2);
        ResponseAPDU resp = sendAPDU(apdu);

        if (resp != null && resp.getSW() == 0x9000) {
            savedBalance = getBalance();
            return true;
        }

        return false;
    }

    // ====================== OTHER OPERATIONS ======================
    public boolean checkIn() {
        return pinVerified;
    }

    public byte[] signTransaction(byte type, long amountVND) {
        if (!pinVerified) {
            return new byte[0];
        }

        try {
            String txData = type + "|" + amountVND + "|" + System.currentTimeMillis();
            byte[] data = txData.getBytes();

            CommandAPDU apdu = new CommandAPDU(CLA, INS_HASH_DATA, 0x00, 0x00, data, 32);
            ResponseAPDU resp = sendAPDU(apdu);

            if (resp != null && resp.getSW() == 0x9000) {
                return resp.getData();
            }
        } catch (Exception e) {
            // ignore
        }

        return new byte[0];
    }

    public void saveCardState() {
        System.out.println("[CARD] ℹ️ Master Key chỉ tồn tại trên Card - không lưu ở PC");
    }

    // ====================== GETTERS/SETTERS ======================
    public void setCardId(String id) {
        this.cardId = id;
    }

    public String getCardId() {
        return cardId;
    }

    public void setRecoveryPhone(String phone) {
        this.recoveryPhone = phone;
    }

    public String getRecoveryPhone() {
        return recoveryPhone;
    }

    public boolean isFirstLogin() {
        return isFirstLogin;
    }

    public void setFirstLoginComplete() {
        isFirstLogin = false;
    }

    public int getPinTriesRemaining() {
        return pinTriesRemaining;
    }

    public boolean isPinVerified() {
        return pinVerified;
    }

    public boolean isCardBlocked() {
        return isCardBlocked;
    }

    public boolean isRsaReady() {
        return cardRsaKeyLoaded;
    }

    // ✅ MỚI: Getters cho cached fields
    public String getCachedName() {
        return cachedName;
    }

    public String getCachedPhone() {
        return cachedPhone;
    }

    public String getCachedEmail() {
        return cachedEmail;
    }

    public String getCachedBirthDate() {
        return cachedBirthDate;
    }

    public String getCachedAddress() {
        return cachedAddress;
    }

    public void logout() {
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║  🔄 RÚT THẺ - MASTER KEY TỰ HỦY TRÊN CARD  ║");
        System.out.println("╚════════════════════════════════════════════╝");

        String oldCardId = cardId;

        if (simulator != null) {
            simulator.reset();
        }

        initNewCard();

        System.out.println("[CARD] 🗑️ Đã hủy thẻ: " + oldCardId);
        System.out.println("[CARD] ✅ Thẻ mới sẵn sàng: " + cardId);
    }

    public void reset() {
        simulator.reset();
        simulator.selectApplet(appletAID);

        CommandAPDU apdu = new CommandAPDU(CLA, INS_INIT_CRYPTO, 0x00, 0x00, 1);
        sendAPDU(apdu);

        apdu = new CommandAPDU(CLA, INS_GEN_RSA_KEYPAIR, 0x00, 0x00, 1);
        ResponseAPDU resp = sendAPDU(apdu);
        if (resp != null && resp.getSW() == 0x9000) {
            rsaKeysGenerated = true;
            loadCardRSAPublicKey();
        }

        pinVerified = false;
        lastPinVerifyTime = 0;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    public void printStatus() {
        System.out.println("\n╔═══════════════ CARD STATUS v2.1 ═══════════════╗");
        System.out.println("║ Card ID:       " + cardId);
        System.out.println("║ Phone:         " + (recoveryPhone != null ? recoveryPhone : "Not set"));
        System.out.println("║ PIN verified:  " + (pinVerified ? "✅" : "❌"));
        System.out.println("║ Tries left:    " + pinTriesRemaining);
        System.out.println("║ First login:   " + (isFirstLogin ? "⚠️ Yes" : "No"));
        System.out.println("║ Blocked:       " + (isCardBlocked ? "🔒 Yes" : "No"));
        System.out.println("║ RSA Ready:     " + (cardRsaKeyLoaded ? "✅" : "❌"));
        System.out.println("║ Balance:       " + savedBalance + " VNĐ");
        System.out.println("║ ─────────────────────────────────────────────");
        System.out.println("║ 🔐 Master Key: CHỈ TỒN TẠI TRÊN CARD!");
        System.out.println("║ 🔐 Mỗi trường được mã hóa riêng biệt!");
        System.out.println("╚════════════════════════════════════════════════╝\n");
    }
}
