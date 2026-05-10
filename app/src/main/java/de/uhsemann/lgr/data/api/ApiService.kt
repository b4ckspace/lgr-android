package de.uhsemann.lgr.data.api

import de.uhsemann.lgr.data.model.*
import de.uhsemann.lgr.data.model.Tag as LgrTag
import retrofit2.http.*

interface ApiService {

    @GET("auth")
    suspend fun getAuthStatus(): AuthStatus

    @POST("auth")
    suspend fun login(@Body request: LoginRequest): AuthStatus

    @HTTP(method = "DELETE", path = "auth", hasBody = false)
    suspend fun logout(): AuthStatus

    @GET("api/barcodes/")
    suspend fun getBarcodes(
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("parent") parent: String? = null
    ): PagedResponse<Barcode>

    @PATCH
    suspend fun patchBarcode(@Url url: String, @Body body: com.google.gson.JsonObject): Barcode

    @GET
    suspend fun getBarcodesPage(@Url url: String): PagedResponse<Barcode>

    @GET("api/barcodes/{code}/")
    suspend fun getBarcode(@Path("code") code: String): Barcode

    @GET
    suspend fun getBarcodeByUrl(@Url url: String): Barcode

    @POST("api/barcodes/")
    suspend fun createBarcode(@Body request: CreateBarcodeRequest): Barcode

    @GET("api/persons/")
    suspend fun getPersons(
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): PagedResponse<Person>

    @GET
    suspend fun getPersonsPage(@Url url: String): PagedResponse<Person>

    @GET
    suspend fun getPersonByUrl(@Url url: String): Person

    @GET("api/items/")
    suspend fun getItems(
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): PagedResponse<Item>

    @GET
    suspend fun getItemsPage(@Url url: String): PagedResponse<Item>

    @GET("api/tags/")
    suspend fun getTags(
        @Query("limit") limit: Int = 200,
        @Query("offset") offset: Int = 0
    ): PagedResponse<LgrTag>

    @GET("api/loans/")
    suspend fun getLoans(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): PagedResponse<Loan>

    @GET
    suspend fun getLoansPage(@Url url: String): PagedResponse<Loan>

    @GET("api/my_loans/")
    suspend fun getMyLoans(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): PagedResponse<Loan>

    @GET
    suspend fun getMyLoansPage(@Url url: String): PagedResponse<Loan>

    @POST("loan")
    suspend fun postLoan(@Body request: LoanRequest): LoanResponse
}
