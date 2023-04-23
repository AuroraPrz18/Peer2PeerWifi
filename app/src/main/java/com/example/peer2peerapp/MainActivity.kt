package com.example.peer2peerapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.*
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import java.io.IOException
import java.net.Socket

const val PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION = 1001

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
    var deviceConnected: WifiP2pDevice? = null
    // otros
    lateinit var list: ListView
    lateinit var txtStatus: TextView
    val socket = Socket()
    var receiver: BroadcastReceiver? = null

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
        mChannel?.also { channel ->
            manager?.connect(channel, config, object : WifiP2pManager.ActionListener {

                override fun onSuccess() {
                    Log.d("P2P Connection accepted", "Connection with "+device.deviceName)
                    deviceConnected = device
                    Toast.makeText(this@MainActivity, "Conexion exitosa con "+device.deviceName, Toast.LENGTH_LONG).show()
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
            if(deviceConnected==null){
                Toast.makeText(this, "Error: Asegurese de conectarse a un dispositivo primero dando clic en el", Toast.LENGTH_SHORT).show()
                return
            }
            var hostAdd = deviceConnected!!.deviceAddress

            /*val serviceIntent = Intent(this, MessageTransferService::class.java)
            serviceIntent.action = "com.example.peertopeerapp.SEND_MESSAGE"
            serviceIntent.putExtra("EXTRA_MESSAGE", "HOLI")
            serviceIntent.putExtra("PORT_NUMBER", 8888.toInt())
            serviceIntent.putExtra("DEVICE_IP", hostAdd)
            startService(serviceIntent)*/
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

    /*
    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        // The owner IP is now known.
        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.
        if (info!=null && info.groupFormed && info.isGroupOwner) {
            ServerAsyncTaskClass(
                this,
                findViewById<View>(R.id.txtStatus) as TextView
            ).execute()
        }
    }*/
}