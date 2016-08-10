fun check(b: Byte) { }

fun a0(): ByteArray = null!!
fun a1(): Array<ByteArray> = null!!
fun a2(): Array<Byte> = null!!

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
ByteArrayKt.box()V
INVOKESTATIC ByteArrayKt.a0 ()[B
ASTORE 0
ALOAD 0
ICONST_0
DUP2
BALOAD
DUP_X2
ICONST_1
IADD
I2B
BASTORE
INVOKESTATIC ByteArrayKt.check (B)V
ALOAD 0
ICONST_0
DUP2
BALOAD
DUP_X2
ICONST_1
IADD
I2B
BASTORE
POP
INVOKESTATIC ByteArrayKt.a1 ()[[B
ASTORE 1
ALOAD 1
ICONST_0
AALOAD
ICONST_0
DUP2
BALOAD
DUP_X2
ICONST_1
IADD
I2B
BASTORE
INVOKESTATIC ByteArrayKt.check (B)V
ALOAD 1
ICONST_0
AALOAD
ICONST_0
DUP2
BALOAD
DUP_X2
ICONST_1
IADD
I2B
BASTORE
POP
INVOKESTATIC ByteArrayKt.a2 ()[Ljava/lang/Byte;
ASTORE 2
ALOAD 2
ICONST_0
DUP2
AALOAD
INVOKEVIRTUAL java/lang/Number.byteValue ()B
DUP_X2
ICONST_1
IADD
I2B
INVOKESTATIC java/lang/Byte.valueOf (B)Ljava/lang/Byte;
AASTORE
INVOKESTATIC ByteArrayKt.check (B)V
ALOAD 2
ICONST_0
DUP2
AALOAD
INVOKEVIRTUAL java/lang/Number.byteValue ()B
DUP_X2
ICONST_1
IADD
I2B
INVOKESTATIC java/lang/Byte.valueOf (B)Ljava/lang/Byte;
AASTORE
POP
RETURN
MAXSTACK = 5
MAXLOCALS = 3
*/
