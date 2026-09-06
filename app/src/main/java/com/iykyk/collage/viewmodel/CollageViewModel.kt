package com.iykyk.collage.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iykyk.collage.data.SaveShareUtil
import com.iykyk.collage.model.PersonCluster
import com.iykyk.collage.model.ProcessingProgress
import com.iykyk.collage.model.ProcessingStage
import com.iykyk.collage.model.VideoResult
import com.iykyk.collage.pipeline.CollagePipeline
import com.iykyk.collage.pipeline.CollageTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class UiState {
    data object Idle : UiState()
    data class Processing(val progress: ProcessingProgress, val videoLabel: String) : UiState()
    data class Result(val result: VideoResult) : UiState()
    data class Error(val message: String) : UiState()
}

sealed class SaveState {
    data object Idle : SaveState()
    data object Saving : SaveState()
    data class Saved(val uri: Uri) : SaveState()
    data class Failed(val message: String) : SaveState()
}

class CollageViewModel(app: Application) : AndroidViewModel(app) {

    private val pipeline = CollagePipeline(app)

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    /** Currently selected collage template */
    private val _selectedTemplate = MutableStateFlow<CollageTemplate>(CollageTemplate.Editorial)
    val selectedTemplate: StateFlow<CollageTemplate> = _selectedTemplate.asStateFlow()

    /** Cached people list and label so template changes don't re-run the pipeline */
    private var cachedPeople: List<PersonCluster>? = null
    private var cachedLabel: String = ""

    /** True while a template-switch collage is being regenerated */
    private val _isRegenerating = MutableStateFlow(false)
    val isRegenerating: StateFlow<Boolean> = _isRegenerating.asStateFlow()

    fun processVideo(uri: Uri, label: String) {
        cachedPeople = null
        cachedLabel = label
        _uiState.value = UiState.Processing(ProcessingProgress(ProcessingStage.ExtractingFrames), label)
        viewModelScope.launch {
            try {
                pipeline.process(uri, label, _selectedTemplate.value).collect { (progress, result) ->
                    if (result != null) {
                        cachedPeople = result.people
                        cachedLabel = result.videoLabel
                        _uiState.value = UiState.Result(result)
                    } else {
                        _uiState.value = UiState.Processing(progress, label)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Something went wrong while analyzing the video")
            }
        }
    }

    /** Switch to a new collage template — regenerates bitmap on-device, no pipeline re-run */
    fun changeTemplate(template: CollageTemplate) {
        val people = cachedPeople ?: return
        if (_selectedTemplate.value == template) return
        _selectedTemplate.value = template

        viewModelScope.launch {
            _isRegenerating.value = true
            try {
                val newCollage = withContext(Dispatchers.Default) {
                    pipeline.rebuildCollage(people, cachedLabel, template)
                }
                val current = _uiState.value
                if (current is UiState.Result) {
                    // Recycle old collage bitmap to free GPU/native memory
                    val old = current.result.collageBitmap
                    _uiState.value = UiState.Result(current.result.copy(collageBitmap = newCollage))
                    if (!old.isRecycled) old.recycle()
                }
            } finally {
                _isRegenerating.value = false
            }
        }
    }

    fun saveCollage(bitmap: Bitmap) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                val uri = SaveShareUtil.saveToGallery(getApplication(), bitmap)
                _saveState.value = if (uri != null) SaveState.Saved(uri)
                    else SaveState.Failed("Could not save to gallery. Check device storage.")
            } catch (e: Exception) {
                _saveState.value = SaveState.Failed(e.localizedMessage ?: "Failed to save collage")
            }
        }
    }

    fun resetSaveState() { _saveState.value = SaveState.Idle }

    fun reset() {
        cachedPeople = null
        _uiState.value = UiState.Idle
        _saveState.value = SaveState.Idle
        _selectedTemplate.value = CollageTemplate.Editorial
    }

    override fun onCleared() {
        super.onCleared()
        pipeline.close()
    }
}
