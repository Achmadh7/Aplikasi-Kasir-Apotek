// Client bundle for the space.
//
// Bundling is owned by the SDK so every space produces an identical
// production-mode React bundle. See @hatch/space-sdk/build for the
// canonical Bun.build() configuration (entrypoint, outdir, NODE_ENV
// define, asset naming, tailwind plugin).

import { cp, readFile, readdir, stat, writeFile } from "node:fs/promises";
import { buildClient } from "@hatch/space-sdk/build";

await buildClient();

// Installable web app (PWA) files: copy the Web App Manifest and its icons
// into dist/assets/ under stable, unhashed names. The runtime serves built
// files under the assets/ route, so ./assets/manifest.json is a real
// same-origin URL the browser can fetch for Android installation (a blob:
// or data: manifest cannot be used for that). The manifest's relative
// start_url/scope ("../") and icon paths resolve against this location.
// The CVM bundler copies non-JS/CSS files under dist/ verbatim, so these
// files survive into the private-rendering bundle as well.
await cp("./client/pwa/manifest.json", "./client/dist/assets/manifest.json");
await cp("./client/src/assets/icon-192.png", "./client/dist/assets/icon-192.png");
await cp("./client/src/assets/icon-512.png", "./client/dist/assets/icon-512.png");
await cp("./client/pwa/icon-maskable-512.png", "./client/dist/assets/icon-maskable-512.png");
// Service worker: copied to the dist ROOT (not assets/) so that, on hosts
// that serve built files at the app root, its scope covers the whole app.
// The ".JS" uppercase extension is deliberate: the platform's CVM bundler
// rejects any second lowercase "*.js" file in dist ("expected exactly one
// JS bundle"), while static hosts resolve the extension case-insensitively
// and still serve it as JavaScript. The worker never caches
// server-action/data requests — see client/pwa/sw.js.
await cp("./client/pwa/sw.js", "./client/dist/sw.JS");

// Full source-code bundle: concatenate every project source file into one
// downloadable text file under dist/assets/ (stable, unhashed name, same
// route as manifest.json). The "Kode" menu in the app links to it with a
// download attribute, so the owner can save the complete source code of
// the app — client, server, database schema, migrations, and PWA files —
// as a single file. Regenerated on every build, so it always matches the
// code that produced the running app.
const SOURCE_BUNDLE_FILES = [
  "space.json",
  "package.json",
  "client/index.html",
  "client/build.mjs",
  "client/src/main.tsx",
  "client/src/App.tsx",
  "client/src/api.ts",
  "client/src/theme.css",
  "client/src/globals.d.ts",
  "server/src/schema.ts",
  "server/src/actions.ts",
  "client/pwa/manifest.json",
  "client/pwa/sw.js",
];
const drizzleFiles = (await readdir("./drizzle")).filter(f => f.endsWith(".sql")).sort();
for (const f of drizzleFiles) SOURCE_BUNDLE_FILES.push(`drizzle/${f}`);
const bundleParts = [
  "================================================================",
  "SOURCE CODE LENGKAP — Aplikasi Apotik Lestari",
  "Kasir, stok obat, keuangan, laporan laba & PWA Android.",
  "Satu file berisi seluruh kode sumber proyek (TypeScript).",
  "Setiap file dipisahkan oleh header ===== FILE: <path> =====",
  "================================================================",
  "",
];
for (const rel of SOURCE_BUNDLE_FILES) {
  let body;
  try { body = await readFile(`./${rel}`, "utf8"); } catch { continue; }
  bundleParts.push("", `===== FILE: ${rel} =====`, "", body.replace(/\s+$/, ""), "");
}
await writeFile("./client/dist/assets/source-code-apotik-lestari.txt", bundleParts.join("\n"), "utf8");

// ---------------------------------------------------------------------------
// source.zip — a real ZIP archive (STORE method, no external dependency)
// containing the complete project source PLUS a pure native Kotlin
// Android Studio project (kotlin-app/) written in Kotlin + Jetpack
// Compose — tanpa folder web sama sekali — so the owner can
// compile an Android APK more easily in Android Studio, via GitHub
// Actions from a phone, or via Termux+GitHub. Written to
// dist/assets/source-apotik-lestari.zip (stable, unhashed name, same
// route as the .txt bundle) and linked from the "Kode" menu with
// download="source.zip". Regenerated on every build from the real
// Kotlin sources under client/kotlin-app/.
// ---------------------------------------------------------------------------
const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c;
  }
  return table;
})();
function crc32(buf) {
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) crc = CRC_TABLE[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}
function dosDateTime(d) {
  const time = (d.getHours() << 11) | (d.getMinutes() << 5) | Math.floor(d.getSeconds() / 2);
  const date = (((d.getFullYear() - 1980) & 0x7f) << 9) | ((d.getMonth() + 1) << 5) | d.getDate();
  return { time, date };
}
function createZip(entries) {
  const now = dosDateTime(new Date());
  const chunks = [];
  const central = [];
  let offset = 0;
  for (const e of entries) {
    const nameBuf = Buffer.from(e.name, "utf8");
    const crc = crc32(e.data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6); // UTF-8 filename flag
    local.writeUInt16LE(0, 8); // method 0 = stored
    local.writeUInt16LE(now.time, 10);
    local.writeUInt16LE(now.date, 12);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(e.data.length, 18);
    local.writeUInt32LE(e.data.length, 22);
    local.writeUInt16LE(nameBuf.length, 26);
    local.writeUInt16LE(0, 28);
    chunks.push(local, nameBuf, e.data);
    const cen = Buffer.alloc(46);
    cen.writeUInt32LE(0x02014b50, 0);
    cen.writeUInt16LE(20, 4);
    cen.writeUInt16LE(20, 6);
    cen.writeUInt16LE(0x0800, 8);
    cen.writeUInt16LE(0, 10);
    cen.writeUInt16LE(now.time, 12);
    cen.writeUInt16LE(now.date, 14);
    cen.writeUInt32LE(crc, 16);
    cen.writeUInt32LE(e.data.length, 20);
    cen.writeUInt32LE(e.data.length, 24);
    cen.writeUInt16LE(nameBuf.length, 28);
    cen.writeUInt32LE(0, 38);
    cen.writeUInt32LE(offset, 42);
    central.push(cen, nameBuf);
    offset += 30 + nameBuf.length + e.data.length;
  }
  const centralBuf = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(centralBuf.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...chunks, centralBuf, end]);
}

