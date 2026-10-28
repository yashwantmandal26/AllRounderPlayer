package com.example.ymediaplayer.mycinema.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.example.ymediaplayer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DownloadSite(
    val id: String,
    val name: String,
    val portalUrl: String,
    val defaultDomain: String,
    val searchTemplate: String,
    val logoResId: Int,
    val accentColorHex: Long,
    val domainHints: List<String>
)

data class DownloadSiteResolveResult(
    val siteId: String,
    val domain: String,
    val searchUrl: String,
    val isStale: Boolean,
    val notice: String? = null
)

object DownloadSitesManager {
    private const val PREFS_NAME = "mycinema_download_sites_v2"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    private const val REMOTE_SITES_URL =
        "https://raw.githubusercontent.com/raj-Dcoder/MyCinema/master/download-sites/sites.json"

    val SITES = listOf(
        DownloadSite(
            id = "hdmovies",
            name = "HDHub4u",
            portalUrl = "https://hdhub4u.bi/",
            defaultDomain = "https://new8.hdhub4u.cl",
            searchTemplate = "/search.html?q={query}",
            logoResId = R.drawable.site_hdhub4u,
            accentColorHex = 0xFFF43F5E,
            domainHints = listOf("hdhub", "hub4u", "hdmovie")
        ),
        DownloadSite(
            id = "catmovies",
            name = "KatMovieHD",
            portalUrl = "https://katworld.net/",
            defaultDomain = "https://new2.katmoviehd.top",
            searchTemplate = "/?s={query}",
            logoResId = R.drawable.site_katmoviehd,
            accentColorHex = 0xFF22D3EE,
            domainHints = listOf("katmovie")
        )
    )

    fun getSite(siteId: String): DownloadSite? {
        return SITES.find { it.id == siteId }
    }

    fun getManualOverride(context: Context, siteId: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("manual_domain_$siteId", null)?.takeIf { it.isNotBlank() }
    }

    fun setManualOverride(context: Context, siteId: String, domain: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val normalized = domain.trim().trimEnd('/')
        prefs.edit().putString("manual_domain_$siteId", normalized).apply()
    }

    fun clearManualOverride(context: Context, siteId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("manual_domain_$siteId").apply()
    }

    suspend fun resolveDownloadSiteUrl(
        context: Context,
        siteId: String,
        title: String,
        year: String? = null
    ): DownloadSiteResolveResult = withContext(Dispatchers.IO) {
        val site = getSite(siteId) ?: throw IllegalArgumentException("Unknown download site: $siteId")
        val cleanTitle = title.trim()
        val query = if (!year.isNullOrBlank() && !cleanTitle.contains(year)) {
            "$cleanTitle $year".trim()
        } else {
            cleanTitle
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Manual override wins unconditionally
        val manual = getManualOverride(context, siteId)
        if (!manual.isNullOrBlank()) {
            val searchUrl = buildSearchUrl(manual, site.searchTemplate, query)
            return@withContext DownloadSiteResolveResult(
                siteId = siteId,
                domain = manual,
                searchUrl = searchUrl,
                isStale = false,
                notice = null
            )
        }

        // 2. Fresh local cache (< 24h)
        val cachedDomain = prefs.getString("cached_domain_$siteId", null)
        val cachedTime = prefs.getLong("cached_time_$siteId", 0L)
        val now = System.currentTimeMillis()
        val cacheAge = now - cachedTime

        if (!cachedDomain.isNullOrBlank() && cacheAge in 0 until CACHE_TTL_MS) {
            val searchUrl = buildSearchUrl(cachedDomain, site.searchTemplate, query)
            return@withContext DownloadSiteResolveResult(
                siteId = siteId,
                domain = cachedDomain,
                searchUrl = searchUrl,
                isStale = false,
                notice = null
            )
        }

        // 3. Resolve live domain
        var resolvedDomain = fetchLiveDomain(site)
        var isStale = false
        var notice: String? = null

        if (!resolvedDomain.isNullOrBlank()) {
            // Persist fresh cache
            prefs.edit()
                .putString("cached_domain_$siteId", resolvedDomain)
                .putLong("cached_time_$siteId", now)
                .apply()
        } else if (!cachedDomain.isNullOrBlank()) {
            // Stale cache fallback
            resolvedDomain = cachedDomain
            isStale = true
            notice = "Latest link verify nahi hua - backup link khola hai."
        } else {
            // Verified fallback mirror
            resolvedDomain = site.defaultDomain
            isStale = true
            notice = "Latest link verify nahi hua - backup link khola hai."
        }

        val finalDomain = resolvedDomain.trimEnd('/')
        val searchUrl = buildSearchUrl(finalDomain, site.searchTemplate, query)

        DownloadSiteResolveResult(
            siteId = siteId,
            domain = finalDomain,
            searchUrl = searchUrl,
            isStale = isStale,
            notice = notice
        )
    }

    private fun buildSearchUrl(domain: String, template: String, query: String): String {
        val encodedQuery = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (_: Exception) {
            query.replace(" ", "+")
        }
        val cleanDomain = domain.trimEnd('/')
        return if (template.contains("{query}")) {
            cleanDomain + template.replace("{query}", encodedQuery)
        } else {
            "$cleanDomain/?s=$encodedQuery"
        }
    }

    private fun fetchLiveDomain(site: DownloadSite): String? {
        // Priority 1: GitHub-hosted verified sites.json
        try {
            val req = Request.Builder()
                .url(REMOTE_SITES_URL)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val resp = DnsOverHttpsClient.client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val root = JSONObject(body)
                    val sitesObj = root.optJSONObject("sites")
                    val siteEntry = sitesObj?.optJSONObject(site.id)
                    val domain = siteEntry?.optString("domain")
                    if (!domain.isNullOrBlank() && domain.startsWith("http")) {
                        return domain.trim().trimEnd('/')
                    }
                }
            }
        } catch (_: Exception) {}

        // Priority 2: For KatMovieHD, try portal 302 redirect
        if (site.id == "catmovies") {
            try {
                val noRedirectClient = DnsOverHttpsClient.client.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .callTimeout(3, TimeUnit.SECONDS)
                    .build()
                val katReq = Request.Builder()
                    .url("${site.portalUrl}?type=KatmovieHD")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                val katResp = noRedirectClient.newCall(katReq).execute()
                val loc = katResp.header("Location")
                if (!loc.isNullOrBlank() && loc.startsWith("http")) {
                    return loc.trim().trimEnd('/')
                }
            } catch (_: Exception) {}
        }

        return null
    }

    fun openInBrowser(context: Context, url: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(0xFF0F172A.toInt())
                        .setSecondaryToolbarColor(0xFF0C1017.toInt())
                        .setNavigationBarColor(0xFF0C1017.toInt())
                        .build()
                )
                .build()
            customTabsIntent.intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            customTabsIntent.launchUrl(context, Uri.parse(url))
        } catch (_: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
