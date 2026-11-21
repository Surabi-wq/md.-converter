package id.surabi.mdconverter

import android.content.ContentResolver
import android.net.Uri
import android.text.Html
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

data class ConversionResult(val markdown:String,val format:String,val warnings:List<String>)

object ConverterEngine {
 private const val LIMIT=100L*1024*1024
 fun convert(resolver:ContentResolver,uri:Uri,name:String):ConversionResult {
  val bytes=resolver.openInputStream(uri)?.use { input ->
   val out=ByteArrayOutputStream(); val buffer=ByteArray(8192); var total=0L
   while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=LIMIT){"Ukuran file melebihi 100 MB."};out.write(buffer,0,n)}
   out.toByteArray()
  } ?: error("File tidak dapat dibaca.")
  require(bytes.isNotEmpty()){"File kosong."}
  val ext=name.substringAfterLast('.', "").lowercase()
  val text=bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
  val warnings=mutableListOf<String>()
  val isZip=bytes.size>3 && bytes[0]==0x50.toByte() && bytes[1]==0x4b.toByte()
  val body=when {
   ext=="docx" && isZip -> docx(bytes,warnings)
   ext in setOf("txt","md","log") -> text.trimEnd()
   ext in setOf("py","js","ts","jsx","tsx","java","kt","c","cpp","cs","php","go","rs","swift","css","scss","sh","bat","sql") -> "``${ext}\n${text.trimEnd()}\n```"
   ext=="csv" || ext=="tsv" -> table(text,if(ext=="tsv") '\t' else ',')
   ext=="json" -> try { val s=text.trim(); val pretty=if(s.startsWith("[")) JSONArray(s).toString(2) else JSONObject(s).toString(2); "```json\n${pretty}\n```" } catch(_:Exception){error("JSON tidak valid.")}
   ext in setOf("html","htm","xhtml") -> Html.fromHtml(text.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\\\1>"),""),Html.FROM_HTML_MODE_COMPACT).toString().trim()
   ext=="xml" || ext=="svg" -> "```xml\n${text}\n```"
   ext=="rtf" -> text.replace(Regex("\\\\'[0-9a-fA-F]{2}"),"").replace(Regex("\\\\[a-zA-Z]+-?\\\\d* ?"),"").replace(Regex("[{}]"),"").trim()
   ext=="pdf" -> {warnings.add("PDF dan OCR belum tersedia pada versi ini."); "> PDF belum dapat diekstrak oleh versi aplikasi ini."}
   ext in setOf("doc","xls","xlsx","xlsm","ppt","pptx","odt","ods","odp","epub","png","jpg","jpeg","webp","bmp","tiff","gif","heic","avif","mhtml","eml","msg","ics","vcf","tex","ipynb","yaml","yml","toml","ini","jsonl","wps","fb2","mobi") -> {warnings.add("Parser khusus untuk format ini belum tersedia."); "> Format .$ext dikenali, tetapi belum dapat dikonversi pada versi ini."}
   else -> error("Format .$ext tidak didukung. File asli tetap aman.")
  }
  return ConversionResult("# ${name.substringBeforeLast('.',name).replace("#","\\\\#")}\n\n${body}",ext.ifBlank{"unknown"},warnings)
 }
 private fun table(value:String,delimiter:Char):String {
  val rows=value.lineSequence().filter{it.isNotBlank()}.map{it.split(delimiter).map{c->c.trim().replace("|","\\\\|")}}.toList()
  if(rows.isEmpty())return ""
  val count=rows.maxOf{it.size}
  fun render(r:List<String>)="| "+(0 until count).joinToString(" | "){r.getOrElse(it){""}}+" |"
  return buildString { appendLine(render(rows.first()));appendLine("| "+(0 until count).joinToString(" | "){"---"}+" |");rows.drop(1).forEach{appendLine(render(it))} }.trimEnd()
 }
 private fun docx(bytes:ByteArray,warnings:MutableList<String>):String {
  var xml:String?=null
  ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
   while(true){val e=zip.nextEntry?:break;if(e.name=="word/document.xml"){val out=ByteArrayOutputStream();zip.copyTo(out);xml=out.toString("UTF-8");break}}
  }
  val source=xml?:error("DOCX tidak valid: document.xml tidak ditemukan.")
  val parser=Xml.newPullParser();parser.setInput(source.reader())
  val out=StringBuilder();var paragraph=StringBuilder();var inP=false;var inText=false;var event=parser.eventType
  while(event!=XmlPullParser.END_DOCUMENT){
   when(event){
    XmlPullParser.START_TAG -> when(parser.name){"p"->{inP=true;paragraph=StringBuilder()},"t"->inText=true,"tab"->if(inP)paragraph.append("    "),"br"->if(inP)paragraph.append('\n')}
    XmlPullParser.TEXT -> if(inP&&inText)paragraph.append(parser.text)
    XmlPullParser.END_TAG -> when(parser.name){"t"->inText=false;"p"->{if(paragraph.isNotEmpty())out.appendLine(paragraph.toString()).appendLine();inP=false}}
   }
   event=parser.next()
  }
  warnings.add("DOCX: hanya teks paragraf diekstrak. Heading styles, tabel, gambar, catatan kaki, header/footer, dan format teks belum dipertahankan.")
  return out.toString().trim()
 }
}