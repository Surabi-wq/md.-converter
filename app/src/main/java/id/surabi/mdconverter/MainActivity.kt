package id.surabi.mdconverter

import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConvertedFile(val name:String,val format:String,val markdown:String,val warnings:List<String>,val error:String?)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  PDFBoxResourceLoader.init(applicationContext)
  setContent { Screen() }
 }

 @OptIn(ExperimentalMaterial3Api::class)
 @Composable private fun Screen(){
  val files= remember { mutableStateListOf<ConvertedFile>() }
  var busy by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf("Pilih dokumen atau media untuk mulai.") }
  var preview by remember { mutableStateOf<ConvertedFile?>(null) }

  val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
   if(uris.isNotEmpty()) processFiles(uris,files) { isBusy,status -> busy=isBusy;message=status }
  }
  val saver=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")){uri->
   val item=preview
   if(uri!=null && item!=null){
    try{
     val output=contentResolver.openOutputStream(uri) ?: error("Lokasi penyimpanan tidak dapat dibuka.")
     output.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(item.markdown);writer.flush() }
     message="Markdown berhasil disimpan."
    }catch(e:Exception){ message="Gagal menyimpan: ${e.message ?: "kesalahan tidak diketahui"}" }
   }
  }

  MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF315F55),secondary=Color(0xFF82A99A),background=Color(0xFFF4F7F5))){
   Scaffold(
    containerColor=Color(0xFFF4F7F5),
    topBar={CenterAlignedTopAppBar(title={Text("MD Converter",fontWeight=FontWeight.Bold)},navigationIcon={Icon(Icons.Default.Description,null,Modifier.padding(start=16.dp),tint=Color(0xFF315F55))})}
   ){padding->
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
     item{
      Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFFE3EEE9))){
       Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Konversi dokumen dan media ke Markdown",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Color(0xFF183D36))
        Text("Ekstrak teks, metadata, dan informasi media secara lokal. File asli tetap utuh.",color=Color(0xFF49635D))
        Button(onClick={picker.launch(arrayOf("*/*"))},enabled=!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){
         Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Pilih file")
        }
        Text("Bisa memilih beberapa file sekaligus",fontSize=12.sp,color=Color.Gray)
       }
      }
     }
     item{Row(verticalAlignment=Alignment.CenterVertically){Text("Antrean konversi",fontSize=18.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Text("${files.size} file",color=Color.Gray)}}
     if(files.isEmpty()) item{
      Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.White)){
       Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Icon(Icons.Default.FolderOpen,null,tint=Color(0xFF82A99A),modifier=Modifier.size(42.dp))
        Text("Belum ada file",fontWeight=FontWeight.SemiBold)
        Text("File yang dipilih akan tampil di sini.",color=Color.Gray,fontSize=13.sp)
       }
      }
     }
     items(files){file->
      Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(15.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
       Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
         Icon(Icons.Default.InsertDriveFile,null,tint=Color(0xFF315F55));Spacer(Modifier.width(9.dp))
         Column(Modifier.weight(1f)){
          Text(file.name,fontWeight=FontWeight.SemiBold,maxLines=1)
          Text(file.format.uppercase()+" • "+if(file.error==null)"Selesai" else "Gagal",fontSize=12.sp,color=if(file.error==null)Color(0xFF28734E) else Color.Red)
         }
         IconButton(onClick={preview=file;saver.launch(file.name.substringBeforeLast('.',file.name)+".md")},enabled=file.error==null){Icon(Icons.Default.Download,"Simpan")}
        }
        file.warnings.forEach{Text("⚠ "+it,fontSize=12.sp,color=Color(0xFF946B20))}
        if(file.error!=null)Text(file.error,color=Color.Red,fontSize=12.sp)
        if(file.error==null)OutlinedButton(onClick={preview=file},modifier=Modifier.fillMaxWidth()){Text("Lihat Markdown")}
       }
      }
     }
     item{
      Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.White)){
       Column(Modifier.padding(16.dp)){
        Text("Status",fontWeight=FontWeight.Bold)
        Text(message,color=Color.Gray,fontSize=13.sp)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
       }
      }
     }
     item{Text("Pemrosesan lokal di perangkat. File tidak diunggah ke server.",fontSize=11.sp,color=Color.Gray,modifier=Modifier.padding(vertical=8.dp))}
    }
   }
   preview?.let{item->
    AlertDialog(
     onDismissRequest={preview=null},
     title={Text("Preview Markdown")},
     text={Column(Modifier.heightIn(max=420.dp)){Text(item.name,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));Text(item.markdown,fontSize=12.sp)}},
     confirmButton={TextButton(onClick={saver.launch(item.name.substringBeforeLast('.',item.name)+".md")}){Text("Ekspor .md")}},
     dismissButton={TextButton(onClick={preview=null}){Text("Tutup")}}
    )
   }
  }
 }

 private fun processFiles(uris:List<Uri>,files:MutableList<ConvertedFile>,onStatus:(Boolean,String)->Unit){
  lifecycleScope.launch{
   onStatus(true,"Memproses ${uris.size} file…")
   for((index,uri) in uris.withIndex()){
    val name=queryName(uri)
    val converted=withContext(Dispatchers.IO){
     try{
      val result=ConverterEngine.convert(this@MainActivity,uri,name)
      ConvertedFile(name,result.format,result.markdown,result.warnings,null)
     }catch(e:Exception){
      ConvertedFile(name,name.substringAfterLast('.', "unknown"),"",emptyList(),e.message ?: "Konversi gagal.")
     }
    }
    files.add(converted)
    onStatus(true,"Memproses file ${index+1} dari ${uris.size}…")
   }
   onStatus(false,"Selesai. ${uris.size} file telah diperiksa.")
  }
 }

 private fun queryName(uri:Uri):String{
  contentResolver.query(uri,null,null,null,null)?.use{cursor->
   val index=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
   if(cursor.moveToFirst() && index>=0)return cursor.getString(index) ?: "dokumen"
  }
  return uri.lastPathSegment ?: "dokumen"
 }
}
