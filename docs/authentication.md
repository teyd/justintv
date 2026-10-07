# Twitch login

Login uses Twitch's device-code grant. There is no auth backend or bundled client secret. Browsing and playback remain available without login; signing in currently connects your account identity, not personalized streams or subscriber-only playback.

## Setup

1. Register your own app in the [Twitch developer console](https://dev.twitch.tv/console/apps). Set its client type to **Public**. A confidential client cannot refresh device-code tokens without a secret.
2. Put `TWITCH_CLIENT_ID=your_client_id` in **`.env.local`** (with a dot, not a space). Do not use Twitch's web-client ID or add a client secret.
3. Restart Expo after changing environment variables. The schema permits an unset ID so guest browsing still works; Account explains missing configuration.
4. Rebuild Android after installing the native dependencies:

   ```sh
   bun run android
   ```

   This requires the project's Android SDK/JDK and an emulator or connected device. This app already uses a custom native networking module, so use its Android build rather than Expo Go.

## Try the flow

- Open **Account → Continue with Twitch**.
- Approve the request in Twitch's browser page. Close the browser or switch back to the app; device authorization does not redirect automatically.
- Alternatively, visit `https://www.twitch.tv/activate` on another device and enter the displayed code.
- The app polls at the server's interval and shows your Twitch username after approval and secure persistence.
- Close and reopen the app: it restores and validates the saved session.
- Sign out: local credentials are removed immediately and access-token revocation is attempted without blocking on connectivity.

Also test cancelling, denying consent, letting the code expire, going offline during startup, reconnecting and pressing Retry connection, and disconnecting the app in Twitch's Connections settings. Network failures preserve a saved session; invalid credentials trigger one refresh attempt, then clear the session if refresh is rejected. Validation runs at startup, on foreground resume, and hourly while active.

## Checks

```sh
bunx --no-install vp check
bun run test
```

Automated tests use an injected storage adapter and mocked HTTP transport (not native module mocks) to cover polling, slow-down, timeouts, cancellation, secure persistence failures, refresh rotation, offline recovery, and logout races. Actual browser, Keystore, and Twitch approval behavior still need testing on an Android device.

OAuth requests only target `id.twitch.tv`. Tokens never enter UI state or the Luminous/playback clients, and all response and persisted data are decoded with Effect Schema. Public-client refresh tokens are single-use and have limited lifetimes, so Twitch can require approval again after prolonged inactivity.
