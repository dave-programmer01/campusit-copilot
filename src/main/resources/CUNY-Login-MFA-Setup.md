# Setting Up CUNY Login MFA

This one-time procedure sets up CUNY Login MFA using the Microsoft Authenticator mobile app. CUNY Login MFA is used to access CUNYfirst, CUNYbuy, Brightspace, Navigate, Zoom, Campus VPN, Lehman 360, and Lehman Electronic Forms (ePAF, ePRF, iDeclare, etc.).

**IMPORTANT — this is NOT the same as Microsoft 365 MFA.** CUNY Login MFA is separate from Microsoft 365 (M365) MFA, which is used only for Microsoft 365 apps like Outlook and Teams. Do not confuse the two.

If you already set up MFA for VPN access, you can continue using your existing One-Time Password (OTP) method.

## Before You Begin

1. On your mobile device, download and install the **Microsoft Authenticator App** from the App Store or Play Store.
   - Most CUNY students already have Microsoft Authenticator installed for Microsoft 365. Google Authenticator or Oracle Mobile Authenticator also work, but do NOT install any other third-party authenticator app.
   - If possible, set up MFA on more than one device (e.g. phone and tablet) as backup in case one is lost or replaced.

## Procedure

2. Open the Microsoft Authenticator App on your mobile device and pause — you'll return to it later.

3. In your browser, go to the CUNY Login MFA Self-Service portal: **https://ssologin.cuny.edu/oaa/rui**
   - If you get a login error in a regular browser window, try a private/incognito window instead.
   - **CRITICAL:** If you visit the CUNY Login MFA Self-Service page more than **three times without successfully setting up at least one MFA factor, your account will be locked**, and you'll need to contact the Help Desk. Do not keep retrying blindly.

4. At the CUNY Login page, enter your **CUNY Login username and password**, then click Login.

5. Click **OK** on the next page.

6. Click **Allow** on the next page.

7. Under **My Authentication Factors**, click **Manage**.

8. Click **Add Authentication Factor**, then click **Mobile Authenticator - TOTP**.

9. Enter a **Friendly Name**, for example `CUNY_Login_MFA`. Use a unique name to avoid confusion with other MFA accounts.

10. A QR Code appears on the page. Click **Verify Now**.

    Now return to the Microsoft Authenticator App on your mobile device.

11. In the Microsoft Authenticator App, tap the **"+"** and choose **Work or school account**.

12. Tap **Scan QR Code**. If your device asks for camera access, tap **Allow**.

13. Point your phone at your computer screen to scan the QR Code. When successful, a new entry appears in the app showing a 6-digit code and your Friendly Name. Enter that 6-digit code in the **Verification Code** field on your computer and click **Verify and Save**.
    - Note: the 6-digit code regenerates every 30 seconds.

14. You'll return to the **My Authentication Factors** page and see an **Enabled** Mobile Authenticator - TOTP entry with your Friendly Name.

Done — CUNY Login MFA is set up. You can now use it to access CUNYfirst, CUNYbuy, Brightspace, Navigate, Zoom, Campus VPN, Lehman 360, and Lehman Electronic Forms.

## If You Get a Login Error (Private/Incognito Window)

If the Self-Service portal gives a login error, use your browser's private window:
- Chrome: New Incognito Window
- Firefox: New Private Window
- Edge: New InPrivate Window
