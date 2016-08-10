fun check(b: Byte) { }

open class A {
    open var prop: Byte = 0
    private var field: Byte = 0

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
INVOKEVIRTUAL A.getProp ()B
DUP_X1
ICONST_1
IADD
I2B
INVOKEVIRTUAL A.setProp (B)V
INVOKESTATIC BytePropInClassKt.check (B)V
ALOAD 0
DUP
INVOKEVIRTUAL A.getProp ()B
ICONST_1
IADD
I2B
INVOKEVIRTUAL A.setProp (B)V
ALOAD 0
DUP
GETFIELD A.field : B
DUP_X1
ICONST_1
IADD
I2B
PUTFIELD A.field : B
INVOKESTATIC BytePropInClassKt.check (B)V
ALOAD 0
DUP
GETFIELD A.field : B
ICONST_1
IADD
I2B
PUTFIELD A.field : B
RETURN
MAXSTACK = 4
MAXLOCALS = 1
*/
