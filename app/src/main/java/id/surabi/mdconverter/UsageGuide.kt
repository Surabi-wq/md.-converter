package id.surabi.mdconverter

internal object UsageGuide {
    val steps = listOf(
        "Pilih file: tekan Pilih file untuk membuka pemilih dokumen.",
        "Bagikan file: dari aplikasi lain, gunakan menu Bagikan/Share lalu pilih Marka. Satu atau beberapa file dapat diterima sekaligus.",
        "Periksa antrean: periksa nama dokumen dan hapus file yang tidak ingin diproses.",
        "Konversi: tekan Konfirmasi & mulai konversi. File diproses berurutan untuk membatasi penggunaan memori.",
        "Batalkan dengan aman: tombol Batalkan akan menyelesaikan file yang sedang aktif, lalu mengembalikan file yang belum diproses ke antrean.",
        "Periksa dan ekspor: lihat status, peringatan, dan preview Markdown. Hasil disimpan sebagai riwayat lokal dan dapat dibuka kembali setelah aplikasi ditutup.",
        "Gabungkan (opsional): pilih Gabungkan & simpan satu file .md untuk menyusun hasil yang berhasil dengan daftar isi.",
        "Kelola riwayat: tombol Hapus hasil membersihkan riwayat lokal dan berkas hasil yang tersimpan di dalam aplikasi. Hasil penting juga sebaiknya diekspor ke folder keluaran."
    )

    const val naming = "Contoh: laporan.pdf menjadi laporan.md. Ekstensi sumber diganti menjadi .md; nama dasarnya dipertahankan."
    const val merge = "Jika beberapa file digabungkan, nama dokumen gabungan menjadi gabungan-namafile-pertama.md. Setiap sumber tetap memiliki bagian sendiri."
    const val privacy = "Konversi dilakukan secara lokal di perangkat. File asli tidak diubah dan tidak diunggah ke server. Riwayat hasil disimpan di penyimpanan internal aplikasi, maksimal 50 hasil atau sekitar 100 MB, dan dapat dihapus melalui tombol Hapus hasil."
}
