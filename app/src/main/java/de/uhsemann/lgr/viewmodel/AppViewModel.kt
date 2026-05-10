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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class UiState<T>(
    val data: T? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

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
    var childBarcodes by mutableStateOf(UiState<List<Barcode>>())
        private set
    var scannedChildCodes by mutableStateOf<Set<String>>(emptySet())
        private set
    var newScannedBarcodes by mutableStateOf<List<Barcode>>(emptyList())
        private set
    var contentScanActive by mutableStateOf(false)
        private set
    var saveContentState by mutableStateOf(UiState<Unit>())
        private set
    var barcodesSearch by mutableStateOf("")
        private set
    var barcodeListContext by mutableStateOf<List<Barcode>?>(null)
        private set
    var barcodeListIndex by mutableStateOf(0)
        private set

    val isAuthenticated get() = auth.data?.authenticated == true
    val username get() = auth.data?.username

    private var searchJob: Job? = null
    private val ownerNameCache = mutableMapOf<String, String>()
    private val barcodeByUrlCache = mutableMapOf<String, Barcode>()

    suspend fun resolveLocation(barcode: Barcode): List<Barcode> {
        val ancestors = mutableListOf<Barcode>()
        var parentUrl = barcode.parent
        var depth = 0
        while (parentUrl != null && depth < 20) {
            val parent = barcodeByUrlCache[parentUrl]
                ?: runCatching { repo.getBarcodeByUrl(parentUrl!!) }
                    .getOrNull()
                    ?.also { barcodeByUrlCache[parentUrl!!] = it }
                ?: break
            ancestors.add(0, parent)
            parentUrl = parent.parent
            depth++
        }
        return ancestors
    }

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

    fun resetLoanState() {
        loanState = UiState()
    }

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
                .onSuccess {
                    items = UiState(data = it.results)
                    itemsNextPage = it.next
                }
                .onFailure { items = UiState(error = it.localizedMessage) }
        }
    }

    fun loadMoreItems() {
        val next = itemsNextPage ?: return
        viewModelScope.launch {
            runCatching { repo.getItemsPage(next) }
                .onSuccess {
                    items = UiState(data = (items.data ?: emptyList()) + it.results)
                    itemsNextPage = it.next
                }
        }
    }

    fun loadBarcodes(search: String? = null) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!search.isNullOrBlank()) delay(300)
            barcodes = UiState(isLoading = true)
            runCatching { repo.getBarcodes(search) }
                .onSuccess {
                    barcodes = UiState(data = it.results)
                    barcodesNextPage = it.next
                }
                .onFailure { barcodes = UiState(error = it.localizedMessage) }
        }
    }

    fun loadMoreBarcodes() {
        val next = barcodesNextPage ?: return
        viewModelScope.launch {
            runCatching { repo.getBarcodesPage(next) }
                .onSuccess {
                    barcodes = UiState(data = (barcodes.data ?: emptyList()) + it.results)
                    barcodesNextPage = it.next
                }
        }
    }

    fun loadPersons(search: String? = null) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!search.isNullOrBlank()) delay(300)
            persons = UiState(isLoading = true)
            runCatching { repo.getPersons(search) }
                .onSuccess {
                    persons = UiState(data = it.results)
                    personsNextPage = it.next
                }
                .onFailure { persons = UiState(error = it.localizedMessage) }
        }
    }

    fun loadMorePersons() {
        val next = personsNextPage ?: return
        viewModelScope.launch {
            runCatching { repo.getPersonsPage(next) }
                .onSuccess {
                    persons = UiState(data = (persons.data ?: emptyList()) + it.results)
                    personsNextPage = it.next
                }
        }
    }

    fun loadLoans() = viewModelScope.launch {
        loans = UiState(isLoading = true)
        runCatching { repo.getLoans() }
            .onSuccess {
                loans = UiState(data = it.results)
                loansNextPage = it.next
            }
            .onFailure { loans = UiState(error = it.localizedMessage) }
    }

    fun loadMoreLoans() {
        val next = loansNextPage ?: return
        viewModelScope.launch {
            runCatching { repo.getLoansPage(next) }
                .onSuccess {
                    loans = UiState(data = (loans.data ?: emptyList()) + it.results)
                    loansNextPage = it.next
                }
        }
    }

    fun loadMyLoans() = viewModelScope.launch {
        myLoans = UiState(isLoading = true)
        runCatching { repo.getMyLoans() }
            .onSuccess {
                myLoans = UiState(data = it.results)
                myLoansNextPage = it.next
            }
            .onFailure { myLoans = UiState(error = it.localizedMessage) }
    }

    fun loadMoreMyLoans() {
        val next = myLoansNextPage ?: return
        viewModelScope.launch {
            runCatching { repo.getMyLoansPage(next) }
                .onSuccess {
                    myLoans = UiState(data = (myLoans.data ?: emptyList()) + it.results)
                    myLoansNextPage = it.next
                }
        }
    }

    fun loadBarcode(code: String) = viewModelScope.launch {
        scannedBarcode = UiState(isLoading = true)
        childBarcodes = UiState()
        scannedChildCodes = emptySet()
        newScannedBarcodes = emptyList()
        contentScanActive = false
        saveContentState = UiState()
        runCatching { repo.getBarcode(code) }
            .onSuccess {
                scannedBarcode = UiState(data = it)
                loadChildBarcodes(it.url)
            }
            .onFailure { scannedBarcode = UiState(error = it.localizedMessage) }
    }

    private fun loadChildBarcodes(parentUrl: String) = viewModelScope.launch {
        childBarcodes = UiState(isLoading = true)
        runCatching { repo.getChildBarcodes(parentUrl) }
            .onSuccess { childBarcodes = UiState(data = it) }
            .onFailure { childBarcodes = UiState(error = it.localizedMessage) }
    }

    fun clearScannedBarcode() { scannedBarcode = UiState() }

    fun startContentScan() {
        scannedChildCodes = emptySet()
        newScannedBarcodes = emptyList()
        contentScanActive = true
        saveContentState = UiState()
    }

    fun onContentBarcodeScanned(code: String) = viewModelScope.launch {
        val currentBarcode = scannedBarcode.data ?: return@launch
        if (code in scannedChildCodes || newScannedBarcodes.any { it.code == code }) return@launch
        val b = runCatching { repo.getBarcode(code) }.getOrNull() ?: return@launch
        if (b.parent == currentBarcode.url) {
            scannedChildCodes = scannedChildCodes + code
        } else {
            newScannedBarcodes = newScannedBarcodes + b
        }
    }

    fun saveContentChanges(parentBarcode: Barcode) = viewModelScope.launch {
        saveContentState = UiState(isLoading = true)
        val children = childBarcodes.data ?: return@launch
        val errors = mutableListOf<String>()

        for (b in newScannedBarcodes) {
            runCatching { repo.patchBarcodeParent(b.url, parentBarcode.url) }
                .onFailure { errors.add(it.localizedMessage ?: b.code) }
        }
        for (b in children.filter { it.code !in scannedChildCodes }) {
            runCatching { repo.patchBarcodeParent(b.url, null) }
                .onFailure { errors.add(it.localizedMessage ?: b.code) }
        }

        if (errors.isEmpty()) {
            saveContentState = UiState(data = Unit)
            scannedChildCodes = emptySet()
            newScannedBarcodes = emptyList()
            contentScanActive = false
            loadChildBarcodes(parentBarcode.url)
        } else {
            saveContentState = UiState(error = errors.joinToString("\n"))
        }
    }

    fun updateBarcodesSearch(query: String) {
        barcodesSearch = query
        loadBarcodes(query)
    }

    fun openBarcodeFromList(list: List<Barcode>, index: Int) {
        barcodeListContext = list
        barcodeListIndex = index
        loadBarcode(list[index].code)
    }

    fun navigateToBarcodeInList(index: Int) {
        val list = barcodeListContext ?: return
        if (index !in list.indices) return
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
