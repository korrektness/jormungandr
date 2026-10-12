/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin

annotation class NeverConvert
annotation class NeverVerify
annotation class DumpExpEmbeddings

/**
 * Marks values of the annotated type as uniquely referenced, as in `x: @Unique Node` or `fun f(): @Unique Node`.
 *
 * On a member function, marks its dispatch receiver `this` as uniquely referenced, as in `@Unique fun reset()`.
 */
@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
annotation class Unique

/**
 * Marks values of the annotated type as borrowed for the duration of a call, as in `x: @Borrowed @Unique Node`.
 *
 * On a member function, marks its dispatch receiver `this` as borrowed, as in `@Borrowed @Unique fun size()`.
 */
@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
annotation class Borrowed

/** Marks a pure function or observer; see "Pure functions and specifications" and "Observers" in SPECIFICATIONS.md. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class Pure

/** Marks a callable ghost observer; see "Callable specifications" in SPECIFICATIONS.md. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class GhostObserver

/** Marks an interface law; see "Interface laws" in SPECIFICATIONS.md. */
@Target(AnnotationTarget.FUNCTION)
annotation class Law

/**
 * Disables automatic permission management for the annotated element.
 *
 * On a class, the automatic folding, unfolding, and havoc of that class's uniqueness predicate
 * are turned off, leaving its permissions to be managed explicitly.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
annotation class Manual

/** Gives the member [name] the specification in this function; see "Specifications beside a member" in SPECIFICATIONS.md. */
@Target(AnnotationTarget.FUNCTION)
annotation class SpecOf(val name: String)
