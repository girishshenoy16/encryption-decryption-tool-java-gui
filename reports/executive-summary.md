# Executive Summary — Encryption Decryption Tool

## Overview

The Encryption Decryption Tool is a Java 17 desktop application that provides secure, authenticated encryption and decryption of text and files. It implements industry-standard cryptographic practices (AES-256-GCM, PBKDF2-HMAC-SHA-256) in a user-friendly Swing interface, along with a modern GitHub Pages website featuring an in-browser crypto playground.

## Objectives

- Build a secure, educational desktop application demonstrating modern cryptographic practices
- Implement AES-256-GCM authenticated encryption with PBKDF2 key derivation
- Provide a clean, responsive GUI for text and file encryption workflows
- Deploy a portfolio-ready website with documentation and an interactive demo
- Achieve production-quality code with comprehensive testing and documentation

## Key Deliverables

| Component | Description | Status |
|-----------|-------------|--------|
| **Java Desktop App** | Swing GUI with text & file encryption/decryption | Complete |
| **Crypto Engine** | AES-256-GCM + PBKDF2-HMAC-SHA-256 (600K iterations) | Complete |
| **File Service** | Streamed file I/O with temp files and atomic moves | Complete |
| **Test Suite** | 14 JUnit 5 tests (8 crypto + 6 file service) | Passing |
| **GitHub Pages Site** | Responsive frontend with light/dark theme | Deployed |
| **Online Playground** | Client-side Web Crypto API encrypt/decrypt | Working |
| **Documentation** | README, project report, this summary | Complete |
| **License** | MIT License | Complete |

## Technical Specifications

| Aspect | Value |
|--------|-------|
| Language | Java 17 |
| Build | Maven 3.9+ |
| GUI | Swing |
| Cipher | AES-256-GCM |
| KDF | PBKDF2-HMAC-SHA-256, 600,000 iterations |
| Salt | 16 bytes, cryptographically random |
| IV | 12 bytes, cryptographically random |
| Auth Tag | 128-bit GCM |
| Min Password | 12 characters |
| Payload Format | `EDT1 \| version \| salt(16) \| iv(12) \| ciphertext+tag` |
| Tests | 14 passing (JUnit 5) |
| Frontend | HTML5, CSS3, Vanilla JavaScript |
| Crypto (Browser) | Web Crypto API (AES-GCM + PBKDF2) |

## Architecture

The application follows a modular 6-package architecture:

- **`main`** — Application entry point, EDT launcher
- **`gui`** — Swing window, user interactions, SwingWorker background processing
- **`crypto`** — AES-256-GCM encryption/decryption, PBKDF2 key derivation, payload parsing
- **`service`** — File encryption/decryption, temp file handling, atomic moves
- **`utility`** — Input validation (text, passwords, files, safe names)
- **`exception`** — Typed exception hierarchy (5 exception classes)

## Testing Results

```
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Test coverage includes:
- Text round-trip (including Unicode/Devanagari)
- Wrong password rejection (no plaintext leaked)
- Tampered payload detection
- Invalid Base64/malformed payload handling
- File round-trip byte equality
- Missing file/directory rejection
- Corrupt encrypted file handling
- Overwrite approve/reject
- No partial output after failure

## GitHub Pages Website

The frontend website (`docs/`) provides:
- Hero section with project overview
- Features and security documentation
- Architecture visualization
- Screenshots gallery with lightbox
- Demo video player
- "Try It Online" browser-based crypto playground
- Light/dark theme with system preference detection
- Responsive design for all screen sizes

**Live URL:** `https://girishshenoy16.github.io/encryption-decryption-tool-java-gui/`

## Limitations

- No password recovery (by design)
- Single-user, local-only operation
- No batch file processing
- Educational/defensive use only
- Java 17+ required
- Memory-limited file size handling

## Conclusion

The project successfully demonstrates modern cryptographic practices in a real-world Java desktop application. It achieves all planned objectives: secure AES-256-GCM encryption, comprehensive testing, professional documentation, and a portfolio-ready website with an interactive crypto playground. The modular architecture ensures maintainability, and the security model follows industry best practices.
