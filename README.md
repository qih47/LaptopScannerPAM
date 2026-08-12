# PAM Laptop Scanner

Aplikasi Android untuk memonitor keluar-masuknya perangkat laptop di lingkungan atau gerbang tertentu. Dibangun dengan menggunakan teknologi modern Jetpack Compose dan Kotlin, aplikasi ini memiliki sinkronisasi data secara langsung (*real-time*) berkat integrasi dengan Supabase.

## Fitur Utama
1. **Pemindaian Kode (Scanner):** Melakukan pendataan perangkat secara instan melalui pemindaian kamera.
2. **Dashboard Real-time:** Menampilkan riwayat transaksi masuk dan keluar secara seketika (*real-time*) setiap kali ada petugas yang memindai perangkat. Terintegrasi pula dengan grafik (*Mini Chart*) harian, mingguan, maupun bulanan.
3. **Notifikasi Sinkronisasi Penuh:** Mendukung *Foreground Service* dengan notifikasi latar belakang. Apabila ada transaksi dari petugas lain, pengguna akan mendapatkan pemberitahuan *real-time*.
4. **Peringatan Mengendap (Overdue):** Memantau perangkat yang telah masuk ke dalam namun belum keluar melewati batas waktu wajar, dengan sistem grup notifikasi tersendiri.
5. **Skala Tampilan Adaptif:** Mengabaikan ukuran *font scale* bawaan sistem Android (yang berpotensi membuat tampilan tidak proporsional) melalui metode pengaturan skala (*Adaptive Density Scaling*), sehingga menu dialog pop-up ukuran tetap ideal.
6. **In-App Update Dialogs:** Pop-up cerdas dengan *scroll view* untuk mendeteksi ketersediaan versi aplikasi terbaru beserta rincian pembaruannya (Release Notes).

## Teknologi
- **Bahasa Pemrograman:** Kotlin
- **UI Toolkit:** Jetpack Compose (Material 3)
- **Database & Realtime Backend:** Supabase (PostgreSQL & Realtime channels)
- **Arsitektur:** MVVM (Model-View-ViewModel) dengan Coroutines & Flow

## Cara Menjalankan
1. Pastikan Anda memiliki Android Studio terbaru (misal: Ladybug / Iguana ke atas).
2. Sesuaikan kredensial integrasi database (seperti Supabase URL dan Anon Key) di file *configuration* yang terhubung.
3. Kompilasi (Build) dan jalankan pada emulator atau *device* Android bersistem operasi minimum SDK 24 ke atas (optimal pada Android 12+).

## Catatan Rilis & Push
- Berkas binari instalasi ukuran besar seperti folder `apk/`, `*.apk`, `*.aab` diabaikan (ignore) dari kontrol versi (Git) untuk menghindari ukuran repositori yang membengkak.
