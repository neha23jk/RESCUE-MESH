package com.meshsos.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.meshsos.MeshSOSApplication
import com.meshsos.data.model.SosPacket
import kotlinx.coroutines.flow.Flow

/**
 * ViewModel for History Screen
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val app = application as MeshSOSApplication
    
    val allPackets: Flow<List<SosPacket>> = app.sosRepository.getAllPackets()
    
    val ownPackets: Flow<List<SosPacket>> = app.sosRepository.getOwnPackets()
    
    val receivedPackets: Flow<List<SosPacket>> = app.sosRepository.getReceivedPackets()
}
