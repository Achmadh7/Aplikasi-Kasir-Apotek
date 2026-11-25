@file:OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package id.apotek.lestari.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.apotek.lestari.data.*
import id.apotek.lestari.ui.theme.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Layar Keuangan dan formulir Catat Kas sebaris (CS4).

@Composable
fun KeuanganScreen() {
    var entries by remember { mutableStateOf<List<CashEntry>>(emptyList()) }
    // Pesan kegagalan muat (S11, audit 2026-11-15): tanpa ini, kegagalan
    // muat entri diam-diam menampilkan angka 0.
    var loadError by remember { mutableStateOf<String?>(null) }
    // R10 (audit 2026-11-15): pilihan periode selamat dari rotasi.
    var period by rememberSaveable { mutableStateOf("today") } // today | month | all — awalnya "Hari Ini" (permintaan Achmad 2026-11-17: menu dibuka langsung di Hari Ini; dulu "Semua" sejak 2026-11-17)
    var typeFilter by rememberSaveable { mutableStateOf("all") } // all | in | out
    var showForm by remember { mutableStateOf(false) }
    var formType by remember { mutableStateOf("in") }
    val scope = rememberCoroutineScope()
    fun reload() {
        scope.launch {
            // S11: kegagalan muat menampilkan kartu galat (bukan angka 0
            // diam-diam); sukses memuat menghapus galat sebelumnya.
            when (val r = repo.listCashEntries()) {
                is ApiResult.Success -> { entries = r.data.entries; loadError = null }
                is ApiResult.Error -> loadError = r.message
                is ApiResult.Loading -> Unit
            }
        }
    }
    // Muat ulang setiap kali layar kembali tampil (ON_RESUME) — pola yang
    // sama seperti Laba/Obat Terlaris/Pengingat/Faktur PBF, supaya data
    // selalu segar setelah ada perubahan dari menu lain.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeKey) { reload() }
    val todayKey = dayKeyID(Date())
    val monthKey = monthKeyID(Date())
    val periodEntries = entries.filter { e ->
        val d = parseISODate(e.createdAt)
        when (period) {
            "today" -> d != null && dayKeyID(d) == todayKey
            "month" -> d != null && monthKeyID(d) == monthKey
            else -> true
        }
    }
    val shown = periodEntries.filter { typeFilter == "all" || it.type == typeFilter }
    // KG1 (2026-11-18, permintaan Achmad): daftar riwayat dikelompokkan per
    // hari seperti Riwayat - header "Hari Ini"/"Kemarin"/tanggal + jumlah
    // transaksi & total bersih hari itu, supaya mudah dipindai.
    val yesterdayKey = dayKeyID(Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta")).apply { time = Date(); add(Calendar.DAY_OF_YEAR, -1) }.time)
    val groupedShown = shown.groupBy { e -> parseISODate(e.createdAt)?.let { dayKeyID(it) } ?: "" }
        .entries.sortedByDescending { (_, list) -> list.maxOfOrNull { parseISODate(it.createdAt)?.time ?: 0L } ?: 0L }
    // R18 (audit 2026-11-15): bila pemuatan entri gagal, JANGAN diam-diam
    // menampilkan 0 sebagai saldo — tampilkan keterangan di kartu (di bawah),
    // karena kartu galat S11 sudah menjelaskan kegagalannya.
    // TB2 (2026-11-17, permintaan Achmad): SEMUA angka di kartu ini khusus
    // hari ini (reset tiap pagi seperti Penjualan Hari Ini) — Masuk/Keluar
    // sejak am165, dan Saldo sejak am166 (permintaan Achmad 2026-11-17:
    // duit laci diambil tiap hari). Saldo = Masuk hari ini − Keluar hari ini.
    // Ringkasan Masuk/Keluar di kartu grafik juga selalu hari ini (am167) —
    // batang grafiknya tetap mengikuti periode, tidak direset.
    // Pil periode hanya mengatur daftar riwayat + grafik di bawah, supaya
    // riwayat tidak ikut kereset (semua catatan tetap terlihat, terbaru dulu).
    val entriHariIni = entries.filter { e -> parseISODate(e.createdAt)?.let { dayKeyID(it) == todayKey } == true }
    val masukHariIni = entriHariIni.filter { it.type == "in" }.sumOf { it.amount }
    val keluarHariIni = entriHariIni.filter { it.type == "out" }.sumOf { it.amount }
    val saldo = masukHariIni - keluarHariIni
    // Arus kas mengikuti periode yang dipilih, kecuali "Hari Ini" yang tetap
    // menampilkan 7 hari terakhir supaya jelas (1 batang saja di tengah kartu
    // terlihat kosong dan tidak memberi konteks tren).
    val jakartaTz = TimeZone.getTimeZone("Asia/Jakarta")
    val cal = Calendar.getInstance(jakartaTz).apply {
        time = Date()
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val todayStartMillis = cal.timeInMillis
    val dayCount = when (period) {
        // "today" -> 7 hari terakhir (bukan 1 hari saja); "Bulan Ini"/"Semua"
        // tetap mengikuti periode.
        "today" -> 7
        "month" -> cal.get(Calendar.DAY_OF_MONTH)
        else -> {
            // "all": dari hari entri pertama sampai hari ini (maks 365 hari).
            val firstDay = entries.mapNotNull { parseISODate(it.createdAt) }.minOfOrNull { it.time }
                ?.let { t ->
                    Calendar.getInstance(jakartaTz).apply {
                        time = Date(t)
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                } ?: todayStartMillis
            (((todayStartMillis - firstDay) / (24L * 60 * 60 * 1000)).toInt() + 1).coerceIn(1, 365)
        }
    }
    // R19 (audit 2026-11-15): dailyFlow dulu dihitung ulang tiap
    // recomposition (O(hari x entri)); sekarang diingat sampai entri,
    // periode, hari, bulan, atau bahasa berubah.
    val dailyFlow = remember(entries, period, todayKey, monthKey, AppLang.code) {
        (dayCount - 1 downTo 0).map { back ->
        val c = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -back) }
        val key = dayKeyID(c.time)
        // Label: nama hari ("Sen") bila grafik pendek (<= 7 batang, tiap hari
        // muncul sekali); tanggal ("27 Okt") bila grafik panjang supaya tidak ambigu.
        val label = if (dayCount <= 7) {
            SimpleDateFormat("EEE", if (AppLang.code == "en") Locale.ENGLISH else Locale("id", "ID")).apply { timeZone = jakartaTz }.format(c.time)
        } else {
            SimpleDateFormat("d MMM", if (AppLang.code == "en") Locale.ENGLISH else Locale("id", "ID")).apply { timeZone = jakartaTz }.format(c.time)
        }
        val dayEntries = entries.filter { e -> parseISODate(e.createdAt)?.let { dayKeyID(it) == key } == true }
        Triple(label, dayEntries.filter { it.type == "in" }.sumOf { it.amount }, dayEntries.filter { it.type == "out" }.sumOf { it.amount })
        }
    }
    val periodIn = dailyFlow.sumOf { it.second }
    val periodOut = dailyFlow.sumOf { it.third }
    // R16 (audit 2026-11-18): penentuan "kosong" memakai periodEntries
    // (tanpa cap 365 hari) — periodIn/periodOut dari dailyFlow terpotong.
    val periodInSemua = periodEntries.filter { it.type == "in" }.sumOf { it.amount }
    val periodOutSemua = periodEntries.filter { it.type == "out" }.sumOf { it.amount }
    val maxFlow = dailyFlow.maxOfOrNull { maxOf(it.second, it.third) }?.coerceAtLeast(1) ?: 1
    // Satu LazyColumn untuk seluruh layar (header + daftar) supaya layar bisa
    // digulir penuh saat lanskap — header tetap + LazyColumn weight(1f) membuat
    // daftar 0dp dan layar tidak bisa digulir kalau header lebih tinggi dari layar.
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(CardGap),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Column {
            KartuHeaderPremium(tr("Keuangan", "Finance"), Icons.Default.AccountBalanceWallet)
            Spacer(Modifier.height(SpaceL))
            DemoBanner()
            // S11: kartu galat muat + tombol coba lagi (pola Beranda S10).
            if (loadError != null) {
                Spacer(Modifier.height(SpaceL))
                ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr("Gagal memuat data keuangan.", "Failed to load the finance data."), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(SpaceS))
                        Text(loadError ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(SpaceS))
                        Button(onClick = { loadError = null; resumeKey++ }) { Text(tr("Coba lagi", "Try again")) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                listOf("today" to tr("Hari Ini", "Today"), "month" to tr("Bulan Ini", "This Month"), "all" to tr("Semua", "All")).forEach { (k, l) ->
                    FilterChip(selected = period == k, onClick = { period = k }, label = { Text(l) })
                }
            }
            Spacer(Modifier.height(SpaceL))
            // Kartu Saldo Kas — Forest Emerald Hero Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusHero))
                    .background(forestHeroBrush())
                    .padding(16.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tr("Saldo kas", "Cash balance"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = ForestMintSoft
                        )
                        Button(
                            onClick = { if (showForm) showForm = false else { formType = "in"; showForm = true } },
                            colors = ButtonDefaults.buttonColors(containerColor = ForestEmeraldAccent, contentColor = ForestOnAccent),
                            shape = RoundedCornerShape(RadiusSmall),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) {
                            if (showForm) {
                                Icon(Icons.Default.ExpandLess, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(tr("Tutup", "Hide"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            } else {
                                Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(tr("Catat Kas", "Record Cash"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    AnimatedAmount(
                        saldo,
                        MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                        if (saldo < 0) ForestErrorSoft else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (loadError != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(tr("Saldo tidak tersedia, gagal memuat.", "Balance unavailable, failed to load."), style = MaterialTheme.typography.bodySmall, color = ForestErrorSoft)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = Color.White.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.NorthEast, null, tint = ForestMint, modifier = Modifier.size(12.dp))
                                    Text(tr("Masuk", "In"), style = MaterialTheme.typography.labelSmall, color = ForestMintMuted, maxLines = 1)
                                }
                                Spacer(Modifier.height(4.dp))
                                AnimatedAmount(
                                    masukHariIni,
                                    MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                    Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.SouthWest, null, tint = ForestErrorSoft, modifier = Modifier.size(12.dp))
                                    Text(tr("Keluar", "Out"), style = MaterialTheme.typography.labelSmall, color = ForestMintMuted, maxLines = 1)
                                }
                                Spacer(Modifier.height(4.dp))
                                AnimatedAmount(
                                    keluarHariIni,
                                    MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                    ForestErrorSoft,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            // Formulir Catat Kas sebaris (CS4, pilihan Achmad 2026-11-04):
            // mengembang tepat di bawah kartu Saldo Kas — bukan bottom sheet
            // lagi. Setelah tersimpan, formulir terlipat dan daftar di bawah
            // memuat entri baru lewat reload() yang sama seperti sebelumnya.
            // Animasi AK2 (2026-11-17): mengembang 300ms saat dibuka dan
            // mengempis saat ditutup, pola sama seperti buka kartu Riwayat
            // (B2); isi dibungkus Column dulu karena AnimatedVisibility
            // menumpuk anak langsung seperti Box.
            AnimatedVisibility(
                visible = showForm,
                enter = expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(300))
            ) {
                Column {
                    Spacer(Modifier.height(SpaceL))
                    CashEntryFormCard(type = formType, onDismiss = { showForm = false }, onSaved = { showForm = false; reload() })
                }
            }
            Spacer(Modifier.height(SpaceL))
            // Grafik arus kas — judul + isi mengikuti periode yang dipilih.
            // Varian 1: batang dipadatkan (maks 56dp, dulu 110dp) + label nilai per hari.
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val chartTitle = when (period) {
                        "today" -> tr("Arus Kas 7 Hari Terakhir", "Cash Flow - Last 7 Days")
                        "month" -> tr("Arus Kas Bulan Ini", "Cash Flow - This Month")
                        else -> tr("Arus Kas Semua", "Cash Flow - All Time")
                    }
                    Text(chartTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(SpaceS))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("Masuk", "In"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("Keluar", "Out"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(
                            tr("Masuk Rp ${formatCompactIDR(masukHariIni)} • Keluar Rp ${formatCompactIDR(keluarHariIni)}", "In Rp ${formatCompactIDR(masukHariIni)} • Out Rp ${formatCompactIDR(keluarHariIni)}"),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(SpaceL))
                    if (periodInSemua + periodOutSemua == 0) {
                        Text(
                            when (period) {
                                "today" -> tr("Belum ada arus kas 7 hari terakhir.", "No cash flow in the last 7 days.")
                                "month" -> tr("Belum ada arus kas bulan ini.", "No cash flow this month.")
                                else -> tr("Belum ada arus kas.", "No cash flow yet.")
                            },
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // <= 7 batang memenuhi lebar kartu; lebih dari itu bisa
                        // digulir horizontal (kolom tetap 44dp) dan otomatis
                        // terbuka di ujung terbaru (kanan).
                        val scrollable = dailyFlow.size > 7
                        val chartScroll = rememberScrollState()
                        if (scrollable) {
                            LaunchedEffect(period, dailyFlow.size) {
                                withFrameNanos { }
                                chartScroll.scrollTo(chartScroll.maxValue)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().then(if (scrollable) Modifier.horizontalScroll(chartScroll) else Modifier),
                            horizontalArrangement = Arrangement.spacedBy(SpaceXS),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // G1 (pilihan Achmad 2026-11-16): batang tebal +
                            // grid. Box mengukur diri dari baris batang;
                            // lapisan grid (matchParentSize) mengikutinya
                            // sehingga garis selebar konten dan ikut tergulir.
                            Box {
                                // Lapisan grid di belakang batang: 3 garis
                                // (atas = maxFlow, tengah = 2/3, bawah = 1/3)
                                // dengan label nilai di kiri (gutter 48dp;
                                // N3+N9 2026-11-16). Blok plot 108dp
                                // (label 16dp + jeda 2dp + batang 90dp);
                                // garis berpusat di 18/48/78dp dari atas.
                                Column(Modifier.matchParentSize()) {
                                    Spacer(Modifier.height(3.dp))
                                    listOf(maxFlow, 2 * maxFlow / 3, maxFlow / 3).forEach { nilaiGaris ->
                                        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                formatCompactIDR(nilaiGaris),
                                                Modifier.width(44.dp), textAlign = TextAlign.End,
                                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                        }
                                    }
                                    Spacer(Modifier.height(15.dp))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
                                    horizontalArrangement = Arrangement.spacedBy(SpaceXS),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    dailyFlow.forEachIndexed { barIndex, (label, dayIn, dayOut) ->
                                        val isToday = barIndex == dailyFlow.lastIndex
                                        Column(
                                            modifier = if (scrollable) Modifier.width(44.dp) else Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            // Label nilai total hari di atas batang (grafik
                                            // ringkas Varian 1); ruang label selalu disediakan
                                            // supaya dasar batang tetap sejajar antarhari.
                                            Box(Modifier.height(16.dp), contentAlignment = Alignment.BottomCenter) {
                                                if (dayIn + dayOut > 0) {
                                                    Text(
                                                        formatCompactIDR(dayIn + dayOut),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                                                key(period, periodIn, periodOut) {
                                                    // Grafik tumbuh dari 0 tiap kali layar
                                                    // ditampilkan (seperti awal).
                                                    val grow = remember { Animatable(0f) }
                                                    // Jepit jeda animasi supaya grafik panjang
                                                    // tidak butuh waktu lama untuk selesai tumbuh.
                                                    LaunchedEffect(period, periodIn, periodOut) { grow.animateTo(1f, tween(durationMillis = 600, delayMillis = (barIndex * 70).coerceAtMost(500), easing = FastOutSlowInEasing)) }
                                                    val inH = ((90f * dayIn / maxFlow * grow.value).coerceIn(0f, 90f)).let { if (dayIn > 0) it.coerceAtLeast(4f) else 0f }
                                                    val outH = ((90f * dayOut / maxFlow * grow.value).coerceIn(0f, 90f)).let { if (dayOut > 0) it.coerceAtLeast(4f) else 0f }
                                                    Box(Modifier.width(14.dp).height(inH.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                                                    Box(Modifier.width(14.dp).height(outH.dp).background(MaterialTheme.colorScheme.error, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                                                }
                                            }
                                            Spacer(Modifier.height(SpaceS))
                                            Text(
                                                label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(SpaceS))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(SpaceS))
                        Text(
                            if (scrollable) tr("Geser untuk melihat hari lain • Biru = masuk • Merah = keluar • Label hari ini ditebalkan", "Scroll to see other days • Blue = in • Red = out • Today's label is bold")
                            else tr("Biru = masuk • Merah = keluar • Label hari ini ditebalkan", "Blue = in • Red = out • Today's label is bold"),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(SpaceL))
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                listOf("all" to tr("Semua", "All"), "in" to tr("Masuk", "In"), "out" to tr("Keluar", "Out")).forEach { (k, l) ->
                    FilterChip(selected = typeFilter == k, onClick = { typeFilter = k }, label = { Text(l) })
                }
            }
            }
        }
        groupedShown.forEach { (dayKey, dayEntries) ->
            // Header hari (KG1): label hari + jumlah transaksi & total bersih hari itu.
            item(key = "header-$dayKey") {
                val dayDate = dayEntries.firstNotNullOfOrNull { parseISODate(it.createdAt) }
                val label = when (dayKey) {
                    todayKey -> tr("Hari Ini", "Today")
                    yesterdayKey -> tr("Kemarin", "Yesterday")
                    else -> dayDate?.let {
                        SimpleDateFormat("d MMM yyyy", if (AppLang.code == "en") Locale.ENGLISH else Locale("id", "ID")).apply { timeZone = jakartaTz }.format(it)
                    } ?: tr("Tanpa tanggal", "No date")
                }
                val dayIn = dayEntries.filter { it.type == "in" }.sumOf { it.amount }
                val dayOut = dayEntries.filter { it.type == "out" }.sumOf { it.amount }
                val dayNet = dayIn - dayOut
                val netText = (if (dayNet >= 0) "+ " else "- ") + formatIDR(if (dayNet >= 0) dayNet else -dayNet)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${dayEntries.size} transaksi • $netText",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            itemsIndexed(dayEntries, key = { _, e -> e.id }) { _, e ->
            
            val isIn = e.type == "in"
            // Animasi A1 (2026-11-16, pilihan Achmad): entri baru
            // muncul dengan fade + geser halus (animateItem standar).
            // Entri E2 PADAT (pilihan Achmad 2026-11-16): kotak 20dp
            // senada P3/R1/K1/B1 + lingkaran berwarna (bukan putih) +
            // baris lebih pendek (ikon 32dp, padding 10dp).
            ExpressiveCard(shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().animateItem()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = if (isIn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer, shape = CircleShape) {
                        Icon(
                            if (isIn) Icons.Default.SouthWest else Icons.Default.NorthEast, null,
                            tint = if (isIn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp).size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${e.category} • ${formatDateTimeID(e.createdAt)} • ${if (e.source == "sale") tr("Otomatis", "Automatic") else "Manual"}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        (if (isIn) "+ " else "- ") + formatIDR(e.amount),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = if (isIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
        }
        }
    }
}


@Composable
fun CashEntryFormCard(type: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    // Jenis bisa diganti dari dalam form lewat pil geser Masuk/Keluar (gaya
    // pemilih metode pembayaran di Kasir); parameter type = pilihan awal.
    var entryType by remember { mutableStateOf(type) }
    val isIn = entryType == "in"
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf(TextFieldValue("")) }
    // Tanggal entri (WIB), default hari ini; hanya dikirim ke repo bila user
    // mengubahnya — bila tidak, repo memakai waktu sekarang (createdAt = null).
    var entryDate by remember { mutableStateOf(dayKeyID(Date())) }
    var dateChanged by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf("") }
    val dateInteraction = remember { MutableInteractionSource() }
    LaunchedEffect(dateInteraction) {
        dateInteraction.interactions.collect { if (it is PressInteraction.Press) showDatePicker = true }
    }
    val scope = rememberCoroutineScope()
    // CS4 (pilihan Achmad 2026-11-04): bukan bottom sheet lagi — formulir
    // ini kartu yang mengembang di halaman Keuangan, tepat di bawah kartu
    // Saldo Kas. Judul + tombol X lembar lama dibuang; tombol di kartu Saldo
    // menjadi pengalih buka/tutup dan tombol Batal menutup formulir.
    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(SpaceS)) {
            // Pilihan Masuk/Keluar: SingleChoiceSegmentedButtonRow resmi
            // Material 3 (menggantikan pil meluncur rakitan manual). Warna
            // aktif tetap biru tua (Masuk) / merah tua (Keluar) pilihan
            // Achmad, dengan teks putih.
            val kasTypes = listOf(
                Triple("in", tr("Kas Masuk", "Cash In"), Icons.Default.SouthWest),
                Triple("out", tr("Kas Keluar", "Cash Out"), Icons.Default.NorthEast)
            )
            // Animasi pindah jenis (permintaan Achmad 2026-11-18, CK2):
            // baris pemilih meluncur 30dp + memudar 300ms searah pilihan
            // (ke Keluar = dari kanan, ke Masuk = dari kiri) — bahasa gerak
            // yang sama dengan S3/TB1; warna tombol Simpan ikut berubah
            // biru <-> merah secara bergradasi, bukan mendadak.
            val geserJenis = with(LocalDensity.current) { 30.dp.toPx().toInt() }
            AnimatedContent(
                targetState = entryType,
                transitionSpec = {
                    val keKeluar = targetState == "out"
                    (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { if (keKeluar) geserJenis else -geserJenis } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { if (keKeluar) -geserJenis else geserJenis } + fadeOut(tween(300)))
                },
                label = "pindahJenisKas"
            ) { jenis ->
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    kasTypes.forEachIndexed { index, (t, label, icon) ->
                        val activeColor = if (t == "in") KasMasukBlue else MaterialTheme.colorScheme.error
                        SegmentedButton(
                            selected = jenis == t,
                            onClick = { entryType = t },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = kasTypes.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = activeColor,
                                activeContentColor = Color.White,
                                activeBorderColor = activeColor
                            ),
                            icon = { Icon(icon, null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.weight(1f)
                        ) { Text(label, maxLines = 1) }
                    }
                }
            }
            OutlinedTextField(category, { category = it }, label = { Text(tr("Kategori", "Category")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text(tr("Keterangan", "Description")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(amount, { amount = formatDecimalFieldValue(it) }, label = { Text(tr("Jumlah (Rp)", "Amount (Rp)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = formatDateID(entryDate),
                onValueChange = {},
                label = { Text(tr("Tanggal", "Date")) },
                readOnly = true, singleLine = true,
                trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = tr("Pilih tanggal", "Pick a date")) },
                interactionSource = dateInteraction,
                modifier = Modifier.fillMaxWidth()
            )
            if (formError.isNotEmpty()) {
                Text(formError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            // CK2 (pilihan Achmad 2026-11-18): Simpan selebar kartu,
            // Batal jadi teks di bawahnya. Warna Simpan bergradasi
            // biru <-> merah mengikuti jenis (300ms).
            val warnaSimpan by animateColorAsState(
                targetValue = if (isIn) KasMasukBlue else MaterialTheme.colorScheme.error,
                animationSpec = tween(300),
                label = "warnaSimpan"
            )
            Column(verticalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        scope.launch {
                            val result = repo.createCashEntry(
                                CreateCashEntryArgs(
                                    entryType,
                                    category.ifBlank { "Lainnya" },
                                    description.ifBlank { if (isIn) "Kas masuk" else "Kas keluar" },
                                    parseDecimal(amount.text),
                                    createdAt = if (dateChanged) dayKeyToIsoUtc(entryDate) else null
                                )
                            )
                            if (result is ApiResult.Success) {
                                onSaved()
                            } else {
                                formError = (result as? ApiResult.Error)?.message ?: tr("Gagal menyimpan entri kas.", "Failed to save the cash entry.")
                            }
                        }
                    },
                    enabled = parseDecimal(amount.text) > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = warnaSimpan,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(tr("Simpan", "Save")) }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(tr("Batal", "Cancel")) }
            }
        }
    }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = utcMidnightMillis(entryDate), selectableDates = NoFutureDates)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        entryDate = dayKeyID(Date(it))
                        dateChanged = true
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(tr("Batal", "Cancel")) } }
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

// ---------- Laporan Laba ----------
