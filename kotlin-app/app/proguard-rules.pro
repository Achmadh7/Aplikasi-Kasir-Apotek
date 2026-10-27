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
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.**
-dontwarn org.apache.xmlbeans.**

# ML Kit Barcode Scanning & CameraX membawa consumer rules sendiri;
# redam peringatan yang tidak relevan supaya build tidak berisik.
-dontwarn com.google.mlkit.**
-dontwarn androidx.camera.**
