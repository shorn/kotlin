var res = ""
fun log(s: String) {
    res += s
}

class A {
    init {
        log("O")
    }
    operator fun String.set(x: Int, y: Int, z: Int) {
        log("#set#$this/$x/$y/$z")
    }

    operator fun String.get(x: Int, y: Int): Int {
        log("#get#$this/$x/$y")
        return x + y
    }

    fun bar() {
        foo()[1, 2]++
    }
}

fun foo(): String {
    log("#foo")
    return "123"
}

fun box(): String {
    A().bar()

    if (res != "O#foo#get#123/1/2#set#123/1/2/4") return "fail: $res"

    return "OK"
}
