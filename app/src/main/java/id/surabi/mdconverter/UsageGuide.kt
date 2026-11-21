package id.surabi.mdconverter

internal object UsageGuide {
    val steps = listOf(
        "Pilih file: tekan Pilih file, atau bagikan dokumen/media dari aplikasi lain lalu pilih Marka pada menu Share Android.",
        "Periksa antrean: cek nama file yang diterima dan hapus file yang tidak ingin diproses.",
        "Konversi: tekan Konfirmasi & mulai konversi. File diproses berurutan agar penggunaan memori lebih terkendali.",
        "Batalkan dengan aman: tombol Batalkan akan menghentikan antrean setelah file yang sedang aktif selesai diproses.",
        "Periksa hasil: lihat status dan peringatan setiap file, lalu gunakan Lihat Markdown untuk memeriksa isinya.",
        "Batch berikutnya: hasil konversi sebelumnya tetap ditampilkan saat antrean baru diproses. Gunakan Hapus hasil hanya bila daftar lama memang ingin dibersihkan.",
        "Gabungkan (opsional): hasil yang berhasil dapat disusun menjadi satu dokumen Markdown dengan daftar isi. Penggabungan dihitung saat diminta, bukan setiap kali daftar berubah.",
        "Simpan: folder keluaran dapat dipilih untuk menyimpan hasil tiap file otomatis. Tanpa folder keluaran, Android akan meminta lokasi penyimpanan."
    )

    const val naming = "Contoh: laporan.pdf menjadi laporan.md. Ekstensi sumber diganti menjadi .md; nama dasarnya dipertahankan."
    const val merge = "Jika beberapa file digabungkan, nama dokumen gabungan menjadi gabungan-namafile-pertama.md. Setiap sumber tetap memiliki bagian sendiri."
    const val privacy = "Semua proses dilakukan secara lokal di perangkat. File asli tidak diubah dan tidak diunggah ke server."
}
