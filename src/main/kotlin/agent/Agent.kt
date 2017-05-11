package agent

import org.objectweb.asm.*
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain

class Agent : ClassFileTransformer {

    override fun transform(loader: ClassLoader?, className: String, classBeingRedefined: Class<*>?,
                           protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray): ByteArray {
        println("Loading $className")
        val writer = ClassWriter(0)
        val adapter = FindTestAdapter(writer)
        val reader = ClassReader(classfileBuffer)
        reader.accept(adapter, 0)
        return writer.toByteArray()
    }

    class FindTestAdapter(cv: ClassVisitor) : ClassVisitor(Opcodes.ASM5, cv) {
        override fun visitMethod(access: Int, name: String?, desc: String?,
                                 signature: String?, exceptions: Array<out String>?): MethodVisitor {
            println("Visiting method $name with sig $signature")
            return TestMethodInjector(super.visitMethod(access, name, desc, signature, exceptions))
        }
    }

    class TestMethodInjector(mv: MethodVisitor) : MethodVisitor(Opcodes.ASM5, mv) {

        private var changed = false

        override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, desc: String?, itf: Boolean) {
            if (opcode == Opcodes.INVOKESTATIC && owner == "example/CoroutineExampleKt"
                    && name == "test" && desc == "(Lkotlin/coroutines/experimental/Continuation;)Ljava/lang/Object;") {
                println("Found INVOKESTATIC with owner $owner name '$name' and desc '$desc'")
                changed = true
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
                mv.visitLdcInsn("Test detected")
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false)
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf)
        }

        override fun visitMaxs(maxStack: Int, maxLocals: Int) {
            println("Visit maxs, changed=$changed")
            if (changed) super.visitMaxs(maxStack + 2, maxLocals)
            else super.visitMaxs(maxStack, maxLocals)
        }
    }

    companion object {
        @JvmStatic
        fun premain(agentArgs: String?, inst: Instrumentation) {
            println("Agent started.")
            inst.addTransformer(Agent())
        }
    }
}