const README_APK = [
  "APLIKASI APOTIK LESTARI — VERSI KOTLIN, CARA COMPILE JADI APK ANDROID",
  "=====================================================================",
  "",
  "Isi source.zip ini:",
  "",
  "1. app-source/ — source code lengkap proyek web ini (client, server,",
  "   database/migrasi, PWA). Kode asli yang menjalankan aplikasi web",
  "   yang sekarang kamu pakai, dipertahankan apa adanya.",
  "",
  "2. kotlin-app/ — proyek Android Studio MURNI KOTLIN + Jetpack Compose:",
  "   murni native, tidak ada folder web di dalamnya.",
  "   Buka folder kotlin-app/ di Android Studio untuk compile APK.",
  "   Panduan lengkap ada di kotlin-app/README-KOTLIN.md.",
  "",
  "Ringkasan compile:",
  "- Android Studio: Open folder kotlin-app, tunggu Gradle Sync,",
  "  lalu Build > Build APK(s). Hasil: app/build/outputs/apk/debug/app-debug.apk",
  "- Dari HP via GitHub Actions: upload isi kotlin-app/ ke root repo,",
  "  buat workflow assembleDebug, jalankan dari tab Actions, unduh APK.",
  "- Termux di HP: compile dilakukan lewat GitHub Actions (bukan di HP).",
  "",
  "Catatan data: data asli ada di server web. Ganti BASE_URL di",
  "kotlin-app/app/build.gradle.kts dari https://example.invalid/ ke",
  "alamat server aslimu. Selama belum diganti, aplikasi Kotlin berjalan",
  "dalam mode demo lokal (data contoh di HP saja). Lihat README-KOTLIN.md.",
  "",
].join("\n");

const zipEntries = [];
const pushText = (name, text) => zipEntries.push({ name, data: Buffer.from(text, "utf8") });
const pushFile = async (zipName, diskPath) => {
  try {
    const data = await readFile(diskPath);
    zipEntries.push({ name: zipName, data: Buffer.isBuffer(data) ? data : Buffer.from(data) });
  } catch { /* file missing — skip, ZIP stays valid */ }
};

pushText("README-APK.md", README_APK);

// Full original project source under app-source/
const ZIP_SOURCE_FILES = [
  ...SOURCE_BUNDLE_FILES,
  "tsconfig.base.json",
  "bunfig.toml",
  "client/tsconfig.json",
  "server/tsconfig.json",
  "client/pwa/icon-maskable-512.png",
];
for (const rel of ZIP_SOURCE_FILES) await pushFile(`app-source/${rel}`, `./${rel}`);
// Binary image assets (read as raw bytes, never as utf8)
await pushFile("app-source/client/src/assets/apotek-lestari-logo.jpg", "./client/src/assets/apotek-lestari-logo.jpg");
await pushFile("app-source/client/src/assets/icon-192.png", "./client/src/assets/icon-192.png");
await pushFile("app-source/client/src/assets/icon-512.png", "./client/src/assets/icon-512.png");
// The concatenated single-file bundle, for quick reading
await pushFile("app-source/source-code-apotik-lestari.txt", "./client/dist/assets/source-code-apotik-lestari.txt");

// Pure native Kotlin project -> kotlin-app/ (walk client/kotlin-app
// recursively; those are the real Kotlin sources maintained in this repo)
async function walkKotlin(dir, prefix) {
  let items;
  try { items = await readdir(dir, { withFileTypes: true }); } catch { return; }
  for (const it of items) {
    const disk = `${dir}/${it.name}`;
    const zipName = `${prefix}/${it.name}`;
    if (it.isDirectory()) { await walkKotlin(disk, zipName); continue; }
    try {
      const st = await stat(disk);
      if (!st.isFile()) continue;
      const data = await readFile(disk);
      zipEntries.push({ name: zipName, data: Buffer.isBuffer(data) ? data : Buffer.from(data) });
    } catch { /* skip unreadable file */ }
  }
}
await walkKotlin("./client/kotlin-app", "kotlin-app");
// Also include .gitignore which readdir shows as file (kept) and a copy of README at kotlin-app root is already walked.

await writeFile("./client/dist/assets/source-apotik-lestari.zip", createZip(zipEntries));
