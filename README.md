# Marka — File to Markdown

Marka 1.7.0 adalah aplikasi Android native untuk mengubah dokumen, teks, source code, gambar, audio, dan video menjadi dokumen Markdown (`.md`) secara lokal di perangkat. Beberapa file dari berbagai format dapat diproses dalam satu antrean, ditinjau per file, lalu digabungkan secara opsional menjadi satu dokumen dengan daftar isi.

## Alur penggunaan

1. Tekan **Pilih file**, kemudian pilih satu atau beberapa file.
2. Alternatifnya, gunakan **Bagikan/Share** dari aplikasi lain dan pilih Marka untuk mengirim satu atau beberapa file langsung ke antrean.
3. Periksa antrean file dan hapus file yang tidak ingin dikonversi.
4. Tekan **Konfirmasi & mulai konversi**. File diproses berurutan untuk mengendalikan penggunaan memori.
5. Periksa hasil setiap file. Tombol **Lihat Markdown** menampilkan isi hasil; tombol ekspor menyimpan file tersebut.
6. Untuk satu dokumen gabungan, tekan **3. Gabungkan & simpan satu file .md**. Semua hasil yang berhasil akan disatukan dengan daftar isi dan bagian terpisah per sumber. Penggabungan bersifat opsional.
7. Riwayat hasil disimpan di penyimpanan internal aplikasi agar tetap tersedia setelah aplikasi ditutup. Folder keluaran juga dapat dipilih untuk mengekspor salinan ke lokasi lain.

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

## Pemrosesan gambar, PDF scan, audio, dan video

- Gambar didekode langsung dari stream dan menggunakan downsampling sebelum OCR; resolusi gambar asli dan ukuran file tetap dicatat jika metadata ukuran tersedia.
- OCR gambar memakai ML Kit Text Recognition on-device dengan batas sisi hasil decode sekitar 2048 piksel untuk mengendalikan RAM.
- PDF diproses per halaman: halaman yang memiliki lapisan teks diekstrak langsung, sedangkan halaman kosong atau hanya memiliki potongan teks pendek dapat menjalani OCR.
- OCR PDF lokal dibatasi hingga maksimal 50 halaman yang memerlukan OCR; jumlah halaman PDF yang diproses dibatasi hingga 1.000. Peringatan ditampilkan jika ada halaman yang tidak diperiksa.
- Render OCR PDF menurunkan skala berdasarkan ukuran halaman dan membatasi sisi bitmap hingga sekitar 2.048 piksel untuk mengendalikan RAM.
- Video dibaca melalui URI tanpa menyalin seluruh file ke RAM. OCR memakai hingga tiga cuplikan skala kecil pada titik waktu yang tersebar dan menggunakan satu recognizer untuk seluruh cuplikan. Pada Android API 26, OCR frame dilewati agar tidak memuat frame penuh ke RAM.
- Audio dibaca melalui decoder Android untuk mengekstrak metadata seperti durasi, judul, artis, album, genre, bitrate, dan jumlah track jika tersedia.
- Signature AAC ADTS, frame MPEG audio, dan Opus pada Ogg dibedakan lebih baik. Dukungan codec tetap bergantung pada decoder Android perangkat.
- Transkripsi ucapan/lirik audio belum tersedia. OCR video membaca teks yang terlihat pada cuplikan, bukan dialog atau subtitle lengkap.

## Perbaikan stabilitas dan penggunaan memori pada 1.7.0

- Isi Markdown tiap hasil disimpan sebagai berkas riwayat di penyimpanan internal aplikasi. Daftar hasil hanya menyimpan metadata dan lokasi berkas, sehingga isi tidak terus berada di RAM.
- Riwayat dipulihkan saat aplikasi dibuka kembali dan dibatasi hingga 50 hasil atau sekitar 100 MB. Hasil terlama dibersihkan saat batas tercapai; pengguna dapat menghapus seluruh riwayat melalui tombol **Hapus hasil**.
- Antrean dan status batch dikelola oleh `ViewModel`, sehingga pergantian orientasi layar tidak menghilangkan hasil yang sudah selesai.
- Pembatalan dilakukan dengan aman pada batas antarfile: file yang sedang aktif dituntaskan terlebih dahulu, sedangkan file yang belum diproses dikembalikan ke antrean.
- Nama file antrean dibaca di thread latar, dan izin baca SAF disimpan saat penyedia dokumen mengizinkan.
- Penggabungan Markdown hanya dihitung ketika diminta, bukan setiap kali daftar hasil berubah. Ekspor dokumen gabungan menulis isi berkas riwayat secara bertahap, bukan menggabungkan seluruh sumber menjadi satu string besar di RAM.
- Pratinjau dokumen gabungan dibatasi untuk sumber hingga sekitar 8 MB; ekspor langsung tetap tersedia untuk dokumen yang lebih besar.
- Preview satu file menampilkan judul, paragraf, daftar, tabel, kutipan, dan blok kode sebagai elemen visual, sementara file ekspor tetap berupa Markdown asli.
- Pemindaian ZIP membatasi jumlah entri, ukuran entri, dan total data dekompresi termasuk entri yang dilewati. XLSX/ODS juga membatasi hasil hingga 5.000 baris dan 256 kolom per lembar, serta memberi peringatan ketika hasil dipotong.
- CSV mencoba mendeteksi pemisah koma, titik koma, tab, dan pipe. JSON/JSONL kini menerima nilai primitif seperti string, angka, boolean, dan null.
- Signature AAC ADTS, frame MPEG audio, dan Opus di dalam Ogg dibedakan lebih baik.

