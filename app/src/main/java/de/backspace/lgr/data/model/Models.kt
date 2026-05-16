package de.backspace.lgr.data.model

import com.google.gson.annotations.SerializedName

data class PagedResponse<T>(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<T>
)

data class AuthStatus(
    @SerializedName("logged_in") val authenticated: Boolean,
    val username: String?
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class Tag(
    val url: String,
    val name: String
)

data class Person(
    val url: String,
    val nickname: String,
    val firstname: String,
    val lastname: String,
    val email: String
)

data class Item(
    val url: String,
    val name: String,
    val description: String,
    val tags: List<String>
)

data class ChildInfo(val name: String, val code: String)

data class LoanInfo(val loan: Boolean, val person: String? = null)

data class Barcode(
    val code: String,
    val owner: String?,
    val description: String,
    val item: String,
    val parent: String?,
    @SerializedName("item_name") val itemName: String,
    @SerializedName("item_description") val itemDescription: String,
    @SerializedName("api_child_names") val apiChildNames: List<ChildInfo>? = null,
    @SerializedName("api_parent_names") val apiParentNames: List<ChildInfo>? = null,
    @SerializedName("api_loan_info") val apiLoanInfo: LoanInfo? = null
)

data class Loan(
    val id: Int?,
    val url: String?,
    val person: Int?,
    val barcodes: List<String>,
    val description: String?,
    val status: String,
    @SerializedName("taken_date") val takenDate: String?,
    @SerializedName("return_date") val returnDate: String?,
    @SerializedName("returned_date") val returnedDate: String?
)

data class LoanRequest(
    val items: List<BarcodeCodeItem>,
    @SerializedName("return_date") val returnDate: String?,
    val preview: Boolean = false
)

data class BarcodeCodeItem(val code: String)

data class BarcodeStatus(
    val code: String,
    val loan: Boolean,
    val person: String,
    @SerializedName("item_name") val itemName: String,
    val description: String
)

data class LoanResponse(
    val items: List<BarcodeStatus>?,
    val blocked: List<BarcodeStatus>?,
    val message: String?
)

data class CreateBarcodeRequest(
    val code: String,
    val item: String,
    val description: String = "",
    val owner: String? = null,
    val parent: String? = null
)

data class CreateItemRequest(
    val name: String,
    val description: String = ""
)
