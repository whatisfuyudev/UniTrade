**UniTrade**

UniTrade adalah aplikasi marketplace untuk mahasiswa universitas yang memungkinkan pengguna membeli, menjual, dan berkomunikasi mengenai barang di lingkungan kampus.

**Ringkasan**:
- **Platform**: Android (Kotlin)
- **Arsitektur singkat**: Jetpack (Fragments, ViewModel), Firebase (Auth, Firestore, Storage), Hilt untuk DI, Glide untuk gambar.
- **Fitur utama**: daftar produk, pencarian, chat antar pengguna, chatbot FAQ berbasis AI, upload gambar, favorit, manajemen produk.

**Lokasi penting**:
- Kode aplikasi: `app/src/main/java/com/unitrade/unitrade`
- Layouts: `app/src/main/res/layout`
- Menu toolbar: `app/src/main/res/menu/home_top_menu.xml`
- Chat adapter: `app/src/main/java/com/unitrade/unitrade/ChatMessageAdapter.kt`
- Chatbot integration: `app/src/main/java/com/unitrade/unitrade/AIChatbotRepository.kt`

**Prasyarat (local development)**
- Android Studio (Electric Eel atau terbaru) dengan Android SDK yang sesuai.
- Java 11+ / JDK yang kompatibel.
- Akses ke project Firebase (Auth, Firestore). Pastikan `google-services.json` berada di `app/` (sudah ada di repo).

**Konfigurasi sensitif / API keys**
- Jangan menyimpan API key publik di kode sumber. Saat ini `AIChatbotRepository.kt` mengandung API key — pindahkan ke konfigurasi lokal.
- Rekomendasi cepat: tambahkan ke `local.properties` (lokal, jangan commit) atau `gradle.properties` yang tidak di-push.
  - Contoh `local.properties` entry:
    - `AI_CHATBOT_API_KEY=your_api_key_here`
  - Lalu expose ke app via `build.gradle` (contoh: `buildConfigField "String", "AI_CHATBOT_API_KEY", '"' + project.property("AI_CHATBOT_API_KEY") + '"'`) dan akses melalui `BuildConfig.AI_CHATBOT_API_KEY`.

**Build & Run (Windows PowerShell)**
- Buka project di Android Studio dan jalankan pada emulator atau perangkat.
- Atau pakai Gradle dari terminal (PowerShell):
```
.\gradlew assembleDebug
.\gradlew installDebug
```
- Untuk menjalankan instrumented tests (perangkat / emulator):
```
.\gradlew connectedAndroidTest
```

**Pengujian singkat**
- Pastikan Firebase terkonfigurasi dan pengguna ada untuk fitur chat.
- Untuk memeriksa avatar chat: lihat `item_chat_message_in.xml` dan `item_chat_message_out.xml`.

**Catatan pengembangan**
- Dependency image loading: `Glide` digunakan di beberapa adapter.
- DI: Hilt dipakai — pastikan aplikasi di-build ulang setelah menambah/mengubah modul Hilt.
- Jika terjadi konflik merge (res) periksa `app/src/main/res/values/colors.xml`.

**Kontribusi**
- Buat branch fitur dari `master`, beri nama singkat (mis. `feature/chat-avatar`).
- Sertakan deskripsi PR dan langkah reproduksi untuk pengujian.

**Kontak / Pemeliharaan**
- Owner repo: `whatisfuyudev` (lihat remote origin).
