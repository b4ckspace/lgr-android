package de.backspace.lgr.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.backspace.lgr.data.api.ApiClient
import de.backspace.lgr.data.model.*
import de.backspace.lgr.data.repository.LgrRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "lgr_settings")
private val FILTERS_EXPANDED_KEY = booleanPreferencesKey("filters_expanded")

data class UiState<T>(
    val data: T? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

enum class ScanResult { FOUND_NEW, FOUND_EXISTING, DUPLICATE, NOT_FOUND }

enum class VerifyPhase { LOCATION, CONTENT }

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("lgr_prefs", Context.MODE_PRIVATE)
    private val repo = LgrRepository()

    var serverUrl by mutableStateOf(prefs.getString("server_url", "") ?: "")
        private set

    var auth by mutableStateOf(UiState<AuthStatus>())
    var items by mutableStateOf(UiState<List<Item>>())
    var itemsNextPage by mutableStateOf<String?>(null)
    var itemsCount by mutableStateOf<Int?>(null)
        private set
    var barcodes by mutableStateOf(UiState<List<Barcode>>())
    var barcodesNextPage by mutableStateOf<String?>(null)
    var barcodesCount by mutableStateOf<Int?>(null)
        private set
    var persons by mutableStateOf(UiState<List<Person>>())
    var personsNextPage by mutableStateOf<String?>(null)
    var personsCount by mutableStateOf<Int?>(null)
        private set
    var loans by mutableStateOf(UiState<List<Loan>>())
    var loansNextPage by mutableStateOf<String?>(null)
    var myLoans by mutableStateOf(UiState<List<Loan>>())
    var myLoansNextPage by mutableStateOf<String?>(null)
    var tags by mutableStateOf(UiState<List<Tag>>())

    var selectedBarcodes by mutableStateOf<Set<String>>(emptySet())
    var loanState by mutableStateOf(UiState<LoanResponse>())
    var deleteBarcodeState by mutableStateOf(UiState<Unit>())
        private set
    var currentItem by mutableStateOf<Item?>(null)
        private set
    var itemBarcodes by mutableStateOf(UiState<List<Barcode>>())
        private set
    var deleteItemState by mutableStateOf(UiState<Unit>())
        private set
    var itemListContext by mutableStateOf<List<Item>?>(null)
        private set
    var itemListIndex by mutableStateOf(0)
        private set
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
    var contentScanDoneTrigger by mutableStateOf(0)
        private set
    var childLoanInfos by mutableStateOf<Map<String, LoanInfo>>(emptyMap())
        private set
    var barcodesSearch by mutableStateOf("")
        private set
    var barcodesNoParentFilter by mutableStateOf(false)
        private set
    var selectedOwners by mutableStateOf<List<Person>>(emptyList())
        private set
    var ownerSearchQuery by mutableStateOf("")
        private set
    var ownerSuggestions by mutableStateOf<List<Person>>(emptyList())
        private set
    var filtersExpanded by mutableStateOf(false)
        private set
    private var ownerSearchJob: Job? = null
    private var barcodesReturnFromDetail = false
    private var barcodesNeedRefresh = true
    var barcodesGeneration by mutableStateOf(0)
        private set
    var itemsNoBarcodeFilter by mutableStateOf(false)
        private set
    private var itemsNeedRefresh = true
    var itemsSearch by mutableStateOf("")
        private set
    var itemsGeneration by mutableStateOf(0)
        private set
    private var personsNeedRefresh = true
    var personsSearch by mutableStateOf("")
        private set
    var personsGeneration by mutableStateOf(0)
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
    var supportsImages by mutableStateOf(prefs.getBoolean("supports_images", false))
        private set
    var newBarcodePendingImageBytes by mutableStateOf<ByteArray?>(null)
        private set
    var editBarcodePendingImageBytes by mutableStateOf<ByteArray?>(null)
        private set
    var editBarcodeDeleteImage by mutableStateOf(false)
        private set
    var editItemPendingImageBytes by mutableStateOf<ByteArray?>(null)
        private set
    var editItemDeleteImage by mutableStateOf(false)
        private set

    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(getApplication())
            .okHttpClient(
                okhttp3.OkHttpClient.Builder()
                    .cookieJar(ApiClient.cookieJar)
                    .build()
            )
            .diskCache(
                DiskCache.Builder()
                    .directory(getApplication<Application>().cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            )
            .build()
    }

    var verifyLocation by mutableStateOf<Barcode?>(null)
        private set
    var verifyContents by mutableStateOf<List<Barcode>>(emptyList())
        private set
    var verifyPhase by mutableStateOf(VerifyPhase.LOCATION)
        private set
    var verifyState by mutableStateOf(UiState<Unit>())
        private set
    var newBarcodeState by mutableStateOf(UiState<Barcode>())
        private set
    var newBarcodeCode by mutableStateOf("")
    var newBarcodeNameQuery by mutableStateOf("")
    var newBarcodeSelectedItem by mutableStateOf<Item?>(null)
    var newBarcodeItemDescription by mutableStateOf("")
    var newBarcodeDescription by mutableStateOf("")
    var newBarcodeParentCode by mutableStateOf("")
    var newBarcodeOwnerQuery by mutableStateOf("")
    var newBarcodeOwnerUrl by mutableStateOf<String?>(null)
    var newBarcodeSelectedPerson by mutableStateOf<Person?>(null)

    var editItemNameQuery by mutableStateOf("")
    var editItemDescription by mutableStateOf("")
    var saveItemEditState by mutableStateOf(UiState<Item>())
        private set

    var editBarcodeNameQuery by mutableStateOf("")
    var editBarcodeSelectedItem by mutableStateOf<Item?>(null)
    var editBarcodeItemDescription by mutableStateOf("")
    var editBarcodeDescription by mutableStateOf("")
    var editBarcodeOwnerQuery by mutableStateOf("")
    var editBarcodeOwnerUrl by mutableStateOf<String?>(null)
    var editBarcodeSelectedPerson by mutableStateOf<Person?>(null)
    var editBarcodeLocationQuery by mutableStateOf("")
    var saveBarcodeEditState by mutableStateOf(UiState<Barcode>())
        private set

    val isAuthenticated get() = auth.data?.authenticated == true
    val username get() = auth.data?.username

    private var searchJob: Job? = null
    private var childLoanJob: Job? = null
    private val ownerNameCache = mutableMapOf<String, String>()
    private val barcodeScrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    fun saveScrollPosition(code: String, index: Int, offset: Int) {
        barcodeScrollPositions[code] = index to offset
    }

    fun getScrollPosition(code: String): Pair<Int, Int> =
        barcodeScrollPositions[code] ?: (0 to 0)

    suspend fun resolveOwnerName(url: String): String {
        ownerNameCache[url]?.takeIf { it != url }?.let { return it }
        val id = url.trimEnd('/').substringAfterLast('/')
        val rewritten = ApiClient.getPersonUrl(id)
        val name = runCatching { repo.getPersonByUrl(rewritten) }
            .getOrNull()
            ?.let { p ->
                listOf(p.firstname, p.lastname)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { p.nickname }
            }
            ?: url
        ownerNameCache[url] = name
        return name
    }

    init {
        if (serverUrl.isNotEmpty()) {
            ApiClient.configure(serverUrl)
            checkAuth()
        }
        viewModelScope.launch {
            application.appDataStore.data
                .catch { emit(emptyPreferences()) }
                .first()[FILTERS_EXPANDED_KEY]?.let { filtersExpanded = it }
        }
    }

    fun applyServerUrl(url: String) {
        serverUrl = url
        prefs.edit().putString("server_url", url).apply()
        ApiClient.reset()
        ApiClient.configure(url)
    }

    fun applySupportsImages(value: Boolean) {
        supportsImages = value
        prefs.edit().putBoolean("supports_images", value).apply()
    }

    fun setNewBarcodePendingImage(bytes: ByteArray?) { newBarcodePendingImageBytes = bytes }
    fun setEditBarcodePendingImage(bytes: ByteArray?) {
        editBarcodePendingImageBytes = bytes
        if (bytes != null) editBarcodeDeleteImage = false
    }
    fun applyEditBarcodeDeleteImage(value: Boolean) { editBarcodeDeleteImage = value }
    fun setEditItemPendingImage(bytes: ByteArray?) {
        editItemPendingImageBytes = bytes
        if (bytes != null) editItemDeleteImage = false
    }
    fun applyEditItemDeleteImage(value: Boolean) { editItemDeleteImage = value }

    fun clearNewBarcodeState() {
        newBarcodeState = UiState()
        newBarcodeCode = ""
        newBarcodeNameQuery = ""
        newBarcodeSelectedItem = null
        newBarcodeItemDescription = ""
        newBarcodeDescription = ""
        newBarcodeParentCode = ""
        newBarcodeSelectedPerson = null
        newBarcodePendingImageBytes = null
        clearPendingNewParent()
        val savedDisplay = prefs.getString("last_owner_display", null)
        val savedUrl = prefs.getString("last_owner_url", null)
        if (savedDisplay != null) {
            newBarcodeOwnerQuery = savedDisplay
            newBarcodeOwnerUrl = savedUrl
        } else {
            newBarcodeOwnerQuery = ""
            newBarcodeOwnerUrl = null
            fillOwnerWithCurrentUser()
        }
    }

    fun fillEditOwnerWithCurrentUser() = viewModelScope.launch {
        val uname = username ?: return@launch
        val results = runCatching { repo.getPersons(search = uname, limit = 10) }
            .getOrNull()?.results ?: return@launch
        val person = results.find { it.nickname == uname } ?: results.firstOrNull() ?: return@launch
        val display = listOf(person.firstname, person.lastname)
            .filter { it.isNotBlank() }.joinToString(" ").ifBlank { person.nickname }
        editBarcodeOwnerQuery = display
        editBarcodeOwnerUrl = person.url
        editBarcodeSelectedPerson = person
    }

    fun enterBarcodeEditMode(barcode: Barcode) {
        saveBarcodeEditState = UiState()
        editBarcodeNameQuery = barcode.itemName
        editBarcodeSelectedItem = Item(url = barcode.item, name = barcode.itemName, description = barcode.itemDescription, tags = emptyList())
        editBarcodeItemDescription = barcode.itemDescription
        editBarcodeDescription = barcode.description
        editBarcodeOwnerUrl = barcode.owner
        editBarcodeSelectedPerson = null
        editBarcodeOwnerQuery = ""
        editBarcodeLocationQuery = barcode.apiParentNames?.lastOrNull()?.code ?: ""
        if (barcode.owner != null) {
            viewModelScope.launch {
                editBarcodeOwnerQuery = resolveOwnerName(barcode.owner)
            }
        }
    }

    fun saveBarcodeEdit() = viewModelScope.launch {
        val barcode = scannedBarcode.data ?: return@launch
        saveBarcodeEditState = UiState(isLoading = true)
        val itemName = editBarcodeNameQuery.trim()
        val selectedItem = editBarcodeSelectedItem
        val itemDescription = editBarcodeItemDescription.trim()
        val description = editBarcodeDescription.trim()
        val ownerUrl = editBarcodeOwnerUrl
        val pendingImageBytes = editBarcodePendingImageBytes
        val itemUrl = if (selectedItem != null) {
            selectedItem.url
        } else {
            val existing = runCatching { repo.getItems(search = itemName, limit = 50) }
                .getOrNull()?.results?.find { it.name.equals(itemName, ignoreCase = true) }
            existing?.url ?: runCatching { repo.createItem(itemName, itemDescription) }
                .getOrElse { saveBarcodeEditState = UiState(error = it.toUserMessage()); return@launch }
                .url
        }
        runCatching {
            repo.updateBarcode(ApiClient.getBarcodeUrl(barcode.code), itemUrl, description, ownerUrl)
        }.onSuccess { updated ->
            val locationCode = editBarcodeLocationQuery.trim()
            val parentUrl = if (locationCode.isNotBlank()) ApiClient.getBarcodeUrl(locationCode) else null
            runCatching { repo.patchBarcodeParent(ApiClient.getBarcodeUrl(barcode.code), parentUrl) }
            if (pendingImageBytes != null && supportsImages) {
                runCatching { repo.uploadItemImage(itemUrl, pendingImageBytes) }
            } else if (editBarcodeDeleteImage && supportsImages) {
                runCatching { repo.clearItemImage(itemUrl) }
            }
            val refreshed = runCatching { repo.getBarcode(barcode.code) }.getOrNull() ?: updated
            scannedBarcode = UiState(data = refreshed)
            saveBarcodeEditState = UiState(data = refreshed)
            barcodesNeedRefresh = true
            itemsNeedRefresh = true
            refreshItemDetail()
        }.onFailure {
            saveBarcodeEditState = UiState(error = it.toUserMessage())
        }
    }

    fun clearBarcodeEditState() {
        saveBarcodeEditState = UiState()
        editBarcodeNameQuery = ""
        editBarcodeSelectedItem = null
        editBarcodeItemDescription = ""
        editBarcodeDescription = ""
        editBarcodeOwnerQuery = ""
        editBarcodeOwnerUrl = null
        editBarcodeSelectedPerson = null
        editBarcodeLocationQuery = ""
        editBarcodePendingImageBytes = null
        editBarcodeDeleteImage = false
    }

    fun fillOwnerWithCurrentUser() = viewModelScope.launch {
        val uname = username ?: return@launch
        val results = runCatching { repo.getPersons(search = uname, limit = 10) }
            .getOrNull()?.results ?: return@launch
        val person = results.find { it.nickname == uname } ?: results.firstOrNull() ?: return@launch
        val display = listOf(person.firstname, person.lastname)
            .filter { it.isNotBlank() }.joinToString(" ").ifBlank { person.nickname }
        newBarcodeOwnerQuery = display
        newBarcodeOwnerUrl = person.url
        newBarcodeSelectedPerson = person
    }

    suspend fun searchPersons(query: String): List<Person> {
        if (query.isBlank()) return emptyList()
        return runCatching { repo.getPersons(search = query, limit = 10) }
            .getOrNull()?.results ?: emptyList()
    }

    suspend fun searchBarcodes(query: String): List<Barcode> {
        if (query.isBlank()) return emptyList()
        val results = runCatching { repo.getBarcodes(search = query, limit = 20) }
            .getOrNull()?.results ?: return emptyList()
        val q = query.lowercase()
        return results.sortedWith(compareBy(
            { if (it.code.lowercase().startsWith(q)) 0 else if (it.code.lowercase().contains(q)) 1 else 2 },
            { it.itemName.lowercase() }
        ))
    }

    fun onNewBarcodeCodeScanned(code: String) {
        newBarcodeCode = code
    }

    fun onNewBarcodeParentScanned() {
        newBarcodeParentCode = pendingNewParent?.code ?: ""
        clearPendingNewParent()
    }

    fun onEditBarcodeParentScanned() {
        editBarcodeLocationQuery = pendingNewParent?.code ?: ""
        clearPendingNewParent()
    }

    suspend fun isBarcodeNew(code: String): Boolean {
        return try {
            repo.getBarcode(code)
            false
        } catch (e: retrofit2.HttpException) {
            e.code() == 404
        } catch (_: Exception) {
            false
        }
    }

    suspend fun searchItemsWithCounts(query: String): List<Pair<Item, Int>> {
        if (query.isBlank()) return emptyList()
        val items = runCatching { repo.getItems(search = query, limit = 10) }
            .getOrNull()?.results ?: return emptyList()
        return coroutineScope {
            items.map { item ->
                async { item to repo.getBarcodeCountForItem(item.name) }
            }.awaitAll()
        }
    }

    fun createNewBarcode() = viewModelScope.launch {
        newBarcodeState = UiState(isLoading = true)
        val code = newBarcodeCode.trim()
        val itemName = newBarcodeNameQuery.trim()
        val selectedItem = newBarcodeSelectedItem
        val itemDescription = newBarcodeItemDescription.trim()
        val description = newBarcodeDescription.trim()
        val parentCode = newBarcodeParentCode.trim()
        val ownerQuery = newBarcodeOwnerQuery
        val ownerUrl = newBarcodeOwnerUrl
        val pendingImageBytes = newBarcodePendingImageBytes

        val itemUrl = if (selectedItem != null) {
            selectedItem.url
        } else {
            val existing = runCatching { repo.getItems(search = itemName, limit = 50) }
                .getOrNull()?.results?.find { it.name.equals(itemName, ignoreCase = true) }
            existing?.url ?: runCatching { repo.createItem(itemName, itemDescription) }
                .getOrElse { newBarcodeState = UiState(error = it.localizedMessage); return@launch }
                .url
        }

        val parentUrl = if (parentCode.isNotBlank()) ApiClient.getBarcodeUrl(parentCode) else null

        runCatching {
            repo.createBarcode(CreateBarcodeRequest(code = code, item = itemUrl, description = description, owner = ownerUrl, parent = parentUrl))
        }.onSuccess { barcode ->
            prefs.edit().apply {
                putString("last_owner_display", ownerQuery)
                if (ownerUrl != null) putString("last_owner_url", ownerUrl) else remove("last_owner_url")
                apply()
            }
            val imageUploaded = pendingImageBytes != null && supportsImages &&
                runCatching { repo.uploadItemImage(itemUrl, pendingImageBytes) }.isSuccess
            val refreshed = if (imageUploaded) runCatching { repo.getBarcode(code) }.getOrNull() ?: barcode else barcode
            scannedBarcode = UiState(data = refreshed)
            barcodeHistory = emptyList()
            barcodeForwardHistory = emptyList()
            barcodeListContext = null
            childLoanInfos = emptyMap()
            contentScanActive = false
            addContentScanActive = false
            saveContentState = UiState()
            pendingNewParent = null
            saveParentState = UiState()
            barcodesNeedRefresh = true
            itemsNeedRefresh = true
            newBarcodeState = UiState(data = barcode)
        }.onFailure {
            newBarcodeState = UiState(error = it.toUserMessage())
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

    fun clearVerifyState() {
        verifyLocation = null
        verifyContents = emptyList()
        verifyPhase = VerifyPhase.LOCATION
        verifyState = UiState()
    }

    fun startVerifyContentRescan() {
        verifyContents = emptyList()
        verifyPhase = VerifyPhase.CONTENT
        verifyState = UiState()
    }

    suspend fun onVerifyBarcodeScanned(code: String): ScanResult {
        return when (verifyPhase) {
            VerifyPhase.LOCATION -> {
                val barcode = runCatching { repo.getBarcode(code) }.getOrNull()
                    ?: return ScanResult.NOT_FOUND
                verifyLocation = barcode
                verifyPhase = VerifyPhase.CONTENT
                ScanResult.FOUND_NEW
            }
            VerifyPhase.CONTENT -> {
                if (verifyContents.any { it.code == code }) return ScanResult.DUPLICATE
                val barcode = runCatching { repo.getBarcode(code) }.getOrNull()
                    ?: return ScanResult.NOT_FOUND
                val isExistingChild = verifyLocation?.apiChildNames?.any { it.code == code } == true
                verifyContents = verifyContents + barcode
                if (isExistingChild) ScanResult.FOUND_EXISTING else ScanResult.FOUND_NEW
            }
        }
    }

    fun saveVerifyChanges() = viewModelScope.launch {
        val location = verifyLocation ?: return@launch
        verifyState = UiState(isLoading = true)
        val dbChildren = location.apiChildNames ?: emptyList()
        val scannedCodes = verifyContents.map { it.code }.toSet()
        val dbCodes = dbChildren.map { it.code }.toSet()
        val redCodes = dbCodes - scannedCodes
        val greenBarcodes = verifyContents.filter { it.code !in dbCodes }
        val errors = mutableListOf<String>()

        for (code in redCodes) {
            runCatching { repo.patchBarcodeParent(ApiClient.getBarcodeUrl(code), null) }
                .onFailure { errors.add(it.localizedMessage ?: code) }
        }
        val parentUrl = ApiClient.getBarcodeUrl(location.code)
        for (b in greenBarcodes) {
            runCatching { repo.patchBarcodeParent(ApiClient.getBarcodeUrl(b.code), parentUrl) }
                .onFailure { errors.add(it.localizedMessage ?: b.code) }
        }

        if (errors.isEmpty()) {
            val keptChildren = dbChildren.filter { it.code in scannedCodes }
            val addedChildren = greenBarcodes.map { b -> ChildInfo(name = "${b.itemName} (${b.code})", code = b.code) }
            verifyLocation = location.copy(apiChildNames = keptChildren + addedChildren)
            verifyState = UiState(data = Unit)
        } else {
            verifyState = UiState(error = errors.joinToString("\n"))
        }
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
            .onFailure { e ->
                val msg = if (e is retrofit2.HttpException && e.code() == 400)
                    "Invalid username or password."
                else
                    e.localizedMessage
                auth = UiState(error = msg)
            }
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

    fun loadItems(search: String? = null, noBarcodes: Boolean = itemsNoBarcodeFilter) {
        if (search != null) itemsSearch = search
        if (search == null && !itemsNeedRefresh && items.data != null) return
        itemsNeedRefresh = false
        itemsGeneration++

        val effectiveSearch = search ?: itemsSearch.takeIf { it.isNotBlank() }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!search.isNullOrBlank()) delay(300)
            items = UiState(isLoading = true)
            runCatching { repo.getItems(effectiveSearch, noBarcodes = noBarcodes) }
                .onSuccess { items = UiState(data = it.results); itemsNextPage = it.next; itemsCount = it.count }
                .onFailure { items = UiState(error = it.localizedMessage) }
        }
    }

    fun toggleItemsNoBarcodeFilter() {
        itemsNoBarcodeFilter = !itemsNoBarcodeFilter
        itemsNeedRefresh = true
        loadItems()
    }

    fun loadMoreItems() {
        val next = itemsNextPage ?: return
        viewModelScope.launch {
            runCatching { repo.getItemsPage(next) }
                .onSuccess { items = UiState(data = (items.data ?: emptyList()) + it.results); itemsNextPage = it.next }
        }
    }

    fun loadBarcodes(search: String? = null, noParent: Boolean = barcodesNoParentFilter) {
        val returning = barcodesReturnFromDetail
        barcodesReturnFromDetail = false
        if (returning && barcodes.data != null && !barcodesNeedRefresh) return
        if (search == null && !barcodesNeedRefresh && barcodes.data != null) return
        barcodesNeedRefresh = false
        barcodesGeneration++

        val textSearch = (search ?: barcodesSearch).takeIf { it.isNotBlank() }
        // filterset_fields uses ModelChoiceFilter which expects the numeric PK, not the full URL
        val ownerIds = selectedOwners.map { it.url.trimEnd('/').substringAfterLast('/') }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!search.isNullOrBlank()) delay(300)
            barcodes = UiState(isLoading = true)
            if (ownerIds.size <= 1) {
                runCatching { repo.getBarcodes(textSearch, noParent = noParent, owner = ownerIds.firstOrNull()) }
                    .onSuccess { barcodes = UiState(data = it.results); barcodesNextPage = it.next; barcodesCount = it.count }
                    .onFailure { barcodes = UiState(error = it.localizedMessage) }
            } else {
                try {
                    coroutineScope {
                        val jobs = ownerIds.map { ownerId ->
                            async { repo.getBarcodes(textSearch, limit = 200, noParent = noParent, owner = ownerId) }
                        }
                        val results = jobs.awaitAll()
                        val merged = results.flatMap { it.results }.distinctBy { it.code }.sortedBy { it.code }
                        barcodes = UiState(data = merged)
                        barcodesNextPage = null
                        barcodesCount = results.sumOf { it.count }
                    }
                } catch (e: Exception) {
                    barcodes = UiState(error = e.localizedMessage)
                }
            }
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
        if (search != null) personsSearch = search
        if (search == null && !personsNeedRefresh && persons.data != null) return
        personsNeedRefresh = false
        personsGeneration++

        val effectiveSearch = search ?: personsSearch.takeIf { it.isNotBlank() }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!search.isNullOrBlank()) delay(300)
            persons = UiState(isLoading = true)
            runCatching { repo.getPersons(effectiveSearch) }
                .onSuccess { persons = UiState(data = it.results); personsNextPage = it.next; personsCount = it.count }
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

    fun refreshPersons() {
        personsNeedRefresh = true
        loadPersons()
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

    fun deleteBarcode(code: String) = viewModelScope.launch {
        deleteBarcodeState = UiState(isLoading = true)
        runCatching { repo.deleteBarcode(code) }
            .onSuccess {
                deleteBarcodeState = UiState(data = Unit)
                barcodesReturnFromDetail = false
                barcodesNeedRefresh = true
                refreshItemDetail()
            }
            .onFailure { deleteBarcodeState = UiState(error = it.toUserMessage()) }
    }

    fun resetDeleteBarcodeState() { deleteBarcodeState = UiState() }

    fun openItemDetail(item: Item) {
        currentItem = item
        deleteItemState = UiState()
        itemListContext = null
        itemBarcodes = UiState(isLoading = true)
        viewModelScope.launch {
            runCatching { repo.getBarcodesByItem(item.name) }
                .onSuccess { itemBarcodes = UiState(data = it) }
                .onFailure { itemBarcodes = UiState(error = it.localizedMessage) }
        }
    }

    fun openItemFromList(list: List<Item>, index: Int) {
        openItemDetail(list[index])
        itemListContext = list
        itemListIndex = index
    }

    fun navigateToItemInList(index: Int) {
        val list = itemListContext ?: return
        if (index !in list.indices) return
        val item = list[index]
        currentItem = item
        deleteItemState = UiState()
        itemListIndex = index
        itemBarcodes = UiState(isLoading = true)
        viewModelScope.launch {
            runCatching { repo.getBarcodesByItem(item.name) }
                .onSuccess { itemBarcodes = UiState(data = it) }
                .onFailure { itemBarcodes = UiState(error = it.localizedMessage) }
        }
    }

    fun deleteItem() = viewModelScope.launch {
        val item = currentItem ?: return@launch
        deleteItemState = UiState(isLoading = true)
        runCatching { repo.deleteItem(item.url) }
            .onSuccess { deleteItemState = UiState(data = Unit); itemsNeedRefresh = true }
            .onFailure { deleteItemState = UiState(error = it.toUserMessage()) }
    }

    fun resetDeleteItemState() { deleteItemState = UiState() }

    fun enterItemEditMode(item: Item) {
        saveItemEditState = UiState()
        editItemNameQuery = item.name
        editItemDescription = item.description
    }

    fun saveItemEdit() = viewModelScope.launch {
        val item = currentItem ?: return@launch
        val pendingImageBytes = editItemPendingImageBytes
        saveItemEditState = UiState(isLoading = true)
        runCatching {
            repo.updateItem(item.url, editItemNameQuery.trim(), editItemDescription.trim())
        }.onSuccess { updated ->
            val finalItem = if (pendingImageBytes != null && supportsImages) {
                runCatching { repo.uploadItemImage(updated.url, pendingImageBytes) }.getOrNull() ?: updated
            } else if (editItemDeleteImage && supportsImages) {
                val oldImageUrl = updated.image
                val cleared = runCatching { repo.clearItemImage(updated.url) }.getOrNull() ?: updated
                if (oldImageUrl != null) {
                    imageLoader.memoryCache?.remove(MemoryCache.Key(oldImageUrl))
                    imageLoader.diskCache?.remove(oldImageUrl)
                }
                cleared
            } else {
                updated
            }
            currentItem = finalItem
            saveItemEditState = UiState(data = finalItem)
            itemsNeedRefresh = true
            barcodesNeedRefresh = true
            val barcodeCode = scannedBarcode.data?.let { if (it.item == item.url) it.code else null }
            if (barcodeCode != null) {
                val refreshed = runCatching { repo.getBarcode(barcodeCode) }.getOrNull()
                if (refreshed != null) scannedBarcode = UiState(data = refreshed)
            }
        }.onFailure {
            saveItemEditState = UiState(error = it.toUserMessage())
        }
    }

    fun clearItemEditState() {
        saveItemEditState = UiState()
        editItemNameQuery = ""
        editItemDescription = ""
        editItemPendingImageBytes = null
        editItemDeleteImage = false
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
                val newParentNames = (parent.apiParentNames ?: emptyList()) + listOf(
                    ChildInfo(name = "${parent.itemName} (${parent.code})", code = parent.code)
                )
                scannedBarcode = UiState(data = barcode.copy(apiParentNames = newParentNames))
                pendingNewParent = null
                saveParentState = UiState(data = Unit)
            }
            .onFailure { saveParentState = UiState(error = it.localizedMessage) }
    }

    // --- Content scanning ---

    fun startContentScan() {
        if (!contentScanActive && !addContentScanActive) {
            scannedChildCodes = emptySet()
            newScannedBarcodes = emptyList()
            saveContentState = UiState()
        }
        contentScanActive = true
        addContentScanActive = false
    }

    fun startAddContentScan() {
        if (!contentScanActive && !addContentScanActive) {
            newScannedBarcodes = emptyList()
            saveContentState = UiState()
        }
        addContentScanActive = true
        contentScanActive = false
    }

    private fun List<Barcode>.sortedByNameThenCode() =
        sortedWith(compareBy({ it.itemName.lowercase() }, { it.code }))

    fun addBarcodeToContent(barcode: Barcode) {
        val currentBarcode = scannedBarcode.data ?: return
        if (newScannedBarcodes.any { it.code == barcode.code }) return
        if (currentBarcode.apiChildNames?.any { it.code == barcode.code } == true) return
        if (!contentScanActive && !addContentScanActive) {
            newScannedBarcodes = emptyList()
            saveContentState = UiState()
        }
        addContentScanActive = true
        newScannedBarcodes = (newScannedBarcodes + barcode).sortedByNameThenCode()
    }

    fun onContentScanDone() { contentScanDoneTrigger++ }

    fun cancelContentScan() {
        scannedChildCodes = emptySet()
        newScannedBarcodes = emptyList()
        contentScanActive = false
        addContentScanActive = false
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
            newScannedBarcodes = (newScannedBarcodes + b).sortedByNameThenCode()
            ScanResult.FOUND_NEW
        }
    }

    suspend fun onAddContentBarcodeScanned(code: String): ScanResult {
        val currentBarcode = scannedBarcode.data ?: return ScanResult.NOT_FOUND
        if (newScannedBarcodes.any { it.code == code }) return ScanResult.DUPLICATE
        if (currentBarcode.apiChildNames?.any { it.code == code } == true) return ScanResult.FOUND_EXISTING
        val b = runCatching { repo.getBarcode(code) }.getOrNull() ?: return ScanResult.NOT_FOUND
        newScannedBarcodes = (newScannedBarcodes + b).sortedByNameThenCode()
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
            scannedChildCodes = emptySet()
            newScannedBarcodes = emptyList()
            contentScanActive = false
            saveContentState = UiState(data = Unit)
            val refreshed = runCatching { repo.getBarcode(parentBarcode.code) }.getOrNull() ?: parentBarcode
            scannedBarcode = UiState(data = refreshed)
            loadChildLoanInfos(refreshed.apiChildNames ?: emptyList())
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
            newScannedBarcodes = emptyList()
            addContentScanActive = false
            saveContentState = UiState(data = Unit)
            val refreshed = runCatching { repo.getBarcode(parentBarcode.code) }.getOrNull() ?: parentBarcode
            scannedBarcode = UiState(data = refreshed)
            loadChildLoanInfos(refreshed.apiChildNames ?: emptyList())
        } else {
            saveContentState = UiState(error = errors.joinToString("\n"))
        }
    }

    fun refreshBarcodes() {
        barcodesNeedRefresh = true
        barcodesReturnFromDetail = false
        loadBarcodes()
    }

    fun refreshItems() {
        itemsNeedRefresh = true
        loadItems()
    }

    fun updateBarcodesSearch(query: String) {
        barcodesSearch = query
        loadBarcodes(query)
    }

    fun toggleBarcodesNoParentFilter() {
        barcodesNoParentFilter = !barcodesNoParentFilter
        loadBarcodes(barcodesSearch)
    }

    fun updateOwnerSearchQuery(query: String) {
        ownerSearchQuery = query
        ownerSearchJob?.cancel()
        if (query.length < 2) {
            ownerSuggestions = emptyList()
            return
        }
        ownerSearchJob = viewModelScope.launch {
            delay(300)
            val results = runCatching { repo.getPersons(search = query, limit = 10) }
                .getOrNull()?.results ?: emptyList()
            ownerSuggestions = results.sortedBy { it.nickname.lowercase() }
        }
    }

    fun selectOnlyOwnerPerson(person: Person) {
        selectedOwners = listOf(person)
        ownerSearchQuery = ""
        ownerSuggestions = emptyList()
        loadBarcodes(barcodesSearch)
    }

    fun toggleOwnerPersonSelection(person: Person) {
        selectedOwners = if (selectedOwners.any { it.url == person.url })
            selectedOwners.filter { it.url != person.url }
        else
            selectedOwners + person
        loadBarcodes(barcodesSearch)
    }

    fun removeOwnerPerson(person: Person) {
        selectedOwners = selectedOwners.filter { it.url != person.url }
        loadBarcodes(barcodesSearch)
    }

    fun clearOwnerFilter() {
        selectedOwners = emptyList()
        ownerSearchQuery = ""
        ownerSuggestions = emptyList()
        loadBarcodes(barcodesSearch)
    }

    fun updateFiltersExpanded(expanded: Boolean) {
        filtersExpanded = expanded
        viewModelScope.launch {
            getApplication<Application>().appDataStore.edit { prefs ->
                prefs[FILTERS_EXPANDED_KEY] = expanded
            }
        }
    }

    fun refreshBarcodeDetail() {
        val code = scannedBarcode.data?.code ?: return
        loadBarcode(code)
    }

    fun refreshItemDetail() {
        val item = currentItem ?: return
        itemBarcodes = UiState(isLoading = true)
        viewModelScope.launch {
            runCatching { repo.getBarcodesByItem(item.name) }
                .onSuccess { itemBarcodes = UiState(data = it) }
                .onFailure { itemBarcodes = UiState(error = it.localizedMessage) }
        }
    }

    fun openBarcodeFromList(list: List<Barcode>, index: Int) {
        barcodesReturnFromDetail = true
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

private fun Throwable.toUserMessage(): String {
    if (this !is retrofit2.HttpException) return localizedMessage ?: toString()
    return try {
        val body = response()?.errorBody()?.string() ?: return "HTTP ${code()}"
        val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
        val map: Map<String, Any> = com.google.gson.Gson().fromJson(body, type)
        map.values.flatMap { value ->
            @Suppress("UNCHECKED_CAST")
            when (value) {
                is List<*> -> value.filterIsInstance<String>()
                is String -> listOf(value)
                else -> emptyList()
            }
        }.firstOrNull() ?: "HTTP ${code()}"
    } catch (_: Exception) {
        localizedMessage ?: "HTTP ${code()}"
    }
}
