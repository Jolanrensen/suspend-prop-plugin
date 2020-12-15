/*
 * Copyright (C) 2020 Brian Norman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bnorm.template

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildProperty
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class TemplateIrGenerationExtension(
    private val messageCollector: MessageCollector,
    private val string: String,
    private val file: String
) : IrGenerationExtension {

    class RecursiveVisitor : IrElementVisitor<Unit, String> {

        override fun visitProperty(declaration: IrProperty, data: String) {
//      declaration.acceptChildren(this, data)
            println("prop found: ${declaration.name} with annotation SuspendProp? ${declaration.hasAnnotation(FqName("SuspendProp"))}")
            visitElement(declaration, data)

        }


        override fun visitElement(element: IrElement, data: String) {
            println("$data${element.render()} {")
            element.acceptChildren(this, "  $data")
            println("$data}")
        }
    }


    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        println(moduleFragment.dump())
//        moduleFragment.accept(
//            visitor = RecursiveVisitor(),
//            data = "    "
//        )

        val nullableAnyType = pluginContext.irBuiltIns.anyNType
        val unitType = pluginContext.irBuiltIns.unitType

        val printlnFunRef = pluginContext.referenceFunctions(FqName("kotlin.io.println"))
            .single {
                it.owner.valueParameters.run {
                    size == 1 && first().type == nullableAnyType
                }
            }

        val funMain = pluginContext.irFactory.buildFun {
            name = Name.identifier("main")
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.FINAL
            returnType = unitType
        }.apply {
            body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
                +irCall(printlnFunRef).apply {
                    putValueArgument(0, irString("Hello World!"))
                }
            }
        }

//        println(funMain.dump())
//        println(funMain.render())

        val intType = pluginContext.irBuiltIns.intType

        val testProperty = pluginContext.irFactory.buildProperty {
            name = Name.identifier("test")
            isVar = true
        }.also { prop ->
            prop.parent = funMain

            prop.addGetter {
                returnType = intType
            }.also { getter ->
                getter.body = DeclarationIrBuilder(pluginContext, getter.symbol).irBlockBody {
                    +irReturn(irInt(4))
                }
            }

            prop.setter = pluginContext.irFactory.buildFun {
                name = Name.special("<set-${prop.name}>")
                returnType = unitType
            }.also { setter ->
                setter.correspondingPropertySymbol = prop.symbol
                setter.parent = prop.parent

                setter.addValueParameter {
                    name = Name.identifier("value")
                    index = 0
                    type = intType
                }

                setter.body = DeclarationIrBuilder(pluginContext, setter.symbol).irBlockBody {
                    +irCall(printlnFunRef).apply {
                        putValueArgument(
                            0,
                            irGet(setter.valueParameters[0]),
                        )
                    }
                }
            }

        }

        println(testProperty.dump())


//    messageCollector.report(CompilerMessageSeverity.INFO, "Argument 'string' = $string")
//    messageCollector.report(CompilerMessageSeverity.INFO, "Argument 'file' = $file")

    }
}
