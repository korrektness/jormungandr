package org.jetbrains.kotlin.formver.viper

/** The declaration-worker budget shared by execution and retained verifier capacity. */
object VerificationConcurrency {
    private const val MAX_JOBS = 16

    val jobs: Int by lazy { parseJobs(System.getProperty("formver.verificationJobs")) }

    fun parseJobs(value: String?): Int {
        if (value == null) return 4
        val jobs = value.toIntOrNull()
        require(jobs != null && jobs in 1..MAX_JOBS) { "Invalid formver.verificationJobs: '$value'" }
        return jobs
    }
}
