# Universal File to Markdown Converter

Aplikasi Android native untuk mengonversi dokumen menjadi Markdown secara lokal. File asli tidak diubah dan aplikasi tidak mengunggah dokumen ke server.

## Build APK
Setiap push ke branch `main` menjalankan GitHub Actions. Artifact debug bernama `universal-file-to-markdown-debug`.

## Format yang saat ini diproses
- TXT, MD, Markdown, LOG
- Source code: Python, JavaScript/TypeScript, Java/Kotlin, C/C++, C#, PHP, Go, Rust, Swift, CSS/SCSS, Shell, SQL, Ruby, Dart, Scala, dan beberapa format lain sebagai code fence
- CSV/TSV dengan dukungan pemisah dan tanda kutip
- JSON dan JSONL/NDJSON
- HTML/HTM/XHTML menjadi teks
- PDF berbasis teks melalui PDFBox-Android
- DOCX: ekstraksi paragraf dan heading dasar
- YAML, TOML, INI, XML/SVG, RTF, EML, MHTML, ICS, VCF sebagai teks/code block sesuai format

## Batasan yang harus diketahui
- PDF hasil scan yang hanya berisi gambar belum didukung OCR.
- DOCX belum mempertahankan tabel kompleks, gambar, catatan kaki, header/footer, dan seluruh pemformatan.
- DOC biner, XLS/XLSX, PPT/PPTX, ODT/ODS/ODP, EPUB, dan format gambar belum didukung.
- Ekstraksi PDF/DOCX dapat berbeda dari tata letak asli. Selalu periksa preview sebelum menyimpan.
- Batas ukuran input 100 MB; ketersediaan memori perangkat dapat membatasi file yang sangat besar.

## Teknologi
Kotlin, Jetpack Compose, Android Storage Access Framework, Android JSON/XML APIs, PDFBox-Android 2.0.27.0. AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9, JDK 17.
