package de.uhsemann.lgr.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.uhsemann.lgr.data.api.ApiClient
import de.uhsemann.lgr.data.model.*
import de.uhsemann.lgr.data.repository.LgrRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class UiState<T>(
    val data: T? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

enum class ScanResult { FOUND_NEW, FOUND_EXISTING, DUPLICATE, NOT_FOUND }

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("lgr_prefs", Context.MODE_PRIVATE)
    private val repo = LgrRepository()

    var serverUrl by mutableStateOf(prefs.getString("server_url", "") ?: "")
        private set

    var auth by mutableStateOf(UiState<AuthStatus>())
    var items by mutableStateOf(UiState<List<Item>>())
    var itemsNextPage by mutableStateOf<String?>(null)
    var barcodes by mutableStateOf(UiState<List<Barcode>>())
    var barcodesNextPage by mutableStateOf<String?>(null)
    var persons by mutableStateOf(UiState<List<Person>>())
    var personsNextPage by mutableStateOf<String?>(null)
    var loans by mutableStateOf(UiState<List<Loan>>())
    var loansNextPage by mutableStateOf<String?>(null)
    var myLoans by mutableStateOf(UiState<List<Loan>>())
    var myLoansNextPage by mutableStateOf<String?>(null)
    var tags by mutableStateOf(UiState<List<Tag>>())

    var selectedBarcodes by mutableStateOf<Set<String>>(emptySet())
    var loanState by mutableStateOf(UiState<LoanResponse>())
    var scannedBarcode by mutableStateOf(UiState<Barcode>())
    var scannedChildCodes by mutableStateOf<Set<String>>(emptySet())
        private set
    var newScannedBarcodes by mutableStateOf<List<Barcode>>(emptyList())
        private set
    var contentScanActive by mutableStateOf(false)
        private set
    var addContentScanActive by mutableStateOf(false)
        private set
    var saveContentState by mutableStateOf(UiState<Unit>())
        private set
    var childLoanInfos by mutableStateOf<Map<String, LoanInfo>>(emptyMap())
        private set
    var barcodesSearch by mutableStateOf("")
        private set
    var barcodeListContext by mutableStateOf<List<Barcode>?>(null)
        private set
    var barcodeListIndex by mutableStateOf(0)
        private set
    var barcodeHistory by mutableStateOf<List<String>>(emptyList())
        private set
    var barcodeForwardHistory by mutableStateOf<List<String>>(emptyList())
        private set
    var pendingNewParent by mutableStateOf<Barcode?>(null)
        private set
    var saveParentState by mutableStateOf(UiState<Unit>())
        private set

    var readonlyMode by mutableStateOf(false)
        private set
    var newBarcodeState by mutableStateOf(UiState<Barcode>())
        private set
    var scannedCodeForNewBarcode by mutableStateOf<String?>(null)
        private set

    val isAuthenticated get() = auth.data?.authenticated == true
    val username get() = auth.data?.username

    private var searchJob: Job? = null
    private var childLoanJob: Job? = null
    private val ownerNameCache = mutableMapOf<String, String>()

    suspend fun resolveOwnerName(url: String): String =
        ownerNameCache.getOrPut(url) {
            runCatching { repo.getPersonByUrl(url) }
                .getOrNull()
                ?.let { p ->
                    listOf(p.firstname, p.lastname)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank { p.nickname }
                }
                ?: url
        }

    init {
        if (serverUrl.isNotEmpty()) {
            ApiClient.configure(serverUrl)
            checkAuth()
        }
    }

    fun applyServerUrl(url: String) {
        serverUrl = url
        prefs.edit().putString("server_url", url).apply()
        ApiClient.reset()
        ApiClient.configure(url)
    }

    fun clearNewBarcodeState() {
        newBarcodeState = UiState()
        scannedCodeForNewBarcode = null
        clearPendingNewParent()
    }

    fun onNewBarcodeCodeScanned(code: String) {
        scannedCodeForNewBarcode = code
    }

    suspend fun searchItemsWithCounts(query: String): List<Pair<Item, Int>> {
        if (query.isBlank()) return emptyList()
        val items = runCatching { repo.getItems(search = query, limit = 10) }
            .getOrNull()?.results ?: return emptyList()
        return coroutineScope {
            items.map { item ->
                async { item to repo.getBarcodeCountForItem(item.url) }
            }.awaitAll()
        }
    }

    fun createNewBarcode(
        code: String,
        itemName: String,
        selectedItem: Item?,
        description: String,
        parentCode: String
    ) = viewModelScope.launch {
        newBarcodeState = UiState(isLoading = true)

        val itemUrl = if (selectedItem != null) {
            selectedItem.url
        } else {
            val existing = runCatching { repo.getItems(search = itemName, limit = 50) }
                .getOrNull()?.results?.find { it.name.equals(itemName, ignoreCase = true) }
            existing?.url ?: runCatching { repo.createItem(itemName) }
                .getOrElse { newBarcodeState = UiState(error = it.localizedMessage); return@launch }
                .url
        }

        val parentUrl = if (parentCode.isNotBlank()) ApiClient.getBarcodeUrl(parentCode) else null

        runCatching {
            repo.createBarcode(CreateBarcodeRequest(code = code, item = itemUrl, description = description, parent = parentUrl))
        }.onSuccess { barcode ->
            scannedBarcode = UiState(data = barcode)
            barcodeHistory = emptyList()
            barcodeForwardHistory = emptyList()
            barcodeListContext = null
            childLoanInfos = emptyMap()
            contentScanActive = false
            addContentScanActive = false
            saveContentState = UiState()
            pendingNewParent = null
            saveParentState = UiState()
            newBarcodeState = UiState(data = barcode)
        }.onFailure {
            newBarcodeState = UiState(error = it.localizedMessage)
        }
    }

    fun enterReadonlyMode(url: String) {
        if (url.isNotBlank()) {
            serverUrl = url
            prefs.edit().putString("server_url", url).apply()
            ApiClient.reset()
            ApiClient.configure(url)
        }
        readonlyMode = true
    }

    fun exitReadonlyMode() {
        readonlyMode = false
    }

    fun resetLoanState() { loanState = UiState() }

    fun checkAuth() = viewModelScope.launch {
        auth = UiState(isLoading = true)
        runCatching { repo.getAuthStatus() }
            .onSuccess { auth = UiState(data = it) }
            .onFailure { auth = UiState(error = it.message) }
    }

    fun login(username: String, password: String) = viewModelScope.launch {
        auth = UiState(isLoading = true)
        runCatching { repo.login(username, password) }
            .onSuccess { auth = UiState(data = it) }
            .onFailure { auth = UiState(error = it.localizedMessage) }
    }

    fun logout() = viewModelScope.launch {
        runCatching { repo.logout() }
            .onSuccess {
                ApiClient.reset()
                ApiClient.configure(serverUrl)
                auth = UiState(data = it)
                selectedBarcodes = emptySet()
            }
            .onFailure { auth = UiState(error = it.localizedMessage) }
    }

    fun loadItems(search: String? = null) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!search.isNullOrBlank()) delay(300)
            items = UiState(isLoading = true)
            runCatching { repo.getItems(search) }
                .onSuccess { items = UiState(data = it.results); itemsNextPage = it.next }
                .onFailure { items = UiState(error = it.localizedMessage) }
        }
    }

    fun loadMoreItems() {
        val next = itemsNextPage ?: return
        viewModelScope.launch {
            runCatching { repo.getItemsPage(next) }
                .onSuccess { items = UiState(data = (items.data ?: emptyList()) + it.results); itemsNextPage = it.next }
        }
    }

    fun loadBarcodes(search: String? = null) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!search.isNullOrBlank()) delay(300)
            barcodes = UiState(isLoading = true)
            runCatching { repo.getBarcodes(search) }
                .onSuccess { barcodes = UiState(data = it.results); barcodesNextPage = it.next }
                .onFailure { barcodes = UiState(error = it.localizedMessage) }
        }
    }

    fun loadMoreBarcodes() {
        val next = barcodesNextPage ?: return
        viewModelScope.launch {
            runCatching { repo.getBarcodesPage(next) }
                .onSuccess { barcodes = UiState(data = (barcodes.data ?: emptyList()) + it.results); barcodesNextPage = it.next }
        }
    }

    fun loadPersons(search: String? = null) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!search.isNullOrBlank()) delay(300)
            persons = UiState(isLoading = true)
            runCatching { repo.getPersons(search) }
                .onSuccess { persons = UiState(data = it.results); personsNextPage = it.next }
                .onFailure { persons = UiState(error = it.localizedMessage) }
        }
    }

    fun loadMorePersons() {
        val next = personsNextPage ?: return
        viewModelScope.launch {
            runCatching { repo.getPersonsPage(next) }
                .onSuccess { persons = UiState(data = (persons.data ?: emptyList()) + it.results); personsNextPage = it.next }
        }
    }

    fun loadLoans() = viewModelScope.launch {
        loans = UiState(isLoading = true)
        runCatching { repo.getLoans() }
            .onSuccess { loans = UiState(data = it.results); loansNextPage = it.next }
            .onFailure { loans = UiState(error = it.localizedMessage) }
    }

    fun loadMoreLoans() {
        val next = loansNextPage ?: return
        viewModelScope.launch {
            runCatching { repo.getLoansPage(next) }
                .onSuccess { loans = UiState(data = (loans.data ?: emptyList()) + it.results); loansNextPage = it.next }
        }
    }

    fun loadMyLoans() = viewModelScope.launch {
        myLoans = UiState(isLoading = true)
        runCatching { repo.getMyLoans() }
            .onSuccess { myLoans = UiState(data = it.results); myLoansNextPage = it.next }
            .onFailure { myLoans = UiState(error = it.localizedMessage) }
    }

    fun loadMoreMyLoans() {
        val next = myLoansNextPage ?: return
        viewModelScope.launch {
            runCatching { repo.getMyLoansPage(next) }
                .onSuccess { myLoans = UiState(data = (myLoans.data ?: emptyList()) + it.results); myLoansNextPage = it.next }
        }
    }

    fun loadBarcode(code: String) = viewModelScope.launch {
        pendingNewParent = null
        saveParentState = UiState()
        childLoanJob?.cancel()
        childLoanInfos = emptyMap()
        scannedBarcode = UiState(isLoading = true)
        scannedChildCodes = emptySet()
        newScannedBarcodes = emptyList()
        contentScanActive = false
        addContentScanActive = false
        saveContentState = UiState()
        runCatching { repo.getBarcode(code) }
            .onSuccess { barcode ->
                scannedBarcode = UiState(data = barcode)
                loadChildLoanInfos(barcode.apiChildNames ?: emptyList())
            }
            .onFailure { scannedBarcode = UiState(error = it.localizedMessage) }
    }

    private fun loadChildLoanInfos(children: List<ChildInfo>) {
        if (children.isEmpty()) return
        childLoanJob = viewModelScope.launch {
            val pairs = children.map { child ->
                async { child.code to runCatching { repo.getBarcode(child.code) }.getOrNull()?.apiLoanInfo }
            }.awaitAll()
            childLoanInfos = pairs.mapNotNull { (code, info) -> info?.let { code to it } }.toMap()
        }
    }

    fun clearScannedBarcode() {
        scannedBarcode = UiState()
        barcodeHistory = emptyList()
        barcodeForwardHistory = emptyList()
        pendingNewParent = null
        saveParentState = UiState()
        addContentScanActive = false
    }

    // --- Barcode link navigation with back/forward history ---

    fun navigateToBarcode(code: String) {
        val currentCode = scannedBarcode.data?.code ?: return
        barcodeHistory = (barcodeHistory + currentCode).takeLast(20)
        barcodeForwardHistory = emptyList()
        barcodeListContext = null
        loadBarcode(code)
    }

    fun popBarcodeHistory(): String? {
        if (barcodeHistory.isEmpty()) return null
        val currentCode = scannedBarcode.data?.code
        val prev = barcodeHistory.last()
        barcodeHistory = barcodeHistory.dropLast(1)
        if (currentCode != null) barcodeForwardHistory = (barcodeForwardHistory + currentCode).takeLast(20)
        return prev
    }

    fun navigateForward() {
        if (barcodeForwardHistory.isEmpty()) return
        val currentCode = scannedBarcode.data?.code
        val next = barcodeForwardHistory.last()
        barcodeForwardHistory = barcodeForwardHistory.dropLast(1)
        if (currentCode != null) barcodeHistory = (barcodeHistory + currentCode).takeLast(20)
        loadBarcode(next)
    }

    // --- New parent scanning ---

    suspend fun setPendingNewParent(code: String): Boolean =
        runCatching { repo.getBarcode(code) }
            .onSuccess { pendingNewParent = it }
            .isSuccess

    fun clearPendingNewParent() {
        pendingNewParent = null
        saveParentState = UiState()
    }

    fun saveNewParent(barcode: Barcode) = viewModelScope.launch {
        val parent = pendingNewParent ?: return@launch
        saveParentState = UiState(isLoading = true)
        val parentUrl = ApiClient.getBarcodeUrl(parent.code)
        runCatching { repo.patchBarcodeParent(ApiClient.getBarcodeUrl(barcode.code), parentUrl) }
            .onSuccess {
                val newParentNames = listOf(
                    ChildInfo(name = "${parent.itemName} (${parent.code})", code = parent.code)
                ) + (parent.apiParentNames ?: emptyList())
                scannedBarcode = UiState(data = barcode.copy(apiParentNames = newParentNames))
                pendingNewParent = null
                saveParentState = UiState(data = Unit)
            }
            .onFailure { saveParentState = UiState(error = it.localizedMessage) }
    }

    // --- Content scanning ---

    fun startContentScan() {
        scannedChildCodes = emptySet()
        newScannedBarcodes = emptyList()
        contentScanActive = true
        addContentScanActive = false
        saveContentState = UiState()
    }

    fun startAddContentScan() {
        newScannedBarcodes = emptyList()
        addContentScanActive = true
        contentScanActive = false
        saveContentState = UiState()
    }

    suspend fun onContentBarcodeScanned(code: String): ScanResult {
        val currentBarcode = scannedBarcode.data ?: return ScanResult.NOT_FOUND
        if (code in scannedChildCodes || newScannedBarcodes.any { it.code == code }) return ScanResult.DUPLICATE
        val isExistingChild = currentBarcode.apiChildNames?.any { it.code == code } == true
        return if (isExistingChild) {
            scannedChildCodes = scannedChildCodes + code
            ScanResult.FOUND_EXISTING
        } else {
            val b = runCatching { repo.getBarcode(code) }.getOrNull() ?: return ScanResult.NOT_FOUND
            newScannedBarcodes = newScannedBarcodes + b
            ScanResult.FOUND_NEW
        }
    }

    suspend fun onAddContentBarcodeScanned(code: String): ScanResult {
        val currentBarcode = scannedBarcode.data ?: return ScanResult.NOT_FOUND
        if (newScannedBarcodes.any { it.code == code }) return ScanResult.DUPLICATE
        if (currentBarcode.apiChildNames?.any { it.code == code } == true) return ScanResult.DUPLICATE
        val b = runCatching { repo.getBarcode(code) }.getOrNull() ?: return ScanResult.NOT_FOUND
        newScannedBarcodes = newScannedBarcodes + b
        return ScanResult.FOUND_NEW
    }

    suspend fun tryLoadBarcode(code: String): Boolean {
        barcodeHistory = emptyList()
        barcodeForwardHistory = emptyList()
        pendingNewParent = null
        saveParentState = UiState()
        childLoanJob?.cancel()
        childLoanInfos = emptyMap()
        scannedBarcode = UiState(isLoading = true)
        scannedChildCodes = emptySet()
        newScannedBarcodes = emptyList()
        contentScanActive = false
        addContentScanActive = false
        saveContentState = UiState()
        return runCatching { repo.getBarcode(code) }
            .onSuccess { barcode ->
                scannedBarcode = UiState(data = barcode)
                loadChildLoanInfos(barcode.apiChildNames ?: emptyList())
            }
            .onFailure { scannedBarcode = UiState() }
            .isSuccess
    }

    fun saveContentChanges(parentBarcode: Barcode) = viewModelScope.launch {
        saveContentState = UiState(isLoading = true)
        val children = parentBarcode.apiChildNames ?: emptyList()
        val parentUrl = ApiClient.getBarcodeUrl(parentBarcode.code)
        val errors = mutableListOf<String>()

        for (b in newScannedBarcodes) {
            runCatching { repo.patchBarcodeParent(ApiClient.getBarcodeUrl(b.code), parentUrl) }
                .onFailure { errors.add(it.localizedMessage ?: b.code) }
        }
        for (child in children.filter { it.code !in scannedChildCodes && childLoanInfos[it.code]?.loan != true }) {
            runCatching { repo.patchBarcodeParent(ApiClient.getBarcodeUrl(child.code), null) }
                .onFailure { errors.add(it.localizedMessage ?: child.code) }
        }

        if (errors.isEmpty()) {
            val keptChildren = children.filter { it.code in scannedChildCodes || childLoanInfos[it.code]?.loan == true }
            val addedChildren = newScannedBarcodes.map { b -> ChildInfo(name = "${b.itemName} (${b.code})", code = b.code) }
            scannedChildCodes = emptySet()
            newScannedBarcodes = emptyList()
            contentScanActive = false
            saveContentState = UiState(data = Unit)
            scannedBarcode = UiState(data = parentBarcode.copy(apiChildNames = keptChildren + addedChildren))
        } else {
            saveContentState = UiState(error = errors.joinToString("\n"))
        }
    }

    fun saveAddedContent(parentBarcode: Barcode) = viewModelScope.launch {
        saveContentState = UiState(isLoading = true)
        val parentUrl = ApiClient.getBarcodeUrl(parentBarcode.code)
        val errors = mutableListOf<String>()

        for (b in newScannedBarcodes) {
            runCatching { repo.patchBarcodeParent(ApiClient.getBarcodeUrl(b.code), parentUrl) }
                .onFailure { errors.add(it.localizedMessage ?: b.code) }
        }

        if (errors.isEmpty()) {
            val addedChildren = newScannedBarcodes.map { b -> ChildInfo(name = "${b.itemName} (${b.code})", code = b.code) }
            val existingChildren = parentBarcode.apiChildNames ?: emptyList()
            newScannedBarcodes = emptyList()
            addContentScanActive = false
            saveContentState = UiState(data = Unit)
            scannedBarcode = UiState(data = parentBarcode.copy(apiChildNames = existingChildren + addedChildren))
        } else {
            saveContentState = UiState(error = errors.joinToString("\n"))
        }
    }

    fun updateBarcodesSearch(query: String) {
        barcodesSearch = query
        loadBarcodes(query)
    }

    fun openBarcodeFromList(list: List<Barcode>, index: Int) {
        barcodeHistory = emptyList()
        barcodeForwardHistory = emptyList()
        barcodeListContext = list
        barcodeListIndex = index
        loadBarcode(list[index].code)
    }

    fun navigateToBarcodeInList(index: Int) {
        val list = barcodeListContext ?: return
        if (index !in list.indices) return
        barcodeHistory = emptyList()
        barcodeForwardHistory = emptyList()
        barcodeListIndex = index
        loadBarcode(list[index].code)
    }

    fun clearBarcodeListContext() { barcodeListContext = null }

    fun toggleBarcodeSelection(code: String) {
        selectedBarcodes = if (code in selectedBarcodes) selectedBarcodes - code else selectedBarcodes + code
    }

    fun clearBarcodeSelection() {
        selectedBarcodes = emptySet()
        loanState = UiState()
    }

    fun previewLoan(returnDate: String?) = viewModelScope.launch {
        loanState = UiState(isLoading = true)
        runCatching { repo.loanBarcodes(selectedBarcodes.toList(), returnDate, preview = true) }
            .onSuccess { loanState = UiState(data = it) }
            .onFailure { loanState = UiState(error = it.localizedMessage) }
    }

    fun confirmLoan(returnDate: String?) = viewModelScope.launch {
        loanState = UiState(isLoading = true)
        runCatching { repo.loanBarcodes(selectedBarcodes.toList(), returnDate, preview = false) }
            .onSuccess {
                loanState = UiState(data = it)
                if (it.message?.contains("created") == true) {
                    selectedBarcodes = emptySet()
                    loadBarcodes()
                    loadMyLoans()
                }
            }
            .onFailure { loanState = UiState(error = it.localizedMessage) }
    }
}
