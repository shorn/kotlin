// WITH_REFLECT

class Foo {
    fun bar(x: Int): Int = x + 1
}

fun box(): String {
    val bar = Foo::class.members.single { it.name == "bar" }

    if (bar is Function1<*, *>) return "Fail 1"
    if (bar is Function2<*, *, *>) return "Fail 2"
    if (bar is Function3<*, *, *, *>) return "Fail 3"

    try {
        bar as Function2<*, *, *>
        return "Fail 4"
    } catch (e: ClassCastException) {
        // OK
    }

    return "OK"
}
