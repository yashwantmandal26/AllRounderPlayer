package com.example.ymediaplayer.mycinema.data

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP client configured with Cloudflare DNS-over-HTTPS (1.1.1.1)
 * with graceful fallback to system DNS.
 * Bypasses local ISP DNS blocks (common in India with Jio/Airtel) for
 * TMDB, Torrentio, MediaFusion, and public torrent trackers.
 */
object DnsOverHttpsClient {

    private class ResilientDns(
        private val dohDns: Dns,
        private val fallbackDns: Dns = Dns.SYSTEM
    ) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                val results = dohDns.lookup(hostname)
                if (results.isNotEmpty()) results else fallbackDns.lookup(hostname)
            } catch (_: Exception) {
                try {
                    fallbackDns.lookup(hostname)
                } catch (e: Exception) {
                    throw UnknownHostException("Failed to resolve $hostname via DoH and System DNS: ${e.message}")
                }
            }
        }
    }

    val client: OkHttpClient by lazy {
        val bootstrapClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        val doh = try {
            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url("https://1.1.1.1/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1"),
                    InetAddress.getByName("8.8.8.8"),
                    InetAddress.getByName("8.8.4.4")
                )
                .includeIPv6(false)
                .build()
        } catch (_: Exception) {
            Dns.SYSTEM
        }

        val effectiveDns = if (doh != Dns.SYSTEM) ResilientDns(doh, Dns.SYSTEM) else Dns.SYSTEM

        val cache = try {
            val dir = java.io.File(com.example.ymediaplayer.YMediaApplication.instance.cacheDir, "mycinema_http_cache")
            okhttp3.Cache(dir, 25L * 1024 * 1024)
        } catch (_: Exception) { null }

        val builder = OkHttpClient.Builder()
            .dns(effectiveDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (cache != null) {
            builder.cache(cache)
        }

        builder.build()
    }
}
