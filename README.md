# SmartCard Gym Management 💳

A high-security gym management system utilizing JavaCard technology for user data storage, balance management, and secure check-in.

 ✨ Key Features
- Applet Logic: Secure APDU command handling on chip memory.
- Advanced Security:
  - PIN-based Master Key derivation (KDF) using SHA-256.
  - Internal data encryption via AES-128 CBC.
  - Digital Signatures (RSA-1024) for transaction integrity.
- Image Processing: Custom compression algorithm to fit user avatars into <2KB chip memory.
- Management UI: Desktop client built with Java Swing for card administration.

## 🛠 Tech Stack
- Smart Card: JavaCard API, JCardSim (Simulator)
- Desktop: Java Swing, JDBC
- Database: MySQL
- Cryptography: RSA, AES, SHA-256

## 🚀 How to Run
1. Configure MySQL database using the provided `.sql` dump.
2. Load the JavaCard Applet into JCardSim.
3. Run the Java Swing desktop application.
4. Default PIN for testing: `123456`.
