package de.backspace.lgr.data.repository

import de.backspace.lgr.data.api.ApiClient
import de.backspace.lgr.data.model.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class LgrRepository {
    private val api get() = ApiClient.getService()

    suspend fun getAuthStatus() = api.getAuthStatus()
    suspend fun login(username: String, password: String): AuthStatus {
        api.getAuthStatus() // GET first so the server sets the csrftoken cookie
        return api.login(LoginRequest(username, password))
    }
    suspend fun logout() = api.logout()

    suspend fun getBarcodes(search: String? = null, limit: Int = 50, offset: Int = 0, noParent: Boolean = false, owner: String? = null) =
        api.getBarcodes(search.takeIf { !it.isNullOrBlank() }, limit, offset, noParent.takeIf { it }, owner = owner)

    suspend fun getBarcodesPage(url: String) = api.getBarcodesPage(url)
    suspend fun getBarcode(code: String) = api.getBarcode(code)
    suspend fun getBarcodeByUrl(url: String) = api.getBarcodeByUrl(url)

    suspend fun patchBarcodeParent(url: String, parentUrl: String?) {
        val json = if (parentUrl != null) "{\"parent\":\"$parentUrl\"}" else "{\"parent\":null}"
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        api.patchBarcode(url, body)
    }
    suspend fun createBarcode(request: CreateBarcodeRequest) = api.createBarcode(request)
    suspend fun deleteBarcode(code: String) {
        val response = api.deleteBarcode(code)
        if (!response.isSuccessful) throw retrofit2.HttpException(response)
    }

    suspend fun updateBarcode(url: String, itemUrl: String, description: String, ownerUrl: String?): Barcode {
        val map: Map<String, Any?> = mapOf("item" to itemUrl, "description" to description, "owner" to ownerUrl)
        val json = com.google.gson.Gson().toJson(map)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        return api.patchBarcode(url, body)
    }

    suspend fun getPersons(search: String? = null, limit: Int = 50, offset: Int = 0) =
        api.getPersons(search.takeIf { !it.isNullOrBlank() }, limit, offset)

    suspend fun getPersonsPage(url: String) = api.getPersonsPage(url)
    suspend fun getPersonByUrl(url: String) = api.getPersonByUrl(url)

    suspend fun getItems(search: String? = null, limit: Int = 50, offset: Int = 0, noBarcodes: Boolean = false) =
        api.getItems(search.takeIf { !it.isNullOrBlank() }, limit, offset, noBarcodes.takeIf { it })

    suspend fun createItem(name: String, description: String = "") =
        api.createItem(CreateItemRequest(name, description))

    suspend fun getBarcodeCountForItem(itemName: String): Int =
        runCatching { api.getBarcodes(search = itemName, limit = 200) }
            .getOrNull()?.results?.count { it.itemName.equals(itemName, ignoreCase = true) } ?: 0

    suspend fun getBarcodesByItem(itemName: String): List<Barcode> {
        val response = api.getBarcodes(search = itemName, limit = 1000)
        return response.results.filter { it.itemName == itemName }
    }

    suspend fun uploadItemImage(itemUrl: String, imageBytes: ByteArray): Item {
        val body = imageBytes.toRequestBody("image/jpeg".toMediaType())
        val uniqueName = "image_${java.util.UUID.randomUUID().toString().replace("-", "").take(8)}.jpg"
        val part = MultipartBody.Part.createFormData("image", uniqueName, body)
        return api.uploadItemImage(itemUrl, part)
    }

    suspend fun updateItem(url: String, name: String, description: String): Item {
        val map: Map<String, Any?> = mapOf("name" to name, "description" to description)
        val json = com.google.gson.Gson().toJson(map)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        return api.patchItem(url, body)
    }

    suspend fun clearItemImage(url: String): Item {
        val body = "{\"image\": null}".toRequestBody("application/json; charset=utf-8".toMediaType())
        return api.patchItem(url, body)
    }

    suspend fun deleteItem(url: String) {
        val response = api.deleteItem(url)
        if (!response.isSuccessful) throw retrofit2.HttpException(response)
    }

    suspend fun getItemsPage(url: String) = api.getItemsPage(url)

    suspend fun getTags() = api.getTags()

    suspend fun getLoans(status: String? = null, limit: Int = 50, offset: Int = 0) = api.getLoans(status, limit, offset)
    suspend fun getLoansPage(url: String) = api.getLoansPage(url)

    suspend fun getMyLoans(status: String? = null, limit: Int = 50, offset: Int = 0) = api.getMyLoans(status, limit, offset)
    suspend fun getMyLoansPage(url: String) = api.getMyLoansPage(url)

    suspend fun returnLoan(url: String): Loan {
        val body = "{\"status\":\"returned\"}".toRequestBody("application/json; charset=utf-8".toMediaType())
        return api.patchLoan(url, body)
    }

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
