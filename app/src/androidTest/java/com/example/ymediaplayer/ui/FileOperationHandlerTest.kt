package com.example.ymediaplayer.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ymediaplayer.AppRoot
import com.example.ymediaplayer.data.FileOperationResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** A consent stand-in that exercises the real IntentSender/ActivityResult round trip. */
class TestFileConsentActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(intent.getIntExtra("result", RESULT_OK))
        finish()
    }
}

class FileOperationHandlerTest {
    @get:Rule val rule = createComposeRule()

    private fun consent(result: Int = Activity.RESULT_OK): android.content.IntentSender {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent().setClassName(instrumentation.context.packageName, TestFileConsentActivity::class.java.name)
            .putExtra("result", result)
        return PendingIntent.getActivity(instrumentation.targetContext, result, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE).intentSender
    }

    @Test fun grantedAccessRetriesOperationBeforeReportingSuccess() {
        var retries = 0
        var successes = 0
        val sender = consent()
        rule.setContent { AppRoot {
            val handle = rememberFileOperationHandler(onSuccess = { successes++ }, onFailure = { error(it) })
            Button(onClick = { handle(FileOperationResult.NeedsConfirmation(sender, retry = {
                retries++
                FileOperationResult.Success
            })) }) { Text("Rename fixture") }
        } }
        rule.onNodeWithText("Rename fixture").performClick()
        rule.waitUntil(10_000) { successes == 1 }
        rule.runOnIdle { assertEquals(1, retries); assertEquals(1, successes) }
    }

    @Test fun systemTrashConsentCompletesWithoutRepeatingDeletion() {
        var successes = 0
        val sender = consent()
        rule.setContent { AppRoot {
            val handle = rememberFileOperationHandler(onSuccess = { successes++ }, onFailure = { error(it) })
            Button(onClick = { handle(FileOperationResult.NeedsConfirmation(sender)) }) { Text("Trash fixture") }
        } }
        rule.onNodeWithText("Trash fixture").performClick()
        rule.waitUntil(10_000) { successes == 1 }
        rule.runOnIdle { assertEquals(1, successes) }
    }
}
