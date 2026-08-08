package com.composechef.instrumentation.runtime

/** Marks a composable function whose compiled body should receive InjectedBadge. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class InjectComposable
