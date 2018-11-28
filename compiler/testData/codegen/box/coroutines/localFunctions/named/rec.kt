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
    val o = "O"
    val k = "K"
    val dot = "."
    suspend fun bar(x: Int): String =
        if (x == until) dot else if (x > until) k + bar(x - 1) else o + bar(x * 2)
    return bar(1)
}

fun box(): String {
    var res = "FAIL"
    builder {
        res = foo(10)
    }
    if (res != "OOOOKKKKKK.") return res
    return "OK"
}
