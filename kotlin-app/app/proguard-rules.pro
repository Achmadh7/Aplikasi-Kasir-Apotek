# ---- Apotek Lestari: aturan R8 / ProGuard ----
# R8 diaktifkan untuk build release (isMinifyEnabled = true di build.gradle.kts)
# supaya APK rilis lebih kecil dan kode ter-obfuscate. Aturan di bawah menjaga
# bagian yang tidak boleh dihapus atau diubah namanya oleh R8.

# Model data & entitas Room — dipakai Room, backup JSON, dan seluruh layar.
# Room sudah membawa consumer rules sendiri, tapi ditegaskan di sini supaya
# entitas dan field-nya tidak dihapus/di-rename.
-keep class id.apotek.lestari.data.** { *; }
-keep class id.apotek.lestari.data.local.** { *; }
-keepattributes *Annotation*

# Apache POI (Excel .xlsx) memakai reflection dan tidak membawa consumer
# rules yang memadai untuk Android — jaga seluruh paketnya (beserta
# dependensi OOXML-nya) supaya Import/Export/Template Stok tidak rusak.
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class org.apache.xmlbeans.** { *; }
# Dependensi transitif POI (dipakai saat tulis/baca .xlsx — ZIP, IO, koleksi,
# log). Wajib di-keep juga: tanpa ini, R8 di build release bisa membuangnya
# sehingga Template & Export Excel jadi file kosong (0 byte).
-keep class org.apache.commons.io.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.collections4.** { *; }
-keep class org.apache.commons.math3.** { *; }
-keep class org.apache.logging.log4j.** { *; }
-keep class com.zaxxer.sparsebits.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.apache.commons.io.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.collections4.**
-dontwarn org.apache.commons.math3.**

# R8 gagal (minifyReleaseWithR8 FAILED) karena POI/Log4j mereferensikan
# class yang tidak ada di Android dan tidak pernah dipakai di HP:
# - java.awt.* (dipakai com.graphbuilder, pustaka gambar POI — desktop only)
# - org.osgi.framework.* (dipakai Log4j OsgiServiceLocator — OSGi, bukan Android)
# Ini hanya peringatan yang R8 anggap fatal; redam supaya build lanjut.
# (Jalur desktop/OSGi itu tidak pernah dijalankan — aplikasi hanya baca/tulis
# sel .xlsx biasa, bukan gambar/chart Excel.)
-dontwarn java.awt.**
-dontwarn org.osgi.**
-dontwarn com.graphbuilder.**
-dontwarn org.apache.logging.log4j.**
