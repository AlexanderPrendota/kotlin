// COMMON_COROUTINES_TEST
// WITH_RUNTIME
// WITH_COROUTINES
// IGNORE_BACKEND: JVM_IR

import helpers.*
import COROUTINES_PACKAGE.*

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

suspend fun foo(until: Int): String {
    suspend fun bar(x: Int): String =
        if (x == until) "OK" else bar(x + 1)
    return bar(0)
}

fun box(): String {
    var res = "FAIL"
    builder {
        res = foo(10)
    }
    return res
}
