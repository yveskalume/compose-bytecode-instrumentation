import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.composechef.bytecodeinstrumentation"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.composechef.bytecodeinstrumentation"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":instrumentation-runtime"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.instrumentation.transformClassesWith(
            ComposeInjectionVisitorFactory::class.java,
            InstrumentationScope.PROJECT
        ) {}
        variant.instrumentation.setAsmFramesComputationMode(
            FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
        )
    }
}

abstract class ComposeInjectionVisitorFactory :
    AsmClassVisitorFactory<InstrumentationParameters.None> {

    override fun isInstrumentable(classData: ClassData): Boolean =
        classData.className.startsWith("com.composechef.bytecodeinstrumentation.")

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor = ComposeInjectionClassVisitor(nextClassVisitor)
}

private class ComposeInjectionClassVisitor(
    nextClassVisitor: ClassVisitor
) : ClassVisitor(Opcodes.ASM9, nextClassVisitor) {

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val next = super.visitMethod(access, name, descriptor, signature, exceptions)
        val argumentTypes = Type.getArgumentTypes(descriptor)
        val composerArgument = argumentTypes.indexOfFirst {
            it.descriptor == "Landroidx/compose/runtime/Composer;"
        }
        if (composerArgument == -1) return next

        val firstArgumentLocal = if (access and Opcodes.ACC_STATIC != 0) 0 else 1
        // Counting slots keeps this correct for instance methods and if a wide
        // argument (Long or Double) appears before Composer.
        val composerLocal = firstArgumentLocal + argumentTypes
            .take(composerArgument)
            .sumOf(Type::getSize)

        return ComposeInjectionMethodVisitor(next, composerLocal)
    }
}

private class ComposeInjectionMethodVisitor(
    nextMethodVisitor: MethodVisitor,
    private val composerLocal: Int
) : MethodVisitor(Opcodes.ASM9, nextMethodVisitor) {
    private var shouldInject = false
    private var injected = false
    private var foundShouldExecute = false

    override fun visitAnnotation(
        descriptor: String,
        visible: Boolean
    ): AnnotationVisitor? {
        if (descriptor ==
            "Lcom/composechef/instrumentation/runtime/InjectComposable;"
        ) {
            shouldInject = true
        }
        return super.visitAnnotation(descriptor, visible)
    }

    override fun visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean
    ) {
        if (owner == "androidx/compose/runtime/Composer" &&
            name == "shouldExecute" &&
            descriptor == "(ZI)Z"
        ) {
            foundShouldExecute = true
        }

        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }

    override fun visitJumpInsn(opcode: Int, label: Label) {
        super.visitJumpInsn(opcode, label)

        // The Compose compiler emits `shouldExecute(...); IFEQ skipLabel`.
        // Emitting after IFEQ puts this call on its fall-through (execute) path,
        // inside the restart group and with an empty operand stack.
        if (shouldInject &&
            !injected &&
            foundShouldExecute &&
            opcode == Opcodes.IFEQ
        ) {
            super.visitVarInsn(Opcodes.ALOAD, composerLocal)
            super.visitInsn(Opcodes.ICONST_0)
            super.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "com/composechef/instrumentation/runtime/InjectedUiKt",
                "InjectedBadge",
                "(Landroidx/compose/runtime/Composer;I)V",
                false
            )
            injected = true
        }
        foundShouldExecute = false
    }
}
