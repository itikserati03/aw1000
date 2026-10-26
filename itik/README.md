# ITIK SERATI

Aplikasi Android (Kotlin + Jetpack Compose) untuk memantau dan mengurus router OpenWrt (contoh: AW1000 5G, firmware Lienol) melalui **ubus JSON-RPC** (`http://IP/ubus`). Tema gelap neon, semua teks Bahasa Melayu, enam skrin: Log Masuk, Utama, Data, Speedtest, Peranti, Sistem.

> **Status jujur:** projek ini ditulis dan disemak secara statik, tetapi **belum dikompil, belum dibina menjadi APK dan belum diuji pada router sebenar** (persekitaran penulis tiada Android SDK/Gradle/internet). Build pertama pada GitHub Actions mungkin mendedahkan ralat kompilasi kecil; hantar log ralat itu untuk dibetulkan.

## 1. Muat naik ke GitHub
1. Log masuk github.com → **New repository** → nama `itik-serati` (Private atau Public) → **Create**.
2. Pilih **uploading an existing file**, seret **semua kandungan** folder projek ini (termasuk folder tersembunyi `.github`) → **Commit changes**.
   - Pastikan struktur: `.github/workflows/android-build.yml`, `app/`, `build.gradle.kts`, `settings.gradle.kts`.
   - Cara lain: `git init && git add . && git commit -m "ITIK SERATI" && git branch -M main && git remote add origin <URL> && git push -u origin main`.

## 2. Aktifkan GitHub Actions
Buka tab **Actions**. Jika ada butang *"I understand my workflows, go ahead and enable them"*, tekan. Build bermula sendiri pada setiap push ke `main`/`master` dan pull request. Untuk jalankan manual: **Actions → Bina ITIK SERATI APK → Run workflow**.

## 3. Muat turun APK
1. **Actions** → klik larian terbaharu yang berwarna hijau ✔.
2. Skrol ke bahagian **Artifacts** di bawah → muat turun **ITIK-SERATI-debug-apk** (fail zip; ekstrak untuk dapat `app-debug.apk`).
3. Salin ke telefon, benarkan *Install unknown apps*, dan pasang. Laporan ujian/lint ada dalam artifact **ITIK-SERATI-laporan**.

Jika larian merah ✖, buka langkah yang gagal (mesej ralat jelas) dan salin log.

## 4. Release berversi (pilihan)
Tolak tag versi: `git tag v1.0.0 && git push origin v1.0.0`. Workflow *Keluaran APK* hanya menerbitkan Release jika ujian dan build berjaya. APK ditandatangani dengan kunci debug (bukan untuk Play Store). Tiada keystore atau rahsia dalam repo.

## Gradle Wrapper
Fail `gradle-wrapper.jar` binari tidak dapat dijana di sini, jadi workflow memasang **Gradle 8.7** terus (`gradle/actions/setup-gradle`). Untuk bina di PC: buka dengan Android Studio (ia menjana wrapper), atau jalankan `gradle wrapper --gradle-version 8.7` sekali lalu `./gradlew assembleDebug`.

## Prasyarat router
Aplikasi guna `uhttpd-mod-ubus`, `rpcd`, `rpcd-mod-file`, `rpcd-mod-iwinfo` (lazimnya sedia ada pada Lienol/OpenWrt). Log masuk guna akaun `root` + kata laluan LuCI. Pastikan telefon pada Wi-Fi/LAN router.

| Fungsi | Keperluan pada router |
|---|---|
| Data per peranti & sejarah | `opkg update && opkg install nlbwmon` (aplikasi memanggil `/usr/sbin/nlbw`) |
| Ping & status Internet | `/bin/ping` (ada pada BusyBox) |
| Speedtest pada router | `wget` dengan HTTPS (`ca-bundle`, `libustream-mbedtls`/`uclient-fetch`) |
| Sekat peranti | `firewall` standard dengan zon `lan` dan `wan` |

## Senarai fungsi
**Dilaksanakan (kod ditulis, perlu ujian router sebenar):**
- Log masuk ubus; kata laluan disulitkan AES-GCM dalam Android Keystore (pilihan simpan), tidak dilog; IP disahkan.
- Utama: status ONLINE/OFFLINE (ping router → 1.1.1.1), kelajuan dari kaunter bait antara muka WAN, graf masa nyata, ping/jitter/paket hilang (dari router), bilangan peranti, tempoh hidup, CPU/RAM/suhu.
- Data: harian/bulanan, pemilih tarikh, graf, jumlah, per peranti. Jumlah direkod daripada kaunter WAN **semasa aplikasi dibuka**; per peranti daripada perbezaan kaunter nlbwmon. **Tiada sejarah direka**; tempoh sebelum aplikasi merekod kosong.
- Speedtest: (a) dari telefon ke Cloudflare (ping TCP, jitter, kehilangan, muat turun & naik berbilang sambungan), (b) pada router (router memuat turun fail sendiri; muat naik tiada). Sejarah hanya keputusan ujian sebenar.
- Peranti: senarai daripada ARP + DHCP + iwinfo, penapis Wi-Fi/LAN, suis sekat/buka dengan dialog pengesahan. Sekatan = peraturan firewall UCI `ITIK-BLOCK-<MAC>` (zon lan→wan), `firewall reload`, kemudian **dibaca semula untuk mengesahkan**. MAC disahkan sebelum digunakan; tiada arahan shell dibina daripada input.
- Sistem: model, firmware, kernel, uptime, CPU/RAM/suhu/storan, IP WAN/LAN, antara muka WAN.

**Belum / bergantung firmware:**
- Isyarat 4G/5G, band, status SIM, IMEI/IMSI: **ditanda "Tidak tersedia"**; tiada kaedah umum tanpa AT command khusus modem.
- Suhu modem hanya jika wujud sebagai `thermal_zone`.
- Daya sekatan pada sambungan sedia ada mungkin tertangguh seketika.
- Ujian pada AW1000 sebenar belum dibuat oleh pembangun.

## Keselamatan
- Tiada kata laluan/token/keystore dalam repo; `.gitignore` menolak `local.properties`, `*.jks`, `*.keystore`.
- HTTP biasa (tanpa sulit) dibenarkan kerana router rumah lazimnya tiada sijil sah; sijil swatandatang hanya diterima jika anda tandakan pilihan itu.

## Perubahan daripada versi lama (AW1000 Manager)
Package `com.aw1000.manager` dikekalkan. UI ditulis semula dalam Compose mengikut reka bentuk baharu; `Secure.kt` dikekalkan; `RouterClient.kt` dikembangkan (WAN sebenar tanpa jatuh balik ke `br-lan`, ping, nlbwmon, sekat peranti, speedtest router). `minSdk` dinaikkan ke 26. Butang loceng reka bentuk diganti butang **muat semula**, dan menu diganti **log keluar**, supaya setiap butang ada fungsi sebenar.
