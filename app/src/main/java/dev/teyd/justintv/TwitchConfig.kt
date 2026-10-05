package dev.teyd.justintv

/**
 * Twitch application client ID, injected at build time from `TWITCH_CLIENT_ID`.
 *
 * Put the value in `.env.local` (see `.env.schema`). Gradle reads that file, and `varlock run`
 * still wins when the variable is already in the environment. Empty until you do.
 */
object TwitchConfig {
    val clientId: String get() = BuildConfig.TWITCH_CLIENT_ID
}