## Batasan yang diketahui

- PDF diproses per halaman. Halaman hasil scan atau halaman campuran yang lapisan teksnya kosong/pendek dapat menjalani OCR lokal maksimal 50 halaman yang memerlukan OCR. PDF di atas 1.000 halaman tidak diproses seluruhnya.
- DOC, XLS, dan PPT biner versi lama belum didukung.
- XLSX menampilkan nilai sel yang tersimpan; rumus tidak dihitung ulang. Format sel, grafik, dan tata letak spreadsheet tidak dipertahankan.
- PPTX mengekstrak teks per slide; gambar, animasi, catatan pembicara, dan layout tidak dipertahankan.
- ODT/ODP mengekstrak paragraf dan heading; tata letak dan media tertanam tidak dipertahankan. ODS diekstrak sebagai tabel.
- EPUB mengekstrak isi HTML/XHTML; urutan bab mengikuti urutan nama entri arsip dan dapat berbeda dari spine pada sebagian buku.
- DOCX mengambil paragraf dan heading dasar; tabel kompleks, gambar, catatan kaki, header/footer, dan tata letak belum dipertahankan.
- OCR bahasa/aksara tertentu, tulisan tangan, gambar buram, atau teks yang sangat kecil dapat keliru atau tidak terdeteksi.
- Audio dan video yang codec-nya tidak didukung decoder perangkat hanya menghasilkan laporan dasar dengan peringatan.
- Batas ukuran file dokumen/arsip yang dibaca ke buffer adalah 64 MB. Pembaca arsip membatasi konten teks terpilih hingga 40 MB, entri terpilih hingga 12 MB, entri lain hingga 32 MB, dan total hasil dekompresi arsip hingga 80 MB. DOCX dan pendeteksian jenis arsip memiliki batas dekompresi tersendiri. Gambar diproses dengan stream; audio/video menggunakan URI/metadata tanpa menyalin seluruh media ke RAM.
- Riwayat Markdown disimpan di direktori internal aplikasi, bukan cache sistem, dengan batas 50 hasil atau sekitar 100 MB. Riwayat dapat dipulihkan setelah aplikasi ditutup; riwayat dihapus ketika aplikasi di-uninstall atau pengguna menekan Hapus hasil.
- Selalu tinjau hasil Markdown sebelum digunakan lebih lanjut. Konversi tidak menjamin format identik dengan tampilan dokumen asli.

## Privasi dan penyimpanan

Pemrosesan dan riwayat dilakukan secara lokal. File asli tidak diubah dan tidak diunggah ke server. Folder keluaran disimpan sebagai URI Storage Access Framework dengan izin yang diberikan Android; jika izin dicabut, pengguna perlu memilih folder kembali. Riwayat internal dihapus melalui tombol Hapus hasil atau saat aplikasi di-uninstall.

## Build

- Platform: Android native, Kotlin, Jetpack Compose
- Minimum SDK: 26
- Target SDK: 35
- JDK: 17
- Gradle: 8.9
- ABI APK: ARM64 (`arm64-v8a`) dan ARMv7 (`armeabi-v7a`); x86/x86_64 tidak disertakan.
- Build debug: `gradle --no-daemon :app:assembleDebug`
- Tes unit: `gradle --no-daemon :app:testDebugUnitTest`
- Android lint: `gradle --no-daemon :app:lintDebug`

GitHub Actions menjalankan tes unit, lint, dan build debug pada push ke `main` serta pull request menuju `main`.
