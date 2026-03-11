package thegym;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.*;

public class thegym extends Applet {

    private static final byte INS_UNBLOCK_PIN = (byte) 0x12;

    private static final byte INS_VERIFY_PIN_SECURE = (byte) 0x13;
    private static final byte INS_CHANGE_PIN_SECURE = (byte) 0x14;
    private static final byte INS_SET_RECOVERY_PHONE = (byte) 0x15;

    private static final byte INS_UPDATE_INFO_ENCAPS = (byte) 0x24;
    private static final byte INS_GET_INFO_ENCAPS = (byte) 0x25;
    private static final byte INS_UPLOAD_AVATAR_ENCAPS = (byte) 0x26;
    private static final byte INS_GET_AVATAR_ENCAPS = (byte) 0x27;

    private static final byte INS_TOPUP = (byte) 0x30;
    private static final byte INS_PAYMENT = (byte) 0x31;
    private static final byte INS_CHECK_BALANCE = (byte) 0x32;
    private static final byte INS_GET_HISTORY = (byte) 0x40;
    private static final byte INS_INIT_CRYPTO = (byte) 0x50;
    private static final byte INS_GET_STATUS = (byte) 0x51;
    private static final byte INS_SET_SESSION_KEY = (byte) 0x52;
    private static final byte INS_GEN_RSA_KEYPAIR = (byte) 0x60;
    private static final byte INS_GET_RSA_PUBLIC = (byte) 0x61;
    private static final byte INS_RSA_ENCRYPT = (byte) 0x62;
    private static final byte INS_RSA_DECRYPT = (byte) 0x63;
    private static final byte INS_RSA_SIGN = (byte) 0x64;
    private static final byte INS_RSA_VERIFY = (byte) 0x65;
    private static final byte INS_HASH_DATA = (byte) 0x81;

    private static final byte PIN_MAX_TRIES = (byte) 5;
    private static final byte PIN_LENGTH = (byte) 6;
    private static final short MAX_INFO_SIZE = (short) 1024;
    private static final short MAX_AVATAR = (short) 10240;
    private static final short RSA_KEY_SIZE = (short) 1024;
    private static final short RSA_BLOCK_SIZE = (short) 128;
    private static final short AES_BLOCK_SIZE = (short) 16;
    private static final short SESSION_KEY_SIZE = (short) 16;
    private static final short IV_SIZE = (short) 16;
    private static final short SHA1_SIZE = (short) 20;

    private OwnerPIN pin;
    private byte[] pinBuffer;
    private byte[] recoverySDT;
    private byte recoverySDTLen;

    private byte[] userData;
    private short userDataLen;
    private byte[] avatarData;
    private short avatarCurrentSize;
    private short balance;
    private byte[] transactionHistory;
    private byte historyIndex;

    private MessageDigest sha1;
    private RandomData secureRandom;
    private Cipher aesCipher;
    private AESKey masterKey;
    private AESKey sessionAesKey;
    private KeyPair rsaKeyPair;
    private RSAPublicKey rsaPublicKey;
    private RSAPrivateKey rsaPrivateKey;
    private Cipher rsaCipher;
    private Signature rsaSignature;

    private byte[] tempBuffer;
    private byte[] tempBuffer2;
    private byte[] masterKeyBuffer;
    private byte[] masterIvBuffer;
    private byte[] sessionKeyBuffer;
    private byte[] sessionIvBuffer;
    private byte[] hashBuffer;

