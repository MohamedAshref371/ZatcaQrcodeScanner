package com.poetscorner.invoiceqr.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun requestAppReview(activity: Activity, allowed: () -> Boolean) {
    val manager = ReviewManagerFactory.create(activity)
    val info = manager.requestReviewFlow().awaitReviewTask()
    if (allowed() && !activity.isFinishing && !activity.isDestroyed) {
        manager.launchReviewFlow(activity, info).awaitReviewTask()
    }
}

private suspend fun <T> Task<T>.awaitReviewTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Review task cancelled"))
        }
    }
}

internal fun openAppStore(context: Context): Boolean {
    val id = context.packageName
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id"))
            .setPackage("com.android.vending"))
        return true
    } catch (_: ActivityNotFoundException) {
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$id")))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
