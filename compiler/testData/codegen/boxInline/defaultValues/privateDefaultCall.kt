// FILE: 1.kt

package test

class Foo {
    private fun foo(s: String = "OK") = s

    internal inline fun inlineFun(): String {
        return foo()
    }
}

// FILE: 2.kt

import test.*

fun box(): String {
    return Foo().inlineFun()
}