    private boolean cryptoInitialized;
    private boolean rsaKeysGenerated;
    private boolean masterKeyDerived;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        thegym applet = new thegym();
        applet.register();
    }

    private thegym() {
        pin = new OwnerPIN(PIN_MAX_TRIES, PIN_LENGTH);
        byte[] defaultPin = {'1', '2', '3', '4', '5', '6'};
        pin.update(defaultPin, (short) 0, PIN_LENGTH);

        pinBuffer = new byte[PIN_LENGTH];
        userData = new byte[MAX_INFO_SIZE];
        avatarData = new byte[MAX_AVATAR];
        recoverySDT = new byte[12];
        transactionHistory = new byte[20];

        tempBuffer = new byte[256];
        tempBuffer2 = new byte[256];
        masterKeyBuffer = new byte[16];
        masterIvBuffer = new byte[16];
        sessionKeyBuffer = new byte[16];
        sessionIvBuffer = new byte[16];
        hashBuffer = new byte[24];

        sha1 = null;
        secureRandom = null;
        aesCipher = null;
        masterKey = null;
        sessionAesKey = null;
        rsaKeyPair = null;
        rsaPublicKey = null;
        rsaPrivateKey = null;
        rsaCipher = null;
        rsaSignature = null;

        cryptoInitialized = false;
        rsaKeysGenerated = false;
        masterKeyDerived = false;

        userDataLen = 0;
        avatarCurrentSize = 0;
        recoverySDTLen = 0;
        balance = 0;
        historyIndex = 0;
    }

    private boolean initCrypto() {
        // Kiem tra neu da khoi tao roi thi tra ve true ngay lap tuc
        if (cryptoInitialized) {
            return true;
        }

        try {
            // Khoi tao cac doi tuong SHA1 Random va AES Cipher
            sha1 = MessageDigest.getInstance(MessageDigest.ALG_SHA, false);
            secureRandom = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
            aesCipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);

            // Cap phat bo nho cho Master Key va Session Key AES 128 bit
            masterKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
            sessionAesKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);

            // Khoi tao cap khoa RSA va cac doi tuong ma hoa ky so RSA
            rsaKeyPair = new KeyPair(KeyPair.ALG_RSA, RSA_KEY_SIZE);
            rsaCipher = Cipher.getInstance(Cipher.ALG_RSA_PKCS1, false);
            rsaSignature = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);

            // Danh dau co khoi tao thanh cong
            cryptoInitialized = true;
            return true;

        } catch (CryptoException e) {
            cryptoInitialized = false;
            return false;
        }
    }

    private void deriveMasterKeyFromPIN(byte[] pinData, short pinOffset, short pinLen) {
        // Bam SHA1 ma PIN lan 1 de sinh ra Master Key
        sha1.reset();
        sha1.update(pinData, pinOffset, pinLen);
        tempBuffer2[0] = (byte) 0x01;
        sha1.doFinal(tempBuffer2, (short) 0, (short) 1, hashBuffer, (short) 0);

        // Lay 16 byte dau tien cua hash lam khoa AES 128 bit
        Util.arrayCopy(hashBuffer, (short) 0, masterKeyBuffer, (short) 0, (short) 16);
        masterKey.setKey(masterKeyBuffer, (short) 0);

        // Bam SHA1 ma PIN lan 2 de sinh ra Master IV
        sha1.reset();
        sha1.update(pinData, pinOffset, pinLen);
        tempBuffer2[0] = (byte) 0x02;
        sha1.doFinal(tempBuffer2, (short) 0, (short) 1, hashBuffer, (short) 0);

        // Luu ket qua vao buffer IV va bat co da sinh khoa xong
        Util.arrayCopy(hashBuffer, (short) 0, masterIvBuffer, (short) 0, (short) 16);
        masterKeyDerived = true;

        // Xoa sach du lieu hash nhay cam trong buffer de bao mat
        Util.arrayFillNonAtomic(hashBuffer, (short) 0, (short) 20, (byte) 0x00);
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }

        byte[] buf = apdu.getBuffer();
        byte ins = buf[ISO7816.OFFSET_INS];

        if (pin.getTriesRemaining() == 0) {
            if (ins != INS_UNBLOCK_PIN && ins != INS_GET_STATUS && ins != INS_CHECK_BALANCE
                    && ins != INS_GET_RSA_PUBLIC && ins != INS_INIT_CRYPTO && ins != INS_GEN_RSA_KEYPAIR) {
                ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
            }
        }

        switch (ins) {

            case INS_VERIFY_PIN_SECURE:
                processVerifyPinSecure(apdu);
                break;
            case INS_CHANGE_PIN_SECURE:
                processChangePinSecure(apdu);
                break;
            case INS_SET_RECOVERY_PHONE:
                processSetRecoveryPhone(apdu);
                break;
            case INS_UNBLOCK_PIN:
                processUnblockPin(apdu);
                break;

            case INS_UPDATE_INFO_ENCAPS:
                processUpdateInfoEncaps(apdu);
                break;
            case INS_GET_INFO_ENCAPS:
                processGetInfoEncaps(apdu);
                break;

            case INS_UPLOAD_AVATAR_ENCAPS:
                processUploadAvatarEncaps(apdu);
                break;
            case INS_GET_AVATAR_ENCAPS:
                processGetAvatarEncaps(apdu);
                break;

            case INS_f:
                processTransaction(apdu, true);
                break;
            case INS_PAYMENT:
                processTransaction(apdu, false);
                break;
            case INS_CHECK_BALANCE:
                processCheckBalance(apdu);
                break;
            case INS_GET_HISTORY:
                processGetHistory(apdu);
                break;

            case INS_INIT_CRYPTO:
                processInitCrypto(apdu);
                break;
            case INS_GET_STATUS:
                processGetStatus(apdu);
                break;
            case INS_SET_SESSION_KEY:
                processSetSessionKey(apdu);
                break;
            case INS_GEN_RSA_KEYPAIR:
                processGenRSAKeyPair(apdu);
                break;
            case INS_GET_RSA_PUBLIC:
                processGetRSAPublic(apdu);
                break;

            case INS_RSA_ENCRYPT:
                processRSAEncrypt(apdu);
                break;
            case INS_RSA_DECRYPT:
                processRSADecrypt(apdu);
                break;
            case INS_RSA_SIGN:
                processRSASign(apdu);
                break;
            case INS_RSA_VERIFY:
                processRSAVerify(apdu);
                break;

            case INS_HASH_DATA:
                processHashData(apdu);
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void processGetStatus(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        buf[0] = (byte) (cryptoInitialized ? 1 : 0);
        buf[1] = (byte) (rsaKeysGenerated ? 1 : 0);
        buf[2] = (byte) (masterKeyDerived ? 1 : 0);
        buf[3] = (byte) (pin.isValidated() ? 1 : 0);
        buf[4] = pin.getTriesRemaining();
        buf[5] = (byte) 0x02;
        apdu.setOutgoingAndSend((short) 0, (short) 6);
    }

    private void processSetSessionKey(APDU apdu) {
        if (!rsaKeysGenerated) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        if (len != RSA_BLOCK_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        if (!decapsulateSessionKey(buf, ISO7816.OFFSET_CDATA)) {
            ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        }
    }

    private void processInitCrypto(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        buf[0] = (byte) (initCrypto() ? 0x01 : 0x00);
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    private void processVerifyPinSecure(APDU apdu) {
        // Kiem tra da sinh khoa RSA chua de co the giai ma PIN
        if (!rsaKeysGenerated) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        // Do dai PIN ma hoa phai dung bang RSA BLOCK SIZE
        if (len != RSA_BLOCK_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        // Giai ma khoi PIN bang Private Key RSA
        rsaCipher.init(rsaPrivateKey, Cipher.MODE_DECRYPT);
        short decLen = rsaCipher.doFinal(buf, ISO7816.OFFSET_CDATA, RSA_BLOCK_SIZE, tempBuffer, (short) 0);

        // Kiem tra do dai PIN sau khi giai ma co dung quy dinh khong
        if (decLen != PIN_LENGTH) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }

        // Copy PIN ra vung nho dem de kiem tra
        Util.arrayCopy(tempBuffer, (short) 0, pinBuffer, (short) 0, PIN_LENGTH);

        // Goi ham check PIN cua OwnerPIN va tra ve so lan thu con lai neu sai
        if (!pin.check(tempBuffer, (short) 0, PIN_LENGTH)) {
            Util.arrayFillNonAtomic(pinBuffer, (short) 0, PIN_LENGTH, (byte) 0x00);
            Util.arrayFillNonAtomic(tempBuffer, (short) 0, decLen, (byte) 0x00);
            short remaining = pin.getTriesRemaining();
            ISOException.throwIt((short) (0x63C0 + remaining));
        }

        // Neu dung PIN thi khoi tao he thong mat ma neu chua co
        if (!cryptoInitialized) {
            initCrypto();
        }
        // Sinh Master Key tu PIN vua nhap de giai ma du lieu
        deriveMasterKeyFromPIN(pinBuffer, (short) 0, PIN_LENGTH);

        // Xoa trang cac vung nho dem chua PIN de bao mat
        Util.arrayFillNonAtomic(pinBuffer, (short) 0, PIN_LENGTH, (byte) 0x00);
        Util.arrayFillNonAtomic(tempBuffer, (short) 0, decLen, (byte) 0x00);

        buf[0] = (byte) 0x01;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    private void processChangePinSecure(APDU apdu) {
        // Phai dang nhap thanh cong moi duoc doi PIN
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        if (!rsaKeysGenerated) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        if (len != RSA_BLOCK_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        // Giai ma goi tin chua PIN Cu va PIN Moi
        rsaCipher.init(rsaPrivateKey, Cipher.MODE_DECRYPT);
        short decLen = rsaCipher.doFinal(buf, ISO7816.OFFSET_CDATA, RSA_BLOCK_SIZE, tempBuffer, (short) 0);

        // Du lieu phai gom 6 byte PIN cu va 6 byte PIN moi
        if (decLen != (short) (PIN_LENGTH * 2)) {
            Util.arrayFillNonAtomic(tempBuffer, (short) 0, decLen, (byte) 0x00);
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }

        // Xac thuc lai PIN cu truoc khi cho phep doi
        if (!pin.check(tempBuffer, (short) 0, PIN_LENGTH)) {
            Util.arrayFillNonAtomic(tempBuffer, (short) 0, decLen, (byte) 0x00);
            short remaining = pin.getTriesRemaining();
            ISOException.throwIt((short) (0x63C0 + remaining));
        }

        // Backup Master Key cu ra vung nho RAM tam thoi
        byte[] oldMasterKeyBuffer = JCSystem.makeTransientByteArray((short) 16, JCSystem.CLEAR_ON_DESELECT);
        byte[] oldMasterIvBuffer = JCSystem.makeTransientByteArray((short) 16, JCSystem.CLEAR_ON_DESELECT);

        Util.arrayCopy(masterKeyBuffer, (short) 0, oldMasterKeyBuffer, (short) 0, (short) 16);
        Util.arrayCopy(masterIvBuffer, (short) 0, oldMasterIvBuffer, (short) 0, (short) 16);

        // Tao doi tuong Key tam thoi cho khoa cu
        AESKey oldMasterKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES_TRANSIENT_DESELECT, KeyBuilder.LENGTH_AES_128, false);
        oldMasterKey.setKey(oldMasterKeyBuffer, (short) 0);

        // Cap nhat PIN moi vao he thong
        pin.update(tempBuffer, PIN_LENGTH, PIN_LENGTH);

        // Sinh Master Key moi tu PIN moi
        deriveMasterKeyFromPIN(tempBuffer, PIN_LENGTH, PIN_LENGTH);

        // Tai ma hoa du lieu UserData bang khoa moi neu co
        if (userDataLen > 0) {
            reEncryptData(userData, userDataLen, oldMasterKey, oldMasterIvBuffer);
        }

        // Tai ma hoa Avatar bang khoa moi neu co
        if (avatarCurrentSize > 0) {
            reEncryptAvatar(oldMasterKey, oldMasterIvBuffer);
        }

        // Xoa sach du lieu nhay cam trong RAM
        Util.arrayFillNonAtomic(tempBuffer, (short) 0, decLen, (byte) 0x00);
        Util.arrayFillNonAtomic(oldMasterKeyBuffer, (short) 0, (short) 16, (byte) 0x00);
        Util.arrayFillNonAtomic(oldMasterIvBuffer, (short) 0, (short) 16, (byte) 0x00);

        buf[0] = (byte) 0x01;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    private void processSetRecoveryPhone(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        if (len < 10 || len > 12) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        Util.arrayCopy(buf, ISO7816.OFFSET_CDATA, recoverySDT, (short) 0, len);
        recoverySDTLen = (byte) len;
    }

    private void reEncryptData(byte[] data, short dataLen, AESKey oldKey, byte[] oldIv) {
        // Giai ma du lieu hien tai bang Key cu va IV cu
        aesCipher.init(oldKey, Cipher.MODE_DECRYPT, oldIv, (short) 0, (short) 16);
        short decLen = aesCipher.doFinal(data, (short) 0, dataLen, tempBuffer, (short) 0);

        // Loai bo padding PKCS7 cu de lay do dai thuc te
        byte padValue = tempBuffer[(short) (decLen - 1)];
        if (padValue > 0 && padValue <= AES_BLOCK_SIZE) {
            decLen -= padValue;
        }

        // Tinh toan va them padding moi cho lan ma hoa tiep theo
        short paddedLen = (short) (((decLen + AES_BLOCK_SIZE) / AES_BLOCK_SIZE) * AES_BLOCK_SIZE);
        byte newPadValue = (byte) (paddedLen - decLen);
        for (short i = decLen; i < paddedLen; i++) {
            tempBuffer[i] = newPadValue;
        }

        // Ma hoa lai du lieu bang Master Key moi va ghi de vao mang goc
        aesCipher.init(masterKey, Cipher.MODE_ENCRYPT, masterIvBuffer, (short) 0, (short) 16);
        short encLen = aesCipher.doFinal(tempBuffer, (short) 0, paddedLen, data, (short) 0);
    }

    private void reEncryptAvatar(AESKey oldKey, byte[] oldIv) {
        // Giai ma toan bo Avatar tu EEPROM ra buffer tam bang Key cu
        aesCipher.init(oldKey, Cipher.MODE_DECRYPT, oldIv, (short) 0, (short) 16);
        short decLen = aesCipher.doFinal(avatarData, (short) 0, avatarCurrentSize, tempBuffer, (short) 0);

        // Xoa padding cu de lay kich thuoc anh goc
        byte padValue = tempBuffer[(short) (decLen - 1)];
        if (padValue > 0 && padValue <= AES_BLOCK_SIZE) {
            decLen -= padValue;
        }

        // Them padding moi vao buffer tam
        short paddedLen = (short) (((decLen + AES_BLOCK_SIZE) / AES_BLOCK_SIZE) * AES_BLOCK_SIZE);
        byte newPadValue = (byte) (paddedLen - decLen);
        for (short i = decLen; i < paddedLen; i++) {
            tempBuffer[i] = newPadValue;
        }

        // Ma hoa lai Avatar bang Master Key moi va cap nhat kich thuoc file
        aesCipher.init(masterKey, Cipher.MODE_ENCRYPT, masterIvBuffer, (short) 0, (short) 16);
        short encLen = aesCipher.doFinal(tempBuffer, (short) 0, paddedLen, avatarData, (short) 0);
        avatarCurrentSize = encLen;
    }

    private void processUnblockPin(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        // Kiem tra da thiet lap SDT khoi phuc chua
        if (recoverySDTLen == 0) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        if (len != recoverySDTLen) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }

        // So sanh SDT gui len voi SDT da luu
        if (Util.arrayCompare(buf, ISO7816.OFFSET_CDATA, recoverySDT, (short) 0, len) != 0) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }

        // Reset so lan thu va dat lai PIN ve mac dinh 123456
        pin.resetAndUnblock();
        byte[] defaultPin = {'1', '2', '3', '4', '5', '6'};
        pin.update(defaultPin, (short) 0, PIN_LENGTH);

        // Huy bo trang thai Master Key vi PIN da bi reset
        masterKeyDerived = false;
        Util.arrayFillNonAtomic(masterKeyBuffer, (short) 0, (short) 16, (byte) 0x00);
        Util.arrayFillNonAtomic(masterIvBuffer, (short) 0, (short) 16, (byte) 0x00);
    }

    private void processGenRSAKeyPair(APDU apdu) {

        if (!cryptoInitialized && !initCrypto()) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        try {

            rsaKeyPair.genKeyPair();

            rsaPublicKey = (RSAPublicKey) rsaKeyPair.getPublic();

            rsaPrivateKey = (RSAPrivateKey) rsaKeyPair.getPrivate();

            rsaKeysGenerated = true;

            byte[] buf = apdu.getBuffer();

            buf[0] = (byte) 0x01;

            apdu.setOutgoingAndSend((short) 0, (short) 1);

        } catch (CryptoException e) {

            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
    }

    private void processGetRSAPublic(APDU apdu) {

        if (!rsaKeysGenerated) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        byte[] buf = apdu.getBuffer();

        byte p1 = buf[ISO7816.OFFSET_P1];

        short len = 0;

        if (p1 == 0x01) {

            len = rsaPublicKey.getModulus(buf, (short) 0);

        } else if (p1 == 0x02) {

            len = rsaPublicKey.getExponent(buf, (short) 0);

        } else {

            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }

        apdu.setOutgoingAndSend((short) 0, len);
    }

    private void processRSADecrypt(APDU apdu) {
        // Kiem tra quyen va giai ma du lieu gui len bang RSA Private Key
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        if (!rsaKeysGenerated) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        rsaCipher.init(rsaPrivateKey, Cipher.MODE_DECRYPT);
        short decLen = rsaCipher.doFinal(buf, ISO7816.OFFSET_CDATA, len, tempBuffer, (short) 0);

        Util.arrayCopy(tempBuffer, (short) 0, buf, (short) 0, decLen);
        apdu.setOutgoingAndSend((short) 0, decLen);
    }

    private void processRSASign(APDU apdu) {
        // Ky so len du lieu bang RSA Private Key de xac thuc nguoi gui
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        if (!rsaKeysGenerated) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        rsaSignature.init(rsaPrivateKey, Signature.MODE_SIGN);

        short sigLen = rsaSignature.sign(buf, ISO7816.OFFSET_CDATA, len, tempBuffer, (short) 0);

        Util.arrayCopy(tempBuffer, (short) 0, buf, (short) 0, sigLen);
        apdu.setOutgoingAndSend((short) 0, sigLen);
    }

    private void processRSAVerify(APDU apdu) {
        // Xac thuc chu ky RSA bang Public Key voi du lieu dau vao gom Data va Signature
        if (!rsaKeysGenerated) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        // Cat du lieu dau vao thanh phan Data goc va phan Chu ky de xac thuc
        short dataLen = Util.getShort(buf, ISO7816.OFFSET_CDATA);

        short sigOffset = (short) (ISO7816.OFFSET_CDATA + 2 + dataLen);

        short sigLen = (short) (len - 2 - dataLen);

        rsaSignature.init(rsaPublicKey, Signature.MODE_VERIFY);

        boolean valid = rsaSignature.verify(
                buf, (short) (ISO7816.OFFSET_CDATA + 2), dataLen,
                buf, sigOffset, sigLen
        );

        buf[0] = (byte) (valid ? 0x01 : 0x00);
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    private boolean decapsulateSessionKey(byte[] rsaEncrypted, short offset) {
        if (!rsaKeysGenerated) {
            return false;
        }

        try {
            // Giai ma goi tin chua Session Key bang RSA Private Key
            rsaCipher.init(rsaPrivateKey, Cipher.MODE_DECRYPT);
            short decLen = rsaCipher.doFinal(rsaEncrypted, offset, RSA_BLOCK_SIZE, tempBuffer2, (short) 0);

            // Kiem tra ket qua giai ma phai du 32 byte gom Key va IV
            if (decLen != (short) (SESSION_KEY_SIZE + IV_SIZE)) {
                return false;
            }

            // Tach lay Session Key va IV luu vao bo nho RAM
            Util.arrayCopy(tempBuffer2, (short) 0, sessionKeyBuffer, (short) 0, SESSION_KEY_SIZE);
            Util.arrayCopy(tempBuffer2, SESSION_KEY_SIZE, sessionIvBuffer, (short) 0, IV_SIZE);

            // Thiet lap doi tuong AES de su dung cho cac tac vu sau
            sessionAesKey.setKey(sessionKeyBuffer, (short) 0);

            return true;

        } catch (CryptoException e) {
            return false;
        }
    }

    private void processUpdateInfoEncaps(APDU apdu) {
        // Kiem tra quyen truy cap va trang thai Master Key
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        if (!masterKeyDerived) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        // Giai ma goi tin van chuyen bang Session Key
        aesCipher.init(sessionAesKey, Cipher.MODE_DECRYPT, sessionIvBuffer, (short) 0, IV_SIZE);
        short plainLen = aesCipher.doFinal(buf, ISO7816.OFFSET_CDATA, len, tempBuffer, (short) 0);

        // Loai bo padding cua lop van chuyen
        byte padValue = tempBuffer[(short) (plainLen - 1)];
        if (padValue > 0 && padValue <= AES_BLOCK_SIZE) {
            plainLen -= padValue;
        }

        // Chuan bi ghi du lieu moi vao EEPROM
        short readOffset = 0;
        short writeOffset = 0;
        byte fieldCount = tempBuffer[readOffset++];

        Util.arrayFillNonAtomic(userData, (short) 0, MAX_INFO_SIZE, (byte) 0x00);
        userData[writeOffset++] = fieldCount;

        // Duyet qua tung truong du lieu va ma hoa lai bang Master Key de luu tru
        for (byte i = 0; i < fieldCount; i++) {
            byte type = tempBuffer[readOffset++];
            short valLen = Util.getShort(tempBuffer, readOffset);
            readOffset += 2;

            // Copy du lieu ra buffer tam de them padding cho lop luu tru
            Util.arrayCopy(tempBuffer, readOffset, tempBuffer2, (short) 0, valLen);
            short paddedLen = (short) (((valLen + AES_BLOCK_SIZE) / AES_BLOCK_SIZE) * AES_BLOCK_SIZE);
            byte masterPad = (byte) (paddedLen - valLen);

            for (short k = valLen; k < paddedLen; k++) {
                tempBuffer2[k] = masterPad;
            }

            // Ghi header va ma hoa du lieu vao truc tiep EEPROM
            userData[writeOffset++] = type;
            Util.setShort(userData, writeOffset, paddedLen);
            writeOffset += 2;

            aesCipher.init(masterKey, Cipher.MODE_ENCRYPT, masterIvBuffer, (short) 0, (short) 16);
            aesCipher.doFinal(tempBuffer2, (short) 0, paddedLen, userData, writeOffset);

            writeOffset += paddedLen;
            readOffset += valLen;
        }

        userDataLen = writeOffset;

        // Xoa sach cac buffer tam thoi chua du lieu nhay cam
        Util.arrayFillNonAtomic(tempBuffer, (short) 0, plainLen, (byte) 0x00);
        Util.arrayFillNonAtomic(tempBuffer2, (short) 0, (short) 256, (byte) 0x00);

        buf[0] = (byte) 0x01;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    private void processGetInfoEncaps(APDU apdu) {
        // Kiem tra an toan va dam bao da co du lieu
        if (!pin.isValidated() || !masterKeyDerived) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        if (userDataLen == 0) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        short readOffset = 0;
        short writeOffset = 0;

        // Doc so luong truong du lieu
        byte fieldCount = userData[readOffset++];
        tempBuffer[writeOffset++] = fieldCount;

        // Giai ma tung truong du lieu tu EEPROM ra buffer tam
        for (byte i = 0; i < fieldCount; i++) {
            byte type = userData[readOffset++];
            short encLen = Util.getShort(userData, readOffset);
            readOffset += 2;

            // Giai ma bang Master Key
            aesCipher.init(masterKey, Cipher.MODE_DECRYPT, masterIvBuffer, (short) 0, (short) 16);
            short plainLen = aesCipher.doFinal(userData, readOffset, encLen, tempBuffer2, (short) 0);

            // Loai bo padding cua lop luu tru
            byte padValue = tempBuffer2[(short) (plainLen - 1)];
            if (padValue > 0 && padValue <= AES_BLOCK_SIZE) {
                plainLen -= padValue;
            }

            // Dong goi lai dang TLV vao buffer gui di
            tempBuffer[writeOffset++] = type;
            Util.setShort(tempBuffer, writeOffset, plainLen);
            writeOffset += 2;
            Util.arrayCopy(tempBuffer2, (short) 0, tempBuffer, writeOffset, plainLen);
            writeOffset += plainLen;

            readOffset += encLen;
        }

        // Ma hoa toan bo goi tin bang Session Key de gui ve Host
        byte[] buf = apdu.getBuffer();
        short totalPlainLen = writeOffset;
        short sessionPaddedLen = (short) (((totalPlainLen + AES_BLOCK_SIZE) / AES_BLOCK_SIZE) * AES_BLOCK_SIZE);
        byte sessionPad = (byte) (sessionPaddedLen - totalPlainLen);

        for (short k = totalPlainLen; k < sessionPaddedLen; k++) {
            tempBuffer[k] = sessionPad;
        }

        aesCipher.init(sessionAesKey, Cipher.MODE_ENCRYPT, sessionIvBuffer, (short) 0, IV_SIZE);
        short finalLen = aesCipher.doFinal(tempBuffer, (short) 0, sessionPaddedLen, buf, (short) 0);

        apdu.setOutgoingAndSend((short) 0, finalLen);
    }

    private void processUploadAvatarEncaps(APDU apdu) {
        // Kiem tra quyen truy cap
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        if (!rsaKeysGenerated || !masterKeyDerived) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        byte[] buf = apdu.getBuffer();
        byte p1 = buf[ISO7816.OFFSET_P1];
        byte p2 = buf[ISO7816.OFFSET_P2];

        // Tinh toan offset va kiem tra co phai chunk cuoi cung khong
        short offset = (short) (((p1 & 0x7F) << 8) | (p2 & 0xFF));
        boolean isFinalChunk = ((p1 & 0x80) != 0);
        short len = apdu.setIncomingAndReceive();

        // Xu ly chunk dau tien: Gom ca RSA Session Key va du lieu anh
        if (offset == 0) {
            if (len < RSA_BLOCK_SIZE) {
                ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            }
            // Giai ma Session Key bang RSA Private Key
            if (!decapsulateSessionKey(buf, ISO7816.OFFSET_CDATA)) {
                ISOException.throwIt(ISO7816.SW_DATA_INVALID);
            }

            avatarCurrentSize = 0;
            short dataOffset = (short) (ISO7816.OFFSET_CDATA + RSA_BLOCK_SIZE);
            short dataLen = (short) (len - RSA_BLOCK_SIZE);

            // Giai ma phan du lieu anh bang Session Key va luu vao bo nho
            if (dataLen > 0) {
                if (dataLen % AES_BLOCK_SIZE != 0) {
                    ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                }
                aesCipher.init(sessionAesKey, Cipher.MODE_DECRYPT, sessionIvBuffer, (short) 0, IV_SIZE);
                short decLen = aesCipher.doFinal(buf, dataOffset, dataLen, tempBuffer, (short) 0);

                if (decLen > MAX_AVATAR) {
                    ISOException.throwIt(ISO7816.SW_FILE_FULL);
                }
                Util.arrayCopy(tempBuffer, (short) 0, avatarData, (short) 0, decLen);
                avatarCurrentSize = decLen;
            }

        } else {
            // Xu ly cac chunk tiep theo: Giai ma AES va noi tiep vao bo nho
            if (len > 0) {
                if (len % AES_BLOCK_SIZE != 0) {
                    ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                }
                aesCipher.init(sessionAesKey, Cipher.MODE_DECRYPT, sessionIvBuffer, (short) 0, IV_SIZE);
                short decLen = aesCipher.doFinal(buf, ISO7816.OFFSET_CDATA, len, tempBuffer, (short) 0);

                if ((short) (avatarCurrentSize + decLen) > MAX_AVATAR) {
                    ISOException.throwIt(ISO7816.SW_FILE_FULL);
                }
                Util.arrayCopy(tempBuffer, (short) 0, avatarData, avatarCurrentSize, decLen);
                avatarCurrentSize += decLen;
            }
        }

        // Neu la chunk cuoi thi thuc hien ma hoa lai toan bo bang Master Key
        if (isFinalChunk && avatarCurrentSize > 0) {
            // Xoa padding cua lop Session Key
            byte padValue = avatarData[(short) (avatarCurrentSize - 1)];
            if (padValue > 0 && padValue <= AES_BLOCK_SIZE) {
                boolean validPad = true;
                for (short i = (short) (avatarCurrentSize - padValue); i < avatarCurrentSize; i++) {
                    if (avatarData[i] != padValue) {
                        validPad = false;
                        break;
                    }
                }
                if (validPad) {
                    avatarCurrentSize -= padValue;
                }
            }

            // Them padding va ma hoa lai bang Master Key de luu tru lau dai
            short plainLen = avatarCurrentSize;
            short paddedLen = (short) (((plainLen + AES_BLOCK_SIZE) / AES_BLOCK_SIZE) * AES_BLOCK_SIZE);
            byte masterPadValue = (byte) (paddedLen - plainLen);

            Util.arrayCopy(avatarData, (short) 0, tempBuffer, (short) 0, plainLen);
            for (short i = plainLen; i < paddedLen; i++) {
                tempBuffer[i] = masterPadValue;
            }

            aesCipher.init(masterKey, Cipher.MODE_ENCRYPT, masterIvBuffer, (short) 0, (short) 16);
            short encLen = aesCipher.doFinal(tempBuffer, (short) 0, paddedLen, avatarData, (short) 0);
            avatarCurrentSize = encLen;

            // Tra ve kich thuoc that cua anh cho Host
            buf[0] = (byte) ((plainLen >> 8) & 0xFF);
            buf[1] = (byte) (plainLen & 0xFF);
            apdu.setOutgoingAndSend((short) 0, (short) 2);
            return;
        }
    }

    private void processGetAvatarEncaps(APDU apdu) {
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        if (!rsaKeysGenerated || !masterKeyDerived) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        byte[] buf = apdu.getBuffer();
        byte p1 = buf[ISO7816.OFFSET_P1];
        byte p2 = buf[ISO7816.OFFSET_P2];
        short len = apdu.setIncomingAndReceive();

        if (avatarCurrentSize == 0) {
            buf[0] = 0;
            buf[1] = 0;
            apdu.setOutgoingAndSend((short) 0, (short) 2);
            return;
        }

        // Yeu cau dau tien (Offset 0): Thiet lap Session Key va chuan bi du lieu
        if (p1 == 0 && p2 == 0) {
            if (len != RSA_BLOCK_SIZE) {
                ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            }
            if (!decapsulateSessionKey(buf, ISO7816.OFFSET_CDATA)) {
                ISOException.throwIt(ISO7816.SW_DATA_INVALID);
            }

            // Giai ma toan bo anh bang Master Key ra Plaintext
            aesCipher.init(masterKey, Cipher.MODE_DECRYPT, masterIvBuffer, (short) 0, (short) 16);
            short decLen = aesCipher.doFinal(avatarData, (short) 0, avatarCurrentSize, tempBuffer, (short) 0);

            byte padValue = tempBuffer[(short) (decLen - 1)];
            if (padValue > 0 && padValue <= AES_BLOCK_SIZE) {
                decLen -= padValue;
            }

            // Luu kich thuoc that va co hieu vao hashBuffer
            hashBuffer[0] = (byte) ((decLen >> 8) & 0xFF);
            hashBuffer[1] = (byte) (decLen & 0xFF);
            hashBuffer[2] = (byte) 0x01;

            // Ghi tam Plaintext vao EEPROM de doc dan cac chunk sau
            Util.arrayCopy(tempBuffer, (short) 0, avatarData, (short) 0, decLen);

            buf[0] = hashBuffer[0];
            buf[1] = hashBuffer[1];
            apdu.setOutgoingAndSend((short) 0, (short) 2);
            return;

        } else {
            // Cac yeu cau sau: Doc tung phan du lieu va ma hoa gui di
            if (hashBuffer[2] != (byte) 0x01) {
                ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
            }

            short offset = (short) ((p1 << 8) | (p2 & 0xFF));
            short plaintextSize = (short) (((hashBuffer[0] & 0xFF) << 8) | (hashBuffer[1] & 0xFF));

            // Neu da doc het thi ma hoa lai bo nho de bao mat
            if (offset >= plaintextSize) {
                reEncryptAvatarWithMasterKey(plaintextSize);
                apdu.setOutgoingAndSend((short) 0, (short) 0);
                return;
            }

            // Cat nho du lieu va ma hoa bang Session Key
            short remaining = (short) (plaintextSize - offset);
            short chunkSize = (remaining > 100) ? 100 : remaining;

            Util.arrayCopy(avatarData, offset, tempBuffer, (short) 0, chunkSize);

            short paddedLen = (short) (((chunkSize + AES_BLOCK_SIZE) / AES_BLOCK_SIZE) * AES_BLOCK_SIZE);
            byte padValue = (byte) (paddedLen - chunkSize);
            for (short i = chunkSize; i < paddedLen; i++) {
                tempBuffer[i] = padValue;
            }

            aesCipher.init(sessionAesKey, Cipher.MODE_ENCRYPT, sessionIvBuffer, (short) 0, IV_SIZE);
            short encLen = aesCipher.doFinal(tempBuffer, (short) 0, paddedLen, buf, (short) 0);

            apdu.setOutgoingAndSend((short) 0, encLen);
        }
    }

    private void reEncryptAvatarWithMasterKey(short plaintextSize) {
        if (plaintextSize <= 0) {
            return;
        }

        Util.arrayCopy(avatarData, (short) 0, tempBuffer, (short) 0, plaintextSize);

        short paddedLen = (short) (((plaintextSize + AES_BLOCK_SIZE) / AES_BLOCK_SIZE) * AES_BLOCK_SIZE);
        byte padValue = (byte) (paddedLen - plaintextSize);
        for (short i = plaintextSize; i < paddedLen; i++) {
            tempBuffer[i] = padValue;
        }

        aesCipher.init(masterKey, Cipher.MODE_ENCRYPT, masterIvBuffer, (short) 0, (short) 16);
        short encLen = aesCipher.doFinal(tempBuffer, (short) 0, paddedLen, avatarData, (short) 0);
        avatarCurrentSize = encLen;

        hashBuffer[2] = (byte) 0x00;
    }

    private void processHashData(APDU apdu) {
        if (!cryptoInitialized && !initCrypto()) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        sha1.reset();
        short hashLen = sha1.doFinal(buf, ISO7816.OFFSET_CDATA, len, tempBuffer, (short) 0);

        Util.arrayCopy(tempBuffer, (short) 0, buf, (short) 0, hashLen);
        apdu.setOutgoingAndSend((short) 0, hashLen);
    }

    private void processTransaction(APDU apdu, boolean isTopUp) {
        // Kiem tra quyen truy cap truoc khi giao dich
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        byte[] buf = apdu.getBuffer();
        short amount = (short) (buf[ISO7816.OFFSET_P1] & 0xFF);

        // Lay so tien giao dich tu tham so P1 va kiem tra hop le
        if (amount == 0) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }

        byte txType;

        // Bat dau transaction de dam bao an toan du lieu
        JCSystem.beginTransaction();

        // Xu ly cong tien hoac tru tien kiem tra so du va ghi lai loai giao dich
        if (isTopUp) {
            balance = (short) (balance + amount);
            txType = (byte) 0x01;
        } else {
            if (balance < amount) {
                JCSystem.abortTransaction();
                ISOException.throwIt((short) 0x6901);
            }
            balance = (short) (balance - amount);
            txType = (byte) 0x02;
        }

        // Ghi nhat ky, commit du lieu va tra ve so du moi cho Host
        logTransaction(txType, amount);
        JCSystem.commitTransaction();

        Util.setShort(buf, (short) 0, balance);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }

    private void logTransaction(byte type, short amount) {
        short off = (short) (historyIndex * 4);

        transactionHistory[off] = type;
        Util.setShort(transactionHistory, (short) (off + 1), amount);
        transactionHistory[(short) (off + 3)] = (byte) 0xFF;

        historyIndex++;
        if (historyIndex >= 5) {
            historyIndex = 0;
        }
    }

    private void processCheckBalance(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        Util.setShort(buf, (short) 0, balance);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }

    private void processGetHistory(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        Util.arrayCopy(transactionHistory, (short) 0, buf, (short) 0, (short) 20);
        apdu.setOutgoingAndSend((short) 0, (short) 20);

    }
