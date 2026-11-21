# Universal File to Markdown Converter
Aplikasi Android native untuk mengonversi file menjadi Markdown secara lokal.

## Build APK
GitHub Actions membangun APK debug pada setiap push ke main dan mengunggah artifact bernama universal-file-to-markdown-debug.

## Format yang diproses pada versi 1.0
TXT, MD, LOG, source code, CSV/TSV, JSON, HTML, XML/SVG, RTF, dan ekstraksi paragraf dasar DOCX.

## Batasan
Belum tersedia parser PDF/OCR, DOC biner, XLS/XLSX, PPT/PPTX, OCR gambar, EPUB, serta ekstraksi gambar/tabel/footnote DOCX. DOCX saat ini hanya mengekstrak teks paragraf. Aplikasi harus menampilkan peringatan atas keterbatasan ini.

## Stack
Kotlin, Jetpack Compose, Android Storage Access Framework, Android JSON/XML APIs. Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 8.9, JDK 17.