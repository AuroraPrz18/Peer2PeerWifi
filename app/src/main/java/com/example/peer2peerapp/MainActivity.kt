package com.example.peer2peerapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.*
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.*
import androidx.annotation.RestrictTo
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.util.concurrent.Executors

const val PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION = 1001
const val MESSAGE_READ = 1
var sendReceive: SendReceive? = null

class MainActivity : AppCompatActivity(), WifiP2pManager.PeerListListener {
    // wifi peer2peer
    lateinit var wifiManager: WifiManager
    lateinit var peers: MutableList<WifiP2pDevice>
    lateinit var devicesName: MutableList<String>
    lateinit var p2pDevices: MutableList<WifiP2pDevice>
    val manager: WifiP2pManager? by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    }
    var mChannel: WifiP2pManager.Channel? = null
    var deviceConnected: WifiP2pDevice? = null // TODO: Checar cuando cambia su valor
    //Socket
    val socket = Socket()
    lateinit var serverClass: ServerClass
    lateinit var clientClass: ClientClass

    // otros
    lateinit var list: ListView
    lateinit var txtStatus: TextView
    var receiver: BroadcastReceiver? = null
    var connectionInfoListener = object : WifiP2pManager.ConnectionInfoListener{
        override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
            Log.d("peers", "Conexion info available")
            if(info==null) return
            // The owner IP is now known.
            // TODO: Especificar quien es el owner
            val groupOwner = info.groupOwnerAddress
            if (info.groupFormed && info.isGroupOwner) {
                // Do whatever tasks are specific to the group owner.
                // One common case is creating a group owner thread and accepting
                // incoming connections.
                txtStatus.text = "Host"
                //TODO: Descomentar
                /*
                lifecycleScope.launch{
                    withContext(Dispatchers.IO) {
                        serverClass = ServerClass(findViewById(R.id.txtMessageReceived) as TextView)
                        serverClass.run()
                    }
                }*/
            }else if (info.groupFormed) {
                // The other device acts as the peer (client). In this case,
                // you'll want to create a peer thread that connects
                // to the group owner.
                txtStatus.text = "Client"
                //TODO: Descomentar
                /*deviceConnected = WifiP2pDevice()
                deviceConnected!!.deviceAddress = groupOwner.hostAddress
                lifecycleScope.launch{
                    withContext(Dispatchers.IO) {
                        clientClass = ClientClass(groupOwner, findViewById(R.id.txtMessageReceived) as TextView)
                        clientClass.run()
                    }
                }*/
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        txtStatus = findViewById(R.id.txtStatus)
        list = findViewById(R.id.list)

        initializeP2PComponents()
        initializeListeners()


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf<String>(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION
            )
        }
    }

    private fun initializeListeners() {
        var btnDiscover = findViewById<Button>(R.id.btnDiscover)
        btnDiscover.setOnClickListener {
            discover()
        }
        var btnSend = findViewById<Button>(R.id.btnSend)
        btnSend.setOnClickListener {
            send()
        }
        list.onItemClickListener = object: AdapterView.OnItemClickListener{
            override fun onItemClick(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                var device = peers[id.toInt()]
                Log.d("P2P Connection request", device.deviceName)
                connectWithDevice(device)
            }

        }
    }

    private fun initializeP2PComponents() {
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if(wifiManager.isWifiEnabled){
            wifiManager.setWifiEnabled(true)
        }
        // registrar la aplicación con el marco de trabajo de P2P Wi-Fi
        mChannel = manager?.initialize(this, mainLooper, null)
        // Crear una instancia del receptor de emisión y asi poder notificar a la actividad
        mChannel?.also { channel ->
            receiver = WifiP2PBroadcastReceiver(manager!!, channel, this)
        }
        peers= mutableListOf()
        devicesName= mutableListOf()
        p2pDevices= mutableListOf()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION -> if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Log.e(
                    "Location",
                    "Fine location permission is not granted!"
                )
                finish()
            }
        }
    }

    private fun discover(){
        manager?.discoverPeers(mChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                //el sistema transmite el intent WIFI_P2P_PEERS_CHANGED_ACTION
                txtStatus.text = "Buscando dispositivos..."
            }

            override fun onFailure(reasonCode: Int) {
                txtStatus.text = "La busqueda de dispositivos ha fallado"
            }
        })
    }

    private fun connectWithDevice(device: WifiP2pDevice){
        val config = WifiP2pConfig()
        config.deviceAddress = device.deviceAddress
        config.wps.setup = WpsInfo.PBC
        mChannel?.also { channel ->
            manager?.connect(channel, config, object : WifiP2pManager.ActionListener {

                override fun onSuccess() {
                    Log.d("P2P Connection accepted", "Connection with "+device.deviceName)
                    deviceConnected = device
                    Toast.makeText(this@MainActivity, "Conexion exitosa con "+device.deviceName, Toast.LENGTH_LONG).show()
                    Log.d("peers", "Connect - WIFI_P2P_CONNECTION_CHANGED_ACTION")
                    // Respond to new connection or disconnections
                    if (manager == null) {
                        return
                    }
                    manager!!.requestConnectionInfo(channel, connectionInfoListener)
                }

                override fun onFailure(reason: Int) {
                    Log.d("P2P Connection accepted", "Connection with "+device.deviceName)
                    Toast.makeText(this@MainActivity, "La conexion a fallado con "+device.deviceName, Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun send(){
        try {
            if(deviceConnected==null){ //TODO: Checar esta nuladidad
                Toast.makeText(this, "Error: Asegurese de conectarse a un dispositivo primero dando clic en el", Toast.LENGTH_SHORT).show()
                return
            }
            var hostAdd = deviceConnected!!.deviceAddress
            val msg = findViewById<EditText>(R.id.txtMessage).text.toString()
            if(sendReceive!=null){
                sendReceive!!.writeMsg(msg.toByteArray())
            }

        } catch (e: IOException) {
            //catch logic
        } finally {
            //Clean up any open sockets when done transferring
            // //or if an exception occurred.
            socket.takeIf { it.isConnected }?.apply {
                close()
            }
        }
    }

    fun disconnect() {
        manager!!.removeGroup(mChannel, object : WifiP2pManager.ActionListener {
            override fun onFailure(reasonCode: Int) {
                Log.d(
                    "peers",
                    "Disconnect failed. Reason :$reasonCode"
                )
            }

            override fun onSuccess() {
                Log.d(
                    "peers",
                    "Disconnect succeded"
                )
            }
        })
    }

    val handler = Handler(object:Handler.Callback {
        override fun handleMessage(msg: Message): Boolean {
            if(msg.what == MESSAGE_READ){
                var readBuff = msg.obj as ByteArray
                var msgAux = String(readBuff, 0, msg.arg1)
                findViewById<TextView>(R.id.txtMessageReceived).text = msgAux
            }
            return true
        }
    })


    //////////////////////////////////// OVERRIDES ///////////////////////////////////////////////////
    override fun onResume() {
        super.onResume()
        // filtro de intents
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        //registrar receiver
        receiver?.also { receiverA ->
            registerReceiver(receiverA, intentFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        // desregistrar el receiver
        receiver?.also { receiverA ->
            unregisterReceiver(receiverA)
        }
    }

    // Listener que sera llamado desde el broadcast cuando haya cambios en los peers
    override fun onPeersAvailable(p0: WifiP2pDeviceList?) {
        Log.d("peers", "Se detectó un cambio en los dispositivos")
        if(p0!=null && !p0.deviceList.equals(peers)){ // si la lista no esta actualizada
            peers.clear()
            peers.addAll(p0.deviceList)

            devicesName.clear()
            p2pDevices.clear()
            peers.map {
                devicesName.add(it.deviceName)
                p2pDevices.add(it)
            }
            var adapter = ArrayAdapter(applicationContext, android.R.layout.simple_list_item_1,devicesName)
            list.adapter = adapter
        }
        if(peers.size==0){
            txtStatus.text = "No se encontraron dispositivos"
        }
    }

fun isPortAvailable(port: Int): Boolean {
    try {
        // Intenta crear un socket en el puerto especificado
        val socket = ServerSocket(port)

        // Si no se produce una excepción, el puerto está disponible
        socket.close()
        return true
    } catch (e: IOException) {
        // Si se produce una excepción, el puerto no está disponible
        return false
    }
}



//////////////////////////// Clases ///////////////////////////////////////
class ServerClassWithExecutor() :Thread(){
    lateinit var serverSocket:ServerSocket
    lateinit var inputStream: InputStream
    lateinit var  outputStream: OutputStream
    lateinit var socket: Socket

    override fun run() {
        try {
            serverSocket = ServerSocket(8888)
            socket = serverSocket.accept()
            inputStream =socket.getInputStream()
            outputStream = socket.getOutputStream()
        }catch (ex:IOException){
            ex.printStackTrace()
        }

        val executors = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())
        executors.execute(Runnable{
            kotlin.run {
                val buffer = ByteArray(1024)
                var byte:Int
                while (true){
                    try {
                        byte =  inputStream.read(buffer)
                        if(byte > 0){
                            var finalByte = byte
                            handler.post(Runnable{
                                kotlin.run {
                                    var tmpMeassage = String(buffer,0,finalByte)

                                    Log.i("Server class","$tmpMeassage")
                                }
                            })

                        }
                    }catch (ex:IOException){
                        ex.printStackTrace()
                    }
                }
            }
        })
    }

    fun write(byteArray: ByteArray){
        try {
            Log.i("Server write","$byteArray sending")
            outputStream.write(byteArray)
        }catch (ex:IOException){
            ex.printStackTrace()
        }
    }
}

class ClientClassWithExecutors(hostAddress: InetAddress): Thread() {

    var hostAddress: String = hostAddress.hostAddress
    lateinit var inputStream: InputStream
    lateinit var outputStream: OutputStream
    lateinit var socket: Socket

    fun write(byteArray: ByteArray){
        try {
            outputStream.write(byteArray)
        }catch (ex:IOException){
            ex.printStackTrace()
        }
    }

    override fun run() {
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(hostAddress,8888),500)
            inputStream = socket.getInputStream()
            outputStream = socket.getOutputStream()
        }catch (ex:IOException){
            ex.printStackTrace()
        }
        val executor = Executors.newSingleThreadExecutor()
        var handler =Handler(Looper.getMainLooper())

        executor.execute(kotlinx.coroutines.Runnable {
            kotlin.run {
                val buffer =ByteArray(1024)
                var byte:Int
                while (true){
                    try{
                        byte = inputStream.read(buffer)
                        if(byte>0){
                            val finalBytes = byte
                            handler.post(Runnable{
                                kotlin.run {
                                    val tmpMeassage = String(buffer,0,finalBytes)

                                    Log.i("client class", tmpMeassage)
                                }
                            })
                        }
                    }catch (ex:IOException){
                        ex.printStackTrace()
                    }
                }
            }
        })
    }

}
class ServerClass ( //Server Class
    private var txtMsg: TextView
) : Thread() {
    lateinit var serverSocket: ServerSocket
    lateinit var socket: Socket //cliente

    override fun run(){
        try {
            // crear server socket
            val isAvailable = isPortAvailable(8881) // Verifica si el puerto está disponible
            if (isAvailable) {
                Log.d("socket", "Puerto disponible para el server")
                serverSocket = ServerSocket(8881)
                serverSocket.use {
                    socket =serverSocket.accept()
                    sendReceive = SendReceive(socket, txtMsg)
                    sendReceive!!.start()
                }
            } else {
                Log.d("socket", "Puerto NO disponible para el server")
            }

        }catch (e: IOException){
            e.printStackTrace()
        }finally {

        }
    }
    fun isPortAvailable(port: Int): Boolean {
        try {
            // Intenta crear un socket en el puerto especificado
            val socket = ServerSocket(port)

            // Si no se produce una excepción, el puerto está disponible
            socket.close()
            return true
        } catch (e: IOException) {
            // Si se produce una excepción, el puerto no está disponible
            return false
        }
    }


}

class ClientClass ( //Client Class
    private val hostAddress: InetAddress,
    private val txtMsg: TextView
) : Thread() {
    lateinit var socket: Socket  //cliente

    override fun run(){
        try {
            Log.d("peers", "ClientClass start")
            socket = Socket()
            val address = "http://"+ hostAddress.hostAddress.toString()
            socket.connect(InetSocketAddress(hostAddress.hostAddress.toString(), 8881), 500)
            sendReceive = SendReceive(socket, txtMsg)
            sendReceive!!.start()
        }catch (e: IOException){
            e.printStackTrace()
        }finally {

        }
    }
    }
}