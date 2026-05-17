package de.backspace.lgr.data.api

import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SessionCookieJar : CookieJar {
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val bucket = store.getOrPut(host) { mutableListOf() }
        cookies.forEach { incoming ->
            bucket.removeAll { it.name == incoming.name }
            bucket.add(incoming)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host] ?: emptyList()

    fun getCsrfToken(): String? = store.values.flatten().find { it.name == "csrftoken" }?.value

    fun clear() = store.clear()
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

    fun reset() {
        cookieJar.clear()
        service = null
    }
}
