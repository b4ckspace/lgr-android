// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.data.api

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// In-memory cookie store that, once attached to SharedPreferences, also persists the session
// (and CSRF) cookies so login survives an app restart.
class SessionCookieJar : CookieJar {
    private val store = mutableMapOf<String, MutableList<Cookie>>()
    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    // Attach a preferences store and reload any persisted cookies into memory.
    @Synchronized
    fun attach(prefs: SharedPreferences) {
        this.prefs = prefs
        val json = prefs.getString(KEY, null) ?: return
        val type = object : TypeToken<Map<String, List<String>>>() {}.type
        val map: Map<String, List<String>> =
            runCatching { gson.fromJson<Map<String, List<String>>>(json, type) }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        map.forEach hosts@ { (host, setCookies) ->
            val httpUrl = "http://$host/".toHttpUrlOrNull() ?: return@hosts
            val bucket = store.getOrPut(host) { mutableListOf() }
            setCookies.forEach cookies@ { sc ->
                val cookie = Cookie.parse(httpUrl, sc) ?: return@cookies
                if (cookie.expiresAt > now) {
                    bucket.removeAll { it.name == cookie.name }
                    bucket.add(cookie)
                }
            }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val bucket = store.getOrPut(host) { mutableListOf() }
        cookies.forEach { incoming ->
            bucket.removeAll { it.name == incoming.name }
            bucket.add(incoming)
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val bucket = store[url.host] ?: return emptyList()
        bucket.removeAll { it.expiresAt < now }
        return bucket.toList()
    }

    fun getCsrfToken(): String? = store.values.flatten().find { it.name == "csrftoken" }?.value

    fun has(name: String): Boolean = store.values.flatten().any { it.name == name }

    @Synchronized
    fun clear() {
        store.clear()
        prefs?.edit()?.remove(KEY)?.apply()
    }

    // Persist only cookies that are meant to outlive the session (have an expiry, not yet expired),
    // serialised as their Set-Cookie strings so they can be re-parsed on the next launch.
    private fun persist() {
        val p = prefs ?: return
        val now = System.currentTimeMillis()
        val serializable = store
            .mapValues { (_, cookies) -> cookies.filter { it.persistent && it.expiresAt > now }.map { it.toString() } }
            .filterValues { it.isNotEmpty() }
        p.edit().putString(KEY, gson.toJson(serializable)).apply()
    }

    private companion object {
        const val KEY = "session_cookies"
    }
}

object ApiClient {
    val cookieJar = SessionCookieJar()
    private var baseUrl: String = "http://localhost:8000/"
    private var service: ApiService? = null

    fun configure(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        if (normalized != baseUrl || service == null) {
            baseUrl = normalized
            service = null
        }
    }

    fun getService(): ApiService {
        return service ?: buildService().also { service = it }
    }

    private fun buildService(): ApiService {
        val csrfInterceptor = Interceptor { chain ->
            val req = chain.request()
            val built = if (req.method in listOf("POST", "PUT", "PATCH", "DELETE")) {
                req.newBuilder()
                    .header("X-CSRFToken", cookieJar.getCsrfToken() ?: "")
                    .header("Referer", baseUrl)
                    .build()
            } else req
            chain.proceed(built)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(csrfInterceptor)
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    fun getBarcodeUrl(code: String) = "${baseUrl}api/barcodes/$code/"
    fun getPersonUrl(id: String) = "${baseUrl}api/persons/$id/"
    fun getItemUrl(id: String) = "${baseUrl}api/items/$id/"
    fun getLoanUrl(id: Int) = "${baseUrl}api/loans/$id/"
    fun getMyLoanUrl(id: Int) = "${baseUrl}api/my_loans/$id/"

    fun attachCookieStore(prefs: SharedPreferences) = cookieJar.attach(prefs)

    // Whether a restored Django session cookie is present (i.e. a startup auth check is worthwhile).
    fun hasSession(): Boolean = cookieJar.has("sessionid")

    fun reset() {
        cookieJar.clear()
        service = null
    }
}
