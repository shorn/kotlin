fun check(b: Long) { }

fun box() {
    var a: Long = 0
    check(a++)
    a++
}

/*
LongLocalVarKt.box()V
LCONST_0
LSTORE 0
LLOAD 0
DUP2
LCONST_1
LADD
LSTORE 0
INVOKESTATIC LongLocalVarKt.check (J)V
LLOAD 0
LCONST_1
LADD
LSTORE 0
RETURN
MAXSTACK = 6
MAXLOCALS = 2
*/
