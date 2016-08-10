// WITH_RUNTIME
fun check(b: Byte, e: Byte) {
    if (b != e) throw IllegalArgumentException(b.toString())
}

var globProp: Byte = 0

@JvmField
var globField: Byte = 0

open class A {
    open var prop: Byte = 0
    private var field: Byte = 0

    fun test() {
        check(prop++, 0)
        prop++
        check(prop, 2)

        check(field++, 0)
        field++
        check(field, 2)
    }
}

fun box(): String {
    var a: Byte = 0
    check(a++, 0)
    a++
    check(a, 2)

    check(globProp++, 0)
    globProp++
    check(globProp, 2)

    A().test()

    return "OK"
}
