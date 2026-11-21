package id.surabi.mdconverter

internal object UsageGuide {
    val steps = listOf(
        "Pilih file: tekan tombol Pilih file, lalu tandai satu atau beberapa dokumen/media.",
        "Periksa antrean: lihat nama file yang dipilih. Hapus file yang tidak ingin diproses.",
        "Konversi: tekan Konfirmasi & mulai konversi. Marka memproses file satu per satu untuk membatasi penggunaan memori.",
        "Periksa hasil: lihat status tiap file, pesan peringatan, dan gunakan Lihat Markdown untuk memeriksa isinya.",
        "Gabungkan (opsional): pada bagian Dokumen gabungan, tekan Gabungkan & simpan satu file .md. Semua hasil yang berhasil akan disusun sebagai satu dokumen dengan daftar isi.",
        "Simpan: bila folder keluaran telah dipilih, hasil tiap file disimpan dengan nama sumber dan ekstensi .md. Dokumen gabungan disimpan sebagai file terpisah. Tanpa folder keluaran, Android akan meminta lokasi penyimpanan."
    )

    const val naming = "Contoh: laporan.pdf menjadi laporan.md. Ekstensi sumber diganti menjadi .md; nama dasarnya dipertahankan."
    const val merge = "Jika beberapa file digabungkan, nama dokumen gabungan menjadi gabungan-namafile-pertama.md. Setiap sumber tetap memiliki bagian sendiri."
    const val privacy = "Semua proses dilakukan secara lokal di perangkat. File asli tidak diubah dan tidak diunggah ke server."
}
