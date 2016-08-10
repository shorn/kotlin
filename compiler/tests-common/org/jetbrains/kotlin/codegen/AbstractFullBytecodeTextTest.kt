/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.codegen

import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.test.ConfigurationKind
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import org.jetbrains.org.objectweb.asm.util.Textifier
import org.jetbrains.org.objectweb.asm.util.TraceMethodVisitor
import java.io.File

abstract class AbstractFullBytecodeTextTest : CodegenTestCase() {
    override fun doMultiFileTest(wholeFile: File, files: MutableList<TestFile>, javaFilesDir: File?) {
        createEnvironmentWithMockJdkAndIdeaAnnotations(ConfigurationKind.ALL, files, javaFilesDir)
        loadMultiFiles(files)
        val classFileFactory = generateClassesInFile()

        val methodDescToMethodNode = classFileFactory.asList().flatMap { output ->
            val classNode = ClassNode()
            val byteArray = output.asByteArray()
            if (byteArray.isEmpty() || !output.relativePath.endsWith(".class")) return@flatMap emptyList<Nothing>()
            ClassReader(byteArray).accept(classNode, ClassReader.SKIP_FRAMES or ClassReader.SKIP_DEBUG)

            classNode.methods.map {
                Pair(classNode.name + "." + it.name + it.desc, it)
            }
        }.toMap()

        val expectations = readExpectedOccurrences()
        assertTrue(expectations.isNotEmpty())

        for ((methodDesc, expectedContent) in expectations) {
            val node = methodDescToMethodNode[methodDesc] ?: error("Method for $methodDesc was not found")
            val textifier = Textifier()
            node.accept(TraceMethodVisitor(textifier))

            val lines = textifier.text.map(Any::toString)
            val isThereLabels = lines.any { it.trim().matches("L\\d+".toRegex()) }
            val skipAtBegin = if (isThereLabels) 3 else 4

            val actual = lines.map { it.substring(skipAtBegin) }.filter(String::isNotEmpty).joinToString("").trimEnd()
            assertEquals(expectedContent, actual)
        }
    }

    private fun readExpectedOccurrences(): List<ExpectationInfo> =
        myFiles.psiFiles.last().allChildren.toList().takeLastWhile {
            it is PsiWhiteSpace || it.node.elementType == KtTokens.BLOCK_COMMENT
        }.filterNot {
            it is PsiWhiteSpace
        }.map {
            val expectationText = it.text.substring(2, it.textLength - 2).trim()
            val lines = expectationText.lines()
            ExpectationInfo(lines[0].trim(), lines.subList(1, lines.size).joinToString("\n").trim())
        }

    private data class ExpectationInfo(val methodDesc: String, val contents: String)
}
