fun check(b: Byte) { }

fun box() {
    var a: Byte = 0
    check(a++)
    a++
}

/*
ByteLocalVarKt.box()V
ICONST_0
ISTORE 0
ILOAD 0
DUP
ICONST_1
IADD
I2B
ISTORE 0
INVOKESTATIC ByteLocalVarKt.check (B)V
ILOAD 0
ICONST_1
IADD
I2B
ISTORE 0
RETURN
MAXSTACK = 3
MAXLOCALS = 1
*/
