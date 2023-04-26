package com.example.peer2peerapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Parcelable
import android.util.Log
import android.widget.Toast

class WifiP2PBroadcastReceiver (
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val activity: MainActivity
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action: String? = intent.action
        when (action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                // Check to see if Wi-Fi is enabled and notify appropriate activity
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                when (state) {
                    WifiP2pManager.WIFI_P2P_STATE_ENABLED -> {
                        Toast.makeText(context, "Wifi esta habilitado", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(context, "Wifi NO esta habilitado", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> { //es llamado solo si detecta pares
                manager?.requestPeers(channel, activity)
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                Log.d("peers", "WIFI_P2P_CONNECTION_CHANGED_ACTION")
                // Respond to new connection or disconnections
                if (manager == null) {
                    return
                }
                val networkInfo = intent
                    .getParcelableExtra<Parcelable>(WifiP2pManager.EXTRA_NETWORK_INFO) as NetworkInfo?
                if (networkInfo!!.isConnected) {
                    // we are connected with the other device, request connection
                    // info to find group owner IP
                    manager.requestConnectionInfo(channel, activity.connectionInfoListener)
                }
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                // Respond to this device's wifi state changing
            }
        }
    }
}