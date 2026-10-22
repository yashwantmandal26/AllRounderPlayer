package com.example.ymediaplayer.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.example.ymediaplayer.data.FileOperationResult
import kotlinx.coroutines.launch

/** Android 10 grants access; Android 11+ trash consent performs the operation itself. */
@Composable
internal fun rememberFileOperationHandler(
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
): (FileOperationResult) -> Unit {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<FileOperationResult.NeedsConfirmation?>(null) }
    val success by rememberUpdatedState(onSuccess)
    val failure by rememberUpdatedState(onFailure)
    fun handle(result: FileOperationResult) {
        when (result) {
            is FileOperationResult.NeedsConfirmation -> pending = result
            FileOperationResult.Success -> { pending = null; success() }
            is FileOperationResult.Failure -> { pending = null; failure(result.message) }
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val operation = pending
        pending = null
        if (result.resultCode == Activity.RESULT_OK && operation != null) {
            scope.launch { handle(operation.retry?.invoke() ?: FileOperationResult.Success) }
        }
    }
    LaunchedEffect(pending) {
        pending?.let { operation ->
            try { launcher.launch(IntentSenderRequest.Builder(operation.intentSender).build()) }
            catch (e: Exception) { pending = null; failure(e.message ?: "Could not request file access") }
        }
    }
    return ::handle
}
