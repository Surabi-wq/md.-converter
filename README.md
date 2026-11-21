# Marka — File to Markdown

## Pembaruan 1.6.0

- Hasil batch sebelumnya tetap dipertahankan ketika batch baru dijalankan; hasil otomatis disimpan hanya untuk batch yang baru selesai.
- Penggabungan Markdown dilakukan hanya saat pengguna meminta pratinjau atau ekspor, sehingga tidak dihitung ulang pada setiap perubahan daftar hasil.
- Proses batch mendukung pembatalan aman setelah file aktif selesai, dengan indikator progres.
- Marka dapat menerima satu atau beberapa file dari menu Share aplikasi Android lain.
- CSV mencoba mendeteksi pemisah koma atau titik koma; JSON/JSONL juga menerima nilai primitif.
- Heading HTML mempertahankan tingkat heading H1 sampai H6.
- Perbaikan deteksi AAC ADTS dan Ogg Opus serta downsampling frame video pada perangkat Android lama.
- Pembacaan ZIP dan DOCX membatasi jumlah entri dan total data hasil dekompresi untuk mengurangi risiko arsip berukuran ekstrem.


Marka adalah aplikasi Android native untuk mengubah dokumen, teks, source code, gambar, audio, dan video menjadi dokumen Markdown (`.md`) secara lokal di perangkat. Beberapa file dari berbagai format dapat diproses dalam satu antrean, ditinjau per file, lalu digabungkan secara opsional menjadi satu dokumen dengan daftar isi.

## Alur penggunaan

1. Tekan **Pilih file**, kemudian pilih satu atau beberapa file.
2. Periksa antrean file dan hapus file yang tidak ingin dikonversi.
3. Tekan **Konfirmasi & mulai konversi**. File diproses berurutan untuk mengendalikan penggunaan memori.
4. Periksa hasil setiap file. Tombol **Lihat Markdown** menampilkan isi hasil; tombol ekspor menyimpan file tersebut.
5. Untuk satu dokumen gabungan, tekan **3. Gabungkan & simpan satu file .md**. Semua hasil yang berhasil akan disatukan dengan daftar isi dan bagian terpisah per sumber. Penggabungan bersifat opsional.
6. Folder keluaran dapat dipilih satu kali. Izin folder disimpan dan digunakan kembali. Tanpa folder keluaran, Android akan meminta lokasi saat hasil diekspor.

Tombol ikon **?** di bagian atas aplikasi membuka panduan penggunaan dan daftar format yang diambil langsung dari registri format aplikasi. Dengan demikian, daftar format pada panduan mengikuti registri yang digunakan konverter.

## Aturan nama hasil

- Setiap file sumber mempertahankan nama dasarnya; hanya ekstensi yang diganti menjadi `.md`. Contoh: `laporan-akhir.pdf` menjadi `laporan-akhir.md`.
- Jika file dengan nama sama sudah ada di folder tujuan, Marka menambahkan akhiran angka agar hasil lama tidak ditimpa, misalnya `laporan-akhir (2).md`.
- Dokumen gabungan dari beberapa sumber dinamai `gabungan-namafile-pertama.md`. Jika hanya ada satu sumber, nama dasarnya tetap sama.

## Format yang didukung

### Dokumen dan e-book
- PDF berbasis teks
- DOCX
- ODT
- XLSX
- PPTX
- ODS
- ODP
- EPUB
- Jupyter Notebook (`.ipynb`)

### Teks, data, konfigurasi, dan subtitle
- TXT, MD, Markdown, MDX, LOG
- CSV, TSV
- JSON, JSONC, JSONL, NDJSON
- HTML, HTM, XHTML
- YAML, YML, TOML, INI, PROPERTIES, CONF, CFG, ENV
- XML, SVG, RTF, EML, MHTML, ICS, VCF
- SRT, VTT, ASS, SSA, LRC, M3U, PLS
- reStructuredText (RST), AsciiDoc (ADOC), TeX

### Source code
- Python, JavaScript, MJS, CJS, TypeScript, JSX, TSX
- Java, Kotlin, C, C++, C#, PHP, Go, Rust, Swift
- CSS, SCSS, SASS, LESS, Shell, Bash, BAT, PowerShell
- SQL, Ruby, R, Lua, Dart, Scala, Groovy, Gradle, Vue, Svelte

### Gambar dan foto
- JPG/JPEG, PNG, WebP, BMP, GIF, TIFF, HEIC/HEIF, AVIF

