# ITIK SERATI

Aplikasi Android (Kotlin + Jetpack Compose) untuk menguruskan router **OpenWrt** (contoh: modem AW1000) melalui API `ubus` JSON-RPC.
Tema gelap neon (cyan/biru/ungu), semua teks dalam Bahasa Melayu. Package name kekal `com.aw1000.manager`.

> **Status jujur:** projek ini disediakan tanpa Android SDK dan tanpa rangkaian, jadi **APK belum dibina atau diuji** oleh penulis.
> Binaan pertama akan dilakukan oleh GitHub Actions. Jika ada ralat kompilasi, salin log daripada langkah yang gagal.

## Muat naik ke GitHub
1. Buat repositori baharu di github.com (contoh `itik-serati`), kosong, tanpa README.
2. Ekstrak ZIP ini. Muat naik **semua** fail termasuk folder tersembunyi `.github` (guna `git`, atau seret-lepas melalui *Add file → Upload files*; pastikan `.github/workflows/` turut dimuat naik).
   ```bash
   git init && git add . && git commit -m "ITIK SERATI"
   git branch -M main
   git remote add origin https://github.com/NAMA_ANDA/itik-serati.git
   git push -u origin main
   ```
3. Buka tab **Actions**. Jika diminta, tekan **I understand my workflows, go ahead and enable them**.
4. Workflow **Bina APK ITIK SERATI** berjalan automatik pada setiap push. Boleh juga ditekan manual: *Actions → Bina APK ITIK SERATI → Run workflow*.

## Muat turun APK
*Actions → klik larian yang berwarna hijau → bahagian **Artifacts** di bawah → **ITIK-SERATI-debug-apk*** (fail ZIP berisi `app-debug.apk`). Ekstrak dan pasang di telefon (benarkan "pasang daripada sumber tidak diketahui"). Laporan ujian/lint ada dalam artifact **ITIK-SERATI-laporan**.

## GitHub Release (pilihan)
```bash
git tag v1.0.0 && git push origin v1.0.0
```
Workflow `release.yml` akan membina dan melampirkan APK pada Releases hanya jika build berjaya. APK ini ditandatangani dengan kunci *debug*; untuk pengedaran rasmi sediakan keystore sendiri melalui GitHub Secrets (jangan commit keystore).

## Gradle Wrapper
Fail `gradlew`, `gradlew.bat` dan `gradle-wrapper.properties` disertakan, tetapi `gradle-wrapper.jar` (fail binari) tidak dapat dijana tanpa rangkaian. Workflow menjananya sendiri (`gradle wrapper --gradle-version 8.9`). Secara tempatan: `gradle wrapper --gradle-version 8.9` sekali, kemudian `./gradlew assembleDebug`.

## Fungsi dan cara ia berfungsi
| Halaman | Sumber data | Status |
|---|---|---|
| Log masuk | `ubus session login`; kata laluan disulitkan AES-256-GCM (Android Keystore) jika "Simpan" dipilih; tidak dilog | Perlu ujian router |
| Utama | Kelajuan = kaunter `/proc/net/dev` antara muka WAN; CPU `/proc/stat`; RAM `system info`; suhu `thermal_zone`; ping oleh router ke 1.1.1.1 | Perlu ujian router |
| Data | `nlbwmon` (`nlbw`) untuk jumlah bulanan setiap peranti; graf harian dikira daripada bacaan kaunter yang direkod aplikasi | Perlu nlbwmon + ujian |
| Speedtest | **Dari Telefon**: muat turun/naik ke Cloudflare (laluan telefon→router→Internet). **Pada Router**: ping + muat turun `wget` dijalankan oleh router; muat naik tidak disokong | Perlu ujian |
| Peranti | `/proc/net/arp` + `dhcp.leases` + `iwinfo` (Wi-Fi/LAN). Suis menambah/membuang peraturan firewall UCI `ITIK_BLOCK_<MAC>`, reload firewall, dan **mengesahkan** hasilnya | Perlu ujian router |
| Sistem | `system board`, `network.interface dump`, `df`, AT `QENG` melalui `sms_tool` (modem Quectel) | Perlu ujian router |

Maklumat yang tidak tersedia dipaparkan sebagai **"Tiada data"**; tiada nombor rekaan.

## Keperluan pada router OpenWrt
- Akaun `root` (atau akaun rpcd dengan kebenaran `uci`, `file`, `system`, `iwinfo`, `network.interface`).
- **Sejarah data:** `opkg install nlbwmon luci-app-nlbwmon` (atau `apk add` pada OpenWrt 24+), kemudian `/etc/init.d/nlbwmon enable && /etc/init.d/nlbwmon start`. Tanpa ini halaman Data memaparkan mesej "tidak tersedia". nlbwmon menyimpan jumlah mengikut tempoh bil (lalai bulanan), bukan harian; graf harian hanya ada untuk hari aplikasi dibuka dan terlalu jauh daripada hari sebelumnya tidak dikira.
- **Modem:** `sms_tool` untuk bacaan band/isyarat (Quectel). Selain itu bahagian modem ditandakan tidak tersedia.
- **Sekat peranti:** perlu `firewall` (fw3/fw4) dan `uci` melalui rpcd. Sambungan sedia ada mungkin kekal sebentar; aplikasi cuba `conntrack -D` jika tersedia. Peraturan hanya menyekat trafik LAN→WAN peranti itu.
- **Speedtest pada router:** perlu `wget` dengan akses HTTP ke Internet (ujian guna `http://speed.cloudflare.com`; jika router mengalihkan ke HTTPS tanpa sokongan TLS, ujian akan dilaporkan gagal).

## Keselamatan
- Tiada kata laluan/token dalam repo. `.gitignore` menolak `local.properties`, keystore dan fail rahsia.
- Semua arahan router ialah skrip tetap; input pengguna (IP, MAC) disahkan dengan regex dan dihantar sebagai argumen JSON, bukan rentetan shell.
- TLS tidak dilumpuhkan. Router biasanya HTTP pada LAN, maka `usesCleartextTraffic` diaktifkan; gunakan hanya pada rangkaian dipercayai atau aktifkan HTTPS dengan sijil sah (Tetapan Lanjutan).
- `allowBackup=false` supaya data tersimpan tidak masuk sandaran awan.

## Perkara yang perlu diuji pada router sebenar
Log masuk, bacaan kelajuan WAN, nama antara muka WAN (contoh `wwan0_1`), Wi-Fi/LAN, bacaan modem, nlbwmon (lajur `rx_bytes`/`tx_bytes` dianggap muat turun/muat naik klien; sahkan arahnya), sekat/buka peranti, speedtest pada router.
