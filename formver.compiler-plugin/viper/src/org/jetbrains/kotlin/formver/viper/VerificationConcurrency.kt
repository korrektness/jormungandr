package org.jetbrains.kotlin.formver.viper

/** The declaration-worker budget shared by execution and retained verifier capacity. */
object VerificationConcurrency {
    private const val DEFAULT_JOBS = 4
    private const val MAX_JOBS = 16

    val jobs: Int by lazy { parseJobs(System.getProperty("formver.verificationJobs")) }

    fun parseJobs(value: String?): Int {
        val setting = value ?: return DEFAULT_JOBS
        val jobs = setting.toIntOrNull()
        require(jobs != null && jobs in 1..MAX_JOBS) { "Invalid formver.verificationJobs: '$setting'" }
        return jobs
    }
}
