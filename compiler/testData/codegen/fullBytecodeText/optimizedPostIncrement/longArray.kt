fun check(b: Long) { }

fun a0(): LongArray = null!!
fun a1(): Array<LongArray> = null!!
fun a2(): Array<Long> = null!!

fun box() {
    var a = a0()
    check(a[0]++)
    a[0]++

    val b = a1()
    check(b[0][0]++)
    b[0][0]++

    var r = a2()
    check(r[0]++)
    r[0]++
}

/*
LongArrayKt.box()V
INVOKESTATIC LongArrayKt.a0 ()[J
ASTORE 0
ALOAD 0
ICONST_0
DUP2
LALOAD
DUP2_X2
LCONST_1
LADD
LASTORE
INVOKESTATIC LongArrayKt.check (J)V
ALOAD 0
ICONST_0
DUP2
LALOAD
LCONST_1
LADD
LASTORE
INVOKESTATIC LongArrayKt.a1 ()[[J
ASTORE 1
ALOAD 1
ICONST_0
AALOAD
ICONST_0
DUP2
LALOAD
DUP2_X2
LCONST_1
LADD
LASTORE
INVOKESTATIC LongArrayKt.check (J)V
ALOAD 1
ICONST_0
AALOAD
ICONST_0
DUP2
LALOAD
LCONST_1
LADD
LASTORE
INVOKESTATIC LongArrayKt.a2 ()[Ljava/lang/Long;
ASTORE 2
ALOAD 2
ICONST_0
DUP2
AALOAD
INVOKEVIRTUAL java/lang/Number.longValue ()J
DUP2_X2
LCONST_1
LADD
INVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;
AASTORE
INVOKESTATIC LongArrayKt.check (J)V
ALOAD 2
ICONST_0
DUP2
AALOAD
INVOKEVIRTUAL java/lang/Number.longValue ()J
LCONST_1
LADD
INVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;
AASTORE
RETURN
MAXSTACK = 8
MAXLOCALS = 3
*/
