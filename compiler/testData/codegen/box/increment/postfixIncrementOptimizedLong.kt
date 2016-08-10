// WITH_RUNTIME
fun check(b: Long, e: Long) {
    if (b != e) throw IllegalArgumentException(b.toString())
}

var globProp: Long = 0

@JvmField
var globField: Long = 0

open class A {
    open var prop: Long = 0
    private var field: Long = 0

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
    var a: Long = 0
    check(a++, 0)
    a++
    check(a, 2)

    check(globProp++, 0)
    globProp++
    check(globProp, 2)

    A().test()

    return "OK"
}
