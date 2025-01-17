/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.config.conditional;

import java.util.List;
import java.util.regex.Pattern;

import com.oracle.svm.configure.config.ConfigurationPredefinedClass;
import com.oracle.svm.configure.config.ConfigurationType;
import com.oracle.svm.configure.config.PredefinedClassesConfiguration;
import com.oracle.svm.configure.config.ProxyConfiguration;
import com.oracle.svm.configure.config.ResourceConfiguration;
import com.oracle.svm.configure.config.SerializationConfiguration;
import com.oracle.svm.configure.config.SerializationConfigurationLambdaCapturingType;
import com.oracle.svm.configure.config.SerializationConfigurationType;
import com.oracle.svm.configure.config.TypeConfiguration;
import com.oracle.svm.configure.filters.ComplexFilter;
import com.oracle.svm.core.configure.ConditionalElement;

public class ConditionalConfigurationPredicate implements TypeConfiguration.Predicate, ProxyConfiguration.Predicate,
                ResourceConfiguration.Predicate, SerializationConfiguration.Predicate, PredefinedClassesConfiguration.Predicate {

    private final ComplexFilter filter;

    public ConditionalConfigurationPredicate(ComplexFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean testIncludedType(ConditionalElement<String> conditionalElement, ConfigurationType type) {
        return !filter.includes(conditionalElement.condition().getTypeName()) || !filter.includes(type.getQualifiedJavaName());
    }

    @Override
    public boolean testProxyInterfaceList(ConditionalElement<List<String>> conditionalElement) {
        return !filter.includes(conditionalElement.condition().getTypeName());
    }

    @Override
    public boolean testIncludedResource(ConditionalElement<String> condition, Pattern pattern) {
        return !filter.includes(condition.condition().getTypeName());
    }

    @Override
    public boolean testIncludedBundle(ConditionalElement<String> condition, ResourceConfiguration.BundleConfiguration bundleConfiguration) {
        return !filter.includes(condition.condition().getTypeName());
    }

    @Override
    public boolean testSerializationType(SerializationConfigurationType type) {
        return !(filter.includes(type.getCondition().getTypeName()) && filter.includes(type.getQualifiedJavaName()));
    }

    @Override
    public boolean testLambdaSerializationType(SerializationConfigurationLambdaCapturingType type) {
        return !(filter.includes(type.getCondition().getTypeName()) && filter.includes(type.getQualifiedJavaName()));
    }

    @Override
    public boolean testPredefinedClass(ConfigurationPredefinedClass clazz) {
        return clazz.getNameInfo() != null && !filter.includes(clazz.getNameInfo());
    }
}
