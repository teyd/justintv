package dev.teyd.justintv.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.net.Network
import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Resolves names on the network the app is actually using, and remembers good answers briefly.
 *
 * [Dns.SYSTEM] goes through `InetAddress.getAllByName`, which in this app can answer "no address
 * associated with hostname" for a name the device can ping, or take seconds to say so. Asking the
 * active network directly is both the fix and the fast path, so it goes first and the platform
 * resolver is only the fallback.
 *
 * Chat and the emote picker open dozens of images at once, so lookups run in parallel, only one
 * runs per hostname at a time, and a good answer is reused for a few minutes. A failure is never
 * cached: the next call tries again. When a lookup fails but an older answer exists, the older
 * one is returned instead of an error.
 */
class ActiveNetworkDns(
    context: Context,
) : Dns {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val executor: ExecutorService =
        Executors.newFixedThreadPool(PARALLEL_LOOKUPS) { runnable ->
            Thread(runnable, "justintv-dns").apply { isDaemon = true }
        }
    private val cache = ConcurrentHashMap<String, Entry>()
    private val inFlight = ConcurrentHashMap<String, InFlight>()

    override fun lookup(hostname: String): List<InetAddress> {
        val cached = cache[hostname]
        if (cached != null && System.currentTimeMillis() - cached.at < CACHE_TTL_MS) return cached.addresses

        val mine = InFlight()
        val running = inFlight.putIfAbsent(hostname, mine)
        if (running != null) {
            val shared = running.await()
            if (shared.isNotEmpty()) return shared
            return cached?.addresses ?: throw UnknownHostException("Unable to resolve $hostname")
        }

        val addresses =
            try {
                resolve(hostname)
            } finally {
                inFlight.remove(hostname)
            }
        mine.complete(addresses)
        if (addresses.isNotEmpty()) {
            cache[hostname] = Entry(addresses, System.currentTimeMillis())
            return addresses
        }
        return cached?.addresses ?: throw UnknownHostException("Unable to resolve $hostname")
    }

    private fun resolve(hostname: String): List<InetAddress> {
        val network = connectivity?.activeNetwork
        if (network != null) {
            val found = queryBoth(network, hostname)
            if (found.isNotEmpty()) return order(found)
        }
        return try {
            order(Dns.SYSTEM.lookup(hostname))
        } catch (_: UnknownHostException) {
            emptyList()
        }
    }

    /** IPv4 first: mobile networks carry it more reliably, and Twitch serves both. */
    private fun order(addresses: List<InetAddress>): List<InetAddress> {
        val v4 = addresses.filterIsInstance<Inet4Address>()
        return (v4 + addresses.filter { it !is Inet4Address }).distinct()
    }

    /** A and AAAA at the same time, so the slower one does not add to the faster one. */
    private fun queryBoth(
        network: Network,
        hostname: String,
    ): List<InetAddress> {
        val v4 = AtomicReference<List<InetAddress>>(emptyList())
        val v6 = AtomicReference<List<InetAddress>>(emptyList())
        val done = CountDownLatch(2)
        query(network, hostname, DnsResolver.TYPE_A, v4, done)
        query(network, hostname, DnsResolver.TYPE_AAAA, v6, done)
        done.await(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return v4.get() + v6.get()
    }

    private fun query(
        network: Network,
        hostname: String,
        type: Int,
        into: AtomicReference<List<InetAddress>>,
        done: CountDownLatch,
    ) {
        DnsResolver.getInstance().query(
            network,
            hostname,
            type,
            DnsResolver.FLAG_EMPTY,
            executor,
            null,
            object : DnsResolver.Callback<List<InetAddress>> {
                override fun onAnswer(
                    answer: List<InetAddress>,
                    rcode: Int,
                ) {
                    into.set(answer)
                    done.countDown()
                }

                override fun onError(error: DnsResolver.DnsException) {
                    done.countDown()
                }
            },
        )
    }

    private class Entry(
        val addresses: List<InetAddress>,
        val at: Long,
    )

    /** One hostname, one lookup. Everyone else waits on the same answer. */
    private class InFlight {
        private val done = CountDownLatch(1)
        private val result = AtomicReference<List<InetAddress>>(emptyList())

        fun complete(addresses: List<InetAddress>) {
            result.set(addresses)
            done.countDown()
        }

        /** Long enough for the slowest lookup to finish, including the platform fallback. */
        fun await(): List<InetAddress> {
            done.await(WAIT_FOR_LEADER_SECONDS, TimeUnit.SECONDS)
            return result.get()
        }
    }

    private companion object {
        const val LOOKUP_TIMEOUT_SECONDS = 4L
        const val WAIT_FOR_LEADER_SECONDS = 12L
        const val CACHE_TTL_MS = 5 * 60_000L
        const val PARALLEL_LOOKUPS = 4
    }
}
