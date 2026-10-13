/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.gradle


open class FormVerExtension {
    internal var myCounterexamples: Boolean? = null

    open fun counterexamples(enabled: Boolean) {
        myCounterexamples = enabled
    }

    internal var myVerificationCacheDirectory: String? = null

    open fun verificationCacheDirectory(directory: String) {
        require(directory.isNotBlank())
        myVerificationCacheDirectory = directory
    }

    internal var myLogLevel: String? = null
    internal var myErrorStyle: String? = null
    internal var myConversionTargetsSelection: String? = null
    internal var myVerificationTargetsSelection: String? = null
    internal var myDumpUniquenessCFG: Boolean? = null
    internal val myExternalSpecs = mutableListOf<Any>()

    open fun logLevel(logLevel: String) {
        myLogLevel = logLevel
    }

    open fun errorStyle(style: String) {
        myErrorStyle = style
    }

    open fun conversionTargetsSelection(selection: String) {
        myConversionTargetsSelection = selection
    }

    open fun verificationTargetsSelection(selection: String) {
        myVerificationTargetsSelection = selection
    }

    open fun dumpUniquenessCFG(enabled: Boolean) {
        myDumpUniquenessCFG = enabled
    }

    /**
     * Adds Kotlin source files that specify code outside the module. The compiler analyses them but does not compile
     * them. Paths resolve as in `Project.files`.
     */
    open fun externalSpec(vararg paths: Any) {
        myExternalSpecs.addAll(paths)
    }
}
