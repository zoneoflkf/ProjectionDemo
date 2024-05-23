package com.lkf.remotecontrol.net.client

interface ClientStateListener {
    fun onOpen(client: ProjectionClient)
    fun onClose(client: ProjectionClient)
    fun onError(client: ProjectionClient, ex: Exception?)
}