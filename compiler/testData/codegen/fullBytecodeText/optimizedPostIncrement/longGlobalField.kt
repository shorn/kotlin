fun check(b: Long) { }
@JvmField
var a: Long = 0

fun box() {
    check(a++)
    a++
}

/*
LongGlobalFieldKt.box()V
GETSTATIC LongGlobalFieldKt.a : J
DUP2
LCONST_1
LADD
PUTSTATIC LongGlobalFieldKt.a : J
INVOKESTATIC LongGlobalFieldKt.check (J)V
GETSTATIC LongGlobalFieldKt.a : J
LCONST_1
LADD
PUTSTATIC LongGlobalFieldKt.a : J
RETURN
MAXSTACK = 6
MAXLOCALS = 0
*/
