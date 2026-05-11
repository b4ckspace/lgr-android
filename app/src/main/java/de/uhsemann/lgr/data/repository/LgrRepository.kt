package de.uhsemann.lgr.data.repository

import de.uhsemann.lgr.data.api.ApiClient
import de.uhsemann.lgr.data.model.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class LgrRepository {
    private val api get() = ApiClient.getService()

    suspend fun getAuthStatus() = api.getAuthStatus()
    suspend fun login(username: String, password: String): AuthStatus {
        api.getAuthStatus() // GET first so the server sets the csrftoken cookie
        return api.login(LoginRequest(username, password))
    }
    suspend fun logout() = api.logout()

    suspend fun getBarcodes(search: String? = null, limit: Int = 50, offset: Int = 0) =
        api.getBarcodes(search.takeIf { !it.isNullOrBlank() }, limit, offset)

    suspend fun getBarcodesPage(url: String) = api.getBarcodesPage(url)
    suspend fun getBarcode(code: String) = api.getBarcode(code)
    suspend fun getBarcodeByUrl(url: String) = api.getBarcodeByUrl(url)

    suspend fun patchBarcodeParent(url: String, parentUrl: String?) {
        val json = if (parentUrl != null) "{\"parent\":\"$parentUrl\"}" else "{\"parent\":null}"
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        api.patchBarcode(url, body)
    }
    suspend fun createBarcode(request: CreateBarcodeRequest) = api.createBarcode(request)

    suspend fun getPersons(search: String? = null, limit: Int = 50, offset: Int = 0) =
        api.getPersons(search.takeIf { !it.isNullOrBlank() }, limit, offset)

    suspend fun getPersonsPage(url: String) = api.getPersonsPage(url)
    suspend fun getPersonByUrl(url: String) = api.getPersonByUrl(url)

    suspend fun getItems(search: String? = null, limit: Int = 50, offset: Int = 0) =
        api.getItems(search.takeIf { !it.isNullOrBlank() }, limit, offset)

    suspend fun getItemsPage(url: String) = api.getItemsPage(url)

    suspend fun getTags() = api.getTags()

    suspend fun getLoans(limit: Int = 50, offset: Int = 0) = api.getLoans(limit, offset)
    suspend fun getLoansPage(url: String) = api.getLoansPage(url)

    suspend fun getMyLoans(limit: Int = 50, offset: Int = 0) = api.getMyLoans(limit, offset)
    suspend fun getMyLoansPage(url: String) = api.getMyLoansPage(url)

    suspend fun loanBarcodes(
        codes: List<String>,
        returnDate: String?,
        preview: Boolean
    ) = api.postLoan(
        LoanRequest(
            items = codes.map { BarcodeCodeItem(it) },
            returnDate = returnDate,
            preview = preview
        )
    )
}
