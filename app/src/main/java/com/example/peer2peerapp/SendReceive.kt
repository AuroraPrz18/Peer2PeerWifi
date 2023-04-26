package com.example.peer2peerapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.TextView
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class SendReceive (
    private val socket: Socket,
    val txtMsg: TextView
) : Thread() {
    lateinit var inputStream: InputStream
    lateinit var outputStream: OutputStream
    lateinit var handler: Handler

    override fun run(){
        try {
            Looper.prepare()
            Handler(object: Handler.Callback {
                override fun handleMessage(msg: Message): Boolean {

                    if(msg.what == MESSAGE_READ){
                        var readBuff = msg.obj as ByteArray
                        var msgAux = String(readBuff, 0, msg.arg1)
                        txtMsg.text = msgAux
                    }
                    return true
                }
            })
            Looper.loop()


            inputStream = socket.getInputStream()
            outputStream = socket.getOutputStream()
            var buffer: ByteArray = ByteArray(1024)
            var bytes: Int = 0
            while(socket!=null){ //listen the messages
                bytes = inputStream.read(buffer)
                if(bytes>0){
                    handler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget()
                }
            }

        }catch (e: IOException){
            e.printStackTrace()
        }finally {

        }
    }

    fun writeMsg(bytes: ByteArray){
        try {
            outputStream.write(bytes) // TODO: No inicializado
        }catch (e: IOException){
            e.printStackTrace()
        }
    }


}