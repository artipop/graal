/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.replacements.amd64;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;
import static jdk.graal.compiler.nodes.calc.BinaryArithmeticNode.getArithmeticOpTable;

import java.math.BigDecimal;

import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.FloatConvertOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.UnaryOp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.UnaryArithmeticNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;

/**
 * This node has the semantics of the AMD64 floating point conversions. It is used in the lowering
 * of the {@link FloatConvertNode} which, on AMD64 needs a {@link AMD64FloatConvertNode} plus some
 * fixup code that handles the corner cases that differ between AMD64 and Java.
 *
 * Since this node evaluates to a special value if the conversion is inexact, its stamp must be
 * modified to avoid optimizing away {@link AMD64ConvertSnippets}.
 */
@NodeInfo(cycles = CYCLES_8, size = SIZE_1)
public final class AMD64FloatConvertNode extends UnaryArithmeticNode<FloatConvertOp> implements ArithmeticLIRLowerable {
    public static final NodeClass<AMD64FloatConvertNode> TYPE = NodeClass.create(AMD64FloatConvertNode.class);

    protected final FloatConvert op;

    public AMD64FloatConvertNode(FloatConvert op, ValueNode value) {
        super(TYPE, getArithmeticOpTable(value).getFloatConvert(op), value);
        this.op = op;
        this.stamp = this.stamp.meet(createInexactCaseStamp());
    }

    @Override
    protected UnaryOp<FloatConvertOp> getOp(ArithmeticOpTable table) {
        return table.getFloatConvert(op);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isJavaConstant()) {
            // This doesn't match the semantics of CVTTSS2SI but since this is only used by
            // AMD64ConvertSnippets this will fold to still produce the right result.
            UnaryOp<FloatConvertOp> floatConvertOp = getOp(getArithmeticOpTable(value));
            return ConstantNode.forPrimitive(floatConvertOp.foldStamp(forValue.stamp(NodeView.DEFAULT)), floatConvertOp.foldConstant(forValue.asConstant()));
        }
        // nothing to do
        return this;
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        Stamp resultStamp = super.foldStamp(newStamp);
        /**
         * The semantics of the x64 CVTTSD2SI
         * https://frama-c.com/2013/10/09/Overflow-float-integer.html instruction allow returning
         * MIN_VALUE (for convert to int Integer.MIN_VALUE, for covert to long Long.MIN_VALUE) in
         * special cases. Those cases are
         *
         * <ul>
         * <li>Input is NAN</li>
         * <li>Input is infinity</li> *
         * <li>Integral part of the floating point number is smaller MIN_VALUE or larger MAX_VALUE
         * in which case an overflow happens during conversion</li>
         * </ul>
         *
         * We must ensure during stamp folding the special cases are considered and accounted for.
         */
        if (resultStamp instanceof IntegerStamp && newStamp instanceof FloatStamp inputStamp) {
            final boolean canBeNan = inputStamp.canBeNaN();
            final boolean canBeInifity = Double.isInfinite(inputStamp.lowerBound()) || Double.isInfinite(inputStamp.upperBound());
            final boolean conversionCanOverflow = integralPartLargerMaxValue(Long.MAX_VALUE, inputStamp.upperBound()) || integralPartSmallerMinValue(Long.MIN_VALUE, inputStamp.lowerBound());
            final boolean canGenerateSpecialValue = canBeNan || canBeInifity || conversionCanOverflow;
            if (canGenerateSpecialValue) {
                return resultStamp.meet(createInexactCaseStamp());
            } else {
                return resultStamp;
            }
        } else {
            return resultStamp.unrestricted();
        }
    }

    private static boolean integralPartLargerMaxValue(long maxValue, double d) {
        if (Double.isInfinite(d) || Double.isNaN(d)) {
            return true;
        }
        return new BigDecimal(d).compareTo(new BigDecimal(maxValue)) > 0;
    }

    private static boolean integralPartSmallerMinValue(long minValue, double d) {
        if (Double.isInfinite(d) || Double.isNaN(d)) {
            return true;
        }
        return new BigDecimal(d).compareTo(new BigDecimal(minValue)) < 0;
    }

    private Stamp createInexactCaseStamp() {
        IntegerStamp intStamp = (IntegerStamp) this.stamp;
        long inexactValue = intStamp.getBits() <= 32 ? 0x8000_0000L : 0x8000_0000_0000_0000L;
        return StampFactory.forConstant(JavaConstant.forPrimitiveInt(intStamp.getBits(), inexactValue));
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitFloatConvert(op, nodeValueMap.operand(getValue())));
    }
}
