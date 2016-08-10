fun check(b: Byte) { }
@JvmField
var a: Byte = 0

fun box() {
    check(a++)
    a++
}

/*
ByteGlobalFieldKt.box()V
GETSTATIC ByteGlobalFieldKt.a : B
DUP
ICONST_1
IADD
I2B
PUTSTATIC ByteGlobalFieldKt.a : B
INVOKESTATIC ByteGlobalFieldKt.check (B)V
GETSTATIC ByteGlobalFieldKt.a : B
DUP
ICONST_1
IADD
I2B
PUTSTATIC ByteGlobalFieldKt.a : B
POP
RETURN
MAXSTACK = 3
MAXLOCALS = 0
*/
