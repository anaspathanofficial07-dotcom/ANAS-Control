# ANAS Control — Starter Project

Phone-first Android + cloud starter. This is a foundation, not a finished parental-control product.

## Included
- Separate Parent and Child Android app modules (Kotlin + Jetpack Compose).
- GitHub Actions workflow to build both debug APKs in the cloud.
- Supabase PostgreSQL starter schema with Row Level Security.
- No hidden monitoring, data extraction, or privileged access is implemented.

## Build APKs without a PC
1. Create a GitHub repository named `ANAS-Control`.
2. Upload the contents of this folder to the repository root using GitHub's mobile website/app.
3. Open the repository's **Actions** tab.
4. Select **Android APK Build** and run it, or push to `main`.
5. Open the completed workflow run and download `anas-control-debug-apks` from Artifacts.

GitHub-hosted runners build the APKs; no Android Studio installation is needed on your phone.

## Supabase setup
1. Create a Supabase project.
2. Open SQL Editor.
3. Paste and run `supabase/schema.sql`.
4. Configure Auth providers and email verification.
5. Keep the `service_role` key secret. Never put it in either Android APK.

## Important deployment notes
- Supabase Free currently has limited database/storage/egress quotas and pauses projects after a week of inactivity. Check current pricing before production use.
- Debug APKs are for testing only. Production release requires a protected signing key and a separate release workflow.
- Device Owner provisioning generally requires initial setup/factory reset. Existing-device enrollment is a more limited standard-app mode.
- Android permission prompts and visible disclosures are required for sensitive capabilities.
- This project intentionally does not implement stealth, covert collection, hidden camera/microphone access, or bypasses for Android uninstall/security controls.

## Next development milestones
1. Add Supabase Auth client and secure parent sign-in.
2. Add QR pairing with short-lived, single-use enrollment tokens.
3. Add explicit consent and permission status screens.
4. Add limited device inventory and usage summaries.
5. Add managed-device policy support using Android Enterprise APIs.
6. Add user-initiated screen sharing with visible session indicator.
