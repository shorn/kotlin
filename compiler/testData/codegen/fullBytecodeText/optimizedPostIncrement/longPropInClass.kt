fun check(b: Long) { }

open class A {
    open var prop: Long = 0
    private var field: Long = 0

    fun test() {
        check(prop++)
        prop++

        check(field++)
        field++
    }
}

/*
A.test()V
ALOAD 0
DUP
INVOKEVIRTUAL A.getProp ()J
DUP2_X1
LCONST_1
LADD
INVOKEVIRTUAL A.setProp (J)V
INVOKESTATIC LongPropInClassKt.check (J)V
ALOAD 0
DUP
INVOKEVIRTUAL A.getProp ()J
DUP2_X1
LCONST_1
LADD
INVOKEVIRTUAL A.setProp (J)V
POP2
ALOAD 0
DUP
GETFIELD A.field : J
DUP2_X1
LCONST_1
LADD
PUTFIELD A.field : J
INVOKESTATIC LongPropInClassKt.check (J)V
ALOAD 0
DUP
GETFIELD A.field : J
DUP2_X1
LCONST_1
LADD
PUTFIELD A.field : J
POP2
RETURN
MAXSTACK = 7
MAXLOCALS = 1
*/
