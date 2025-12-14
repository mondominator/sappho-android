# Privacy Policy for Sappho Audiobooks

**Last Updated: December 14, 2024**

## Overview

Sappho Audiobooks is a self-hosted audiobook player application. This privacy policy explains how the app handles your data.

## Data Collection

### What We Do NOT Collect

The Sappho Audiobooks app does **not**:
- Collect any analytics or usage data
- Track your location
- Access your contacts, camera, or microphone
- Send data to third-party services
- Include any advertising or tracking SDKs
- Store any data on our servers

### Data Stored Locally on Your Device

The app stores the following information locally on your device using encrypted storage:

- **Server URL**: The address of your self-hosted Sappho server
- **Authentication Token**: Used to authenticate with your server
- **Username and Display Name**: Cached locally for display purposes

This data is stored using Android's EncryptedSharedPreferences with AES-256 encryption and never leaves your device except to communicate with your own server.

## Self-Hosted Architecture

Sappho Audiobooks is designed to work with a self-hosted server that you control. This means:

- **Your audiobook library** is stored on your own server
- **Your listening progress** is synced to your own server
- **Your account credentials** are managed by your own server
- **All data** remains under your control

The app developer has no access to your server, your audiobooks, or your listening data.

## Network Communications

The app communicates only with the server URL you provide during login. All communications use the authentication token you receive when logging in. We recommend using HTTPS for secure communication with your server.

## Data Sharing

We do not share any data with third parties because we do not collect any data. Your data stays between your device and your self-hosted server.

## Data Deletion

You can delete all locally stored data by:
1. Logging out of the app (clears authentication data)
2. Clearing the app's data in Android Settings
3. Uninstalling the app

Data stored on your self-hosted server is managed by you through your server administration.

## Children's Privacy

The app does not knowingly collect any personal information. Since this is a self-hosted solution, any user accounts and content are managed by the server operator.

## Changes to This Policy

We may update this privacy policy from time to time. Any changes will be reflected in the "Last Updated" date above.

## Open Source

Sappho Audiobooks is open source software. You can review the source code to verify our privacy practices:
- Android App: https://github.com/mondominator/sappho-android
- Server: https://github.com/mondominator/sappho

## Contact

If you have questions about this privacy policy, please open an issue on our GitHub repository.
