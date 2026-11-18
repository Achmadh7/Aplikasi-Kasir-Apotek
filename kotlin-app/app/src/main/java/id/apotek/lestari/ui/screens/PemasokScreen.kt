@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package id.apotek.lestari.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import id.apotek.lestari.data.*
import id.apotek.lestari.ui.theme.*
import kotlinx.coroutines.launch

// Layar Daftar Pemasok PBF (G5, pilihan Achmad 2026-11-12): nama +
// kontak pemasok yang tersimpan, dipakai pemilih di formulir Faktur PBF.
// Menghapus pemasok tidak mengubah catatan pembelian yang sudah ada
// (catatan menyimpan nama sebagai teks).

@Composable
fun PemasokScreen() {
    var suppliers by remember { mutableStateOf<List<Supplier>>(emptyList()) }
    // Pesan kegagalan muat (S11, audit 2026-11-15): tanpa ini, kegagalan
    // muat diam-diam menampilkan daftar kosong.
    var loadError by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var formTarget by remember { mutableStateOf<Supplier?>(null) }
    var formNama by remember { mutableStateOf("") }
    var formKontak by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Supplier?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Muat ulang setiap kali layar kembali tampil (ON_RESUME), pola yang
    // sama seperti Faktur PBF.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeKey) {
        when (val r = repo.listSuppliers()) {
            is ApiResult.Success -> { suppliers = r.data.suppliers; loadError = null }
            is ApiResult.Error -> loadError = r.message
            is ApiResult.Loading -> Unit
        }
    }

    fun bukaFormTambah() {
        formTarget = null
        formNama = ""
        formKontak = ""
        formError = ""
        showForm = true
    }

    fun bukaFormUbah(target: Supplier) {
        formTarget = target
        formNama = target.name
        formKontak = target.contact
        formError = ""
        showForm = true
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(SpaceL)
        ) {
            item {
                KartuHeaderPremium(
                    tr("Pemasok", "Suppliers"),
                    Icons.Default.Store,
                    tr("Daftar PBF untuk Faktur Pembelian", "PBF list for purchase invoices")
                )
                Spacer(Modifier.height(SpaceL))
            }
            // S11: kartu galat muat + tombol coba lagi (pola Beranda S10).
            if (loadError != null) {
                item {
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(tr("Gagal memuat daftar pemasok.", "Failed to load the supplier list."), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(SpaceS))
                            Text(loadError ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(SpaceS))
                            Button(onClick = { loadError = null; resumeKey++ }) { Text(tr("Coba lagi", "Try again")) }
                        }
                    }
                }
            }
            if (suppliers.isEmpty()) {
                item {
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                                Icon(Icons.Default.Store, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(14.dp).size(28.dp))
                            }
                            Spacer(Modifier.height(SpaceM))
                            Text(tr("Belum ada pemasok", "No suppliers yet"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(SpaceXS))
                            Text(tr("Tambah pemasok lewat tombol + di kanan bawah.", "Add a supplier with the + button at the bottom right."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            items(suppliers, key = { it.id }) { s ->
                ExpressiveCard(
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().clickable { bukaFormUbah(s) }
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (s.contact.isNotBlank()) s.contact else tr("Tanpa kontak", "No contact"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { deleteTarget = s }) {
                            Icon(Icons.Default.Delete, tr("Hapus", "Delete"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 88.dp, bottom = 16.dp))
        FloatingActionButton(
            onClick = { bukaFormTambah() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Icon(Icons.Default.Add, tr("Tambah Pemasok", "Add Supplier"))
        }
    }

    if (showForm) {
        val target = formTarget
        // DF1 (2026-11-18, pilihan Achmad): form jadi bottom sheet
        // seperti Tambah Obat (TO1); kedua field dalam 1 kartu PBF,
        // Simpan full-width (CK2) + Batal teks di bawah.
        ModalBottomSheet(
            onDismissRequest = { showForm = false },
            sheetState = sheetState,
            // Lebar sheet adaptif (lihat adaptiveSheetMaxWidth): selebar layar di HP potret, 360dp di HP lanskap, 640dp di tablet.
            sheetMaxWidth = adaptiveSheetMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(SpaceS)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (target == null) tr("Tambah Pemasok", "Add Supplier") else tr("Ubah Pemasok", "Edit Supplier"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showForm = false }) { Icon(Icons.Default.Close, tr("Tutup", "Close")) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(SpaceS)) {
                        Text(tr("PBF", "PBF"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = formNama,
                            onValueChange = { formNama = it },
                            label = { Text(tr("Nama PBF", "PBF Name")) },
                            placeholder = { Text(tr("cth: Kimia Farma", "e.g. Kimia Farma")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = formKontak,
                            onValueChange = { formKontak = it },
                            label = { Text(tr("Kontak (boleh kosong)", "Contact (optional)")) },
                            placeholder = { Text(tr("mis. nomor WA/telepon", "e.g. WhatsApp/phone number")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (formError.isNotEmpty()) Text(formError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Button(
                    onClick = {
                        scope.launch {
                            formError = ""
                            val res = if (target == null) repo.createSupplier(SupplierArgs(formNama, formKontak))
                            else repo.updateSupplier(UpdateSupplierArgs(target.id, formNama, formKontak))
                            if (res is ApiResult.Success) {
                                showForm = false
                                resumeKey++
                                snackbarHostState.showSnackbar(
                                    if (target == null) tr("Pemasok ditambahkan.", "Supplier added.")
                                    else tr("Pemasok diubah.", "Supplier updated.")
                                )
                            } else {
                                formError = (res as? ApiResult.Error)?.message ?: tr("Gagal menyimpan", "Failed to save")
                            }
                        }
                    },
                    enabled = formNama.isNotBlank(),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(tr("Simpan", "Save")) }
                TextButton(onClick = { showForm = false }, modifier = Modifier.fillMaxWidth()) { Text(tr("Batal", "Cancel")) }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(tr("Hapus Pemasok?", "Delete Supplier?")) },
            text = { Text(tr("Pemasok ${target.name} akan dihapus dari daftar. Catatan pembelian yang sudah memakai nama ini tidak berubah.", "Supplier ${target.name} will be removed from the list. Purchase records that already use this name are unchanged.")) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        val res = repo.deleteSupplier(target.id)
                        if (res is ApiResult.Success) {
                            resumeKey++
                            snackbarHostState.showSnackbar(tr("Pemasok dihapus.", "Supplier deleted."))
                        } else {
                            snackbarHostState.showSnackbar((res as? ApiResult.Error)?.message ?: tr("Gagal menghapus", "Failed to delete"))
                        }
                    }
                }) { Text(tr("Hapus", "Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(tr("Batal", "Cancel")) } }
        )
    }
}
