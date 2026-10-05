package dev.teyd.justintv

/**
 * Twitch application client ID, injected at build time from `TWITCH_CLIENT_ID`.
 *
 * Put the value in `.env` (see `.env.schema`) and build with `varlock run -- ./gradlew ...`.
 * Empty until you do. Login is not wired yet; this is where it will be read from.
 */
object TwitchConfig {
    val clientId: String get() = BuildConfig.TWITCH_CLIENT_ID
}
