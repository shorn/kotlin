// WITH_RUNTIME
fun check(b: Byte, e: Byte) {
    if (b != e) throw IllegalArgumentException(b.toString())
}

fun box(): String {
    var a = byteArrayOf(0)
    check(a[0]++, 0)
    a[0]++
    check(a[0], 2)

    val b = Array(1) { byteArrayOf(0) }
    check(b[0][0]++, 0)
    b[0][0]++
    check(b[0][0], 2)

    return "OK"
}