### Audio
- MP3, M4A/M4B, AAC, WAV, OGG/OGA, Opus, FLAC, AMR, WMA, AIFF/AIF, CAF, 3GA

### Video
- MP4, M4V, MKV, WebM, MOV, AVI, 3GP/3G2, TS/M2TS, MPEG/MPG/MPE, M2V, VOB, FLV, F4V, WMV, ASF

Deteksi format menggunakan kombinasi signature file, MIME dari Android, dan ekstensi. Dukungan decoder media tetap bergantung pada Android dan codec yang tersedia pada perangkat.

## Pemrosesan gambar, audio, dan video

- Gambar didekode langsung dari stream dan menggunakan downsampling sebelum OCR; resolusi gambar asli dan ukuran file tetap dicatat jika metadata ukuran tersedia.
- OCR gambar memakai ML Kit Text Recognition on-device dengan batas sisi hasil decode sekitar 2048 piksel untuk mengendalikan RAM.
- Video dibaca melalui URI tanpa menyalin seluruh file ke RAM. OCR memakai hingga tiga cuplikan skala kecil pada titik waktu yang tersebar dan menggunakan satu recognizer untuk seluruh cuplikan.
- Audio dibaca melalui decoder Android untuk mengekstrak metadata seperti durasi, judul, artis, album, genre, bitrate, dan jumlah track jika tersedia.
- Transkripsi ucapan/lirik audio belum tersedia. OCR video membaca teks yang terlihat pada cuplikan, bukan dialog atau subtitle lengkap.

## Batasan yang diketahui

- PDF hasil scan kini memiliki OCR fallback sampai 30 halaman pertama jika tidak memiliki lapisan teks. Dokumen PDF campuran teks dan scan masih dapat memerlukan pemeriksaan manual.
- DOC, XLS, dan PPT biner versi lama belum didukung.
- XLSX menampilkan nilai sel yang tersimpan; rumus tidak dihitung ulang. Format sel, grafik, dan tata letak spreadsheet tidak dipertahankan.
- PPTX mengekstrak teks per slide; gambar, animasi, catatan pembicara, dan layout tidak dipertahankan.
- ODT/ODP mengekstrak paragraf dan heading; tata letak dan media tertanam tidak dipertahankan. ODS diekstrak sebagai tabel.
- EPUB mengekstrak isi HTML/XHTML; urutan bab mengikuti urutan nama entri arsip dan dapat berbeda dari spine pada sebagian buku.
- DOCX mengambil paragraf dan heading dasar; tabel kompleks, gambar, catatan kaki, header/footer, dan tata letak belum dipertahankan.
- OCR bahasa/aksara tertentu, tulisan tangan, gambar buram, atau teks yang sangat kecil dapat keliru atau tidak terdeteksi.
- Audio dan video yang codec-nya tidak didukung decoder perangkat hanya menghasilkan laporan dasar dengan peringatan.
- Batas ukuran file dokumen/arsip yang dibaca ke buffer adalah 64 MB. Konten teks yang dipilih dari arsip dibatasi 40 MB total dan 12 MB per entri (DOCX memakai batas XML 20 MB); seluruh data hasil dekompresi arsip dibatasi 128 MB dan maksimal 5.000 entri. Gambar diproses dengan stream; audio/video menggunakan URI/metadata tanpa menyalin seluruh media ke RAM.
- Selalu tinjau hasil Markdown sebelum digunakan lebih lanjut. Konversi tidak menjamin format identik dengan tampilan dokumen asli.

## Privasi dan penyimpanan

Pemrosesan dilakukan secara lokal. File asli tidak diubah dan tidak diunggah ke server. Folder keluaran disimpan sebagai URI Storage Access Framework dengan izin yang diberikan Android; jika izin dicabut, pengguna perlu memilih folder kembali.

## Build

- Platform: Android native, Kotlin, Jetpack Compose
- Minimum SDK: 26
- Target SDK: 35
- ABI APK: `arm64-v8a`, `armeabi-v7a`
- JDK: 17
- Gradle: 8.9
- Build debug: `gradle --no-daemon :app:assembleDebug`
- Tes unit: `gradle --no-daemon :app:testDebugUnitTest`
- Android lint: `gradle --no-daemon :app:lintDebug`

GitHub Actions menjalankan tes unit, lint, dan build debug pada push ke `main` serta pull request menuju `main`.
