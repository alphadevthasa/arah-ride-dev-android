# ArahRide — Android native

ArahRide berisi dua aplikasi Android dengan `applicationId` berbeda:

- `:customer` → **Arah**, untuk membuat dan memantau pesanan.
- `:driver` → **Arah Driver**, untuk online, menerima pesanan, menandai tiba, mulai, dan selesai.
- `:core` → model, HTTP/WebSocket, peta MapLibre, dan panel UI bersama.

Peta native MapLibre ditempatkan di layout XML supaya komponen peta tetap berada di view system. Panel status dan tombol memakai Jetpack Compose. Data peta demo berasal dari OpenStreetMap melalui [OpenFreeMap](https://openfreemap.org/quick_start/); atribusi terlihat di bagian atas layar.
Peta dapat diperbesar atau diperkecil dengan gestur cubit.

Pratinjau: [tampilan Driver dengan ikon mobil](docs/live-car.png).

## Menjalankan

1. Jalankan [Arah Ride Backend Dev](https://github.com/alphadevthasa/arah-ride-backend-dev).
2. Buka folder ini di Android Studio; jalankan `customer` dan `driver` pada emulator Android yang sama atau berbeda.
3. Di Arah Driver, tekan **Mulai online** dan izinkan lokasi. Di Arah, pilih titik jemput dan tujuan dengan mengetuk peta, lalu pesan. Kembali ke Driver untuk menerima hingga selesai.
4. Untuk build terminal: `./gradlew :customer:assembleDebug :driver:assembleDebug`.

Default API adalah `http://10.0.2.2:18080`, alamat host dari emulator Android. Untuk perangkat fisik, set URL yang bisa dijangkau perangkat:

```sh
./gradlew :customer:assembleDebug :driver:assembleDebug \
  -PAPI_BASE_URL=http://nama-mac.local:18080 \
  -PMAP_STYLE_URL=https://tiles.openfreemap.org/styles/liberty
```

Jika memakai perangkat fisik, server demo perlu `ARAH_ADDR=0.0.0.0:18080`. Hanya gunakan ini pada jaringan pengembangan yang dipercaya. Build release mematikan HTTP cleartext; gunakan endpoint HTTPS produksi.

## Alur dan batas prototipe

Status: `requested → accepted → arrived → in_progress → completed`, dengan pembatalan Customer hanya sebelum perjalanan dimulai. Backend demo mengambil rute jalan dari OSRM, menyimpan geometri pada pesanan, dan kedua aplikasi menggambarnya sebagai polyline. Posisi Driver berasal dari LocationManager setiap 3 detik atau setelah bergerak 5 meter, dikirim ke backend, lalu diteruskan melalui WebSocket. Saat Driver menekan **Mulai perjalanan**, backend mengirim lokasi simulasi tiap 100 ms selama 16 detik mengikuti polyline; lokasi perangkat dijeda selama simulasi. Ikon mobil bergerak di sepanjang rute; tombol **Lihat posisi Driver** mengarahkan peta Customer ke lokasi terbaru. Layanan lokasi foreground dengan notifikasi menjaga pengiriman ketika aplikasi Driver Android berada di latar belakang.

Server demo hanya mendukung satu Driver dan satu pesanan aktif, tanpa akun, pembayaran, tarif, dispatch multi-driver, navigasi jalan, atau penyimpanan persisten. Titik peta adalah koordinat; belum ada geocoding alamat atau estimasi rute. Sebelum rilis, tambahkan autentikasi, otorisasi per pesanan, database, penyebaran driver, APNs/FCM untuk penawaran saat app tidak aktif, dan endpoint HTTPS. Ganti penyedia tile demo dengan penyedia OSM ber-SLA atau tile server sendiri sesuai beban. Jangan arahkan aplikasi komersial ke server tile publik `tile.openstreetmap.org`.

## Hasil verifikasi pencarian tempat (2026-06-28)

- Otomatis: Android: `./gradlew :core:testDebugUnitTest :customer:assembleDebug :driver:assembleDebug` lulus (19 unit test).
- Backend dengan Photon palsu lokal (`ARAH_GEOCODER_URL`): `/v1/places/search` dan `/v1/places/reverse` memetakan hasil dengan benar; kata kunci kosong → 400.
- **Belum diverifikasi**: Photon publik (`photon.komoot.io`) tidak terjangkau dari mesin ini saat pengujian (backend mengembalikan 502 generik), sehingga pencarian alamat/POI Indonesia sungguhan dan ketersediaan produksi tidak teruji. Uji emulator/simulator end-to-end (pilih titik, buat pesanan, label di app Driver, tap peta) juga belum dijalankan.
