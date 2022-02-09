/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.api.common.operators;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.operators.util.FieldSet;

import java.util.HashSet;
import java.util.Set;

/**
 * A class encapsulating compiler hints describing the behavior of the user function. If set, the
 * optimizer will use them to estimate the sizes of the intermediate results. Note that these values
 * are optional hints, the optimizer will always generate a valid plan without them as well. The
 * hints may help, however, to improve the plan choice.
 * 封装编译器提示的类，描述了用户函数的行为。 如果设置，优化器将使用它们来估计中间结果的大小。
 * 请注意，这些值是可选提示，优化器也将始终生成一个有效的计划，而无需它们。 然而，这些提示可能有助于改进计划选择。
 */
@Internal
public class CompilerHints {

    private long outputSize = -1;

    private long outputCardinality = -1;

    private float avgOutputRecordSize = -1.0f;

    private float filterFactor = -1.0f;

    private Set<FieldSet> uniqueFields;

    // --------------------------------------------------------------------------------------------
    //  Basic Record Statistics
    // --------------------------------------------------------------------------------------------

    public long getOutputSize() {
        return outputSize;
    }

    public void setOutputSize(long outputSize) {
        if (outputSize < 0) {
            throw new IllegalArgumentException("The output size cannot be smaller than zero.");
        }

        this.outputSize = outputSize;
    }

    public long getOutputCardinality() {
        return this.outputCardinality;
    }

    public void setOutputCardinality(long outputCardinality) {
        if (outputCardinality < 0) {
            throw new IllegalArgumentException(
                    "The output cardinality cannot be smaller than zero.");
        }

        this.outputCardinality = outputCardinality;
    }

    public float getAvgOutputRecordSize() {
        return this.avgOutputRecordSize;
    }

    public void setAvgOutputRecordSize(float avgOutputRecordSize) {
        if (avgOutputRecordSize <= 0) {
            throw new IllegalArgumentException("The size of produced records must be positive.");
        }

        this.avgOutputRecordSize = avgOutputRecordSize;
    }

    public float getFilterFactor() {
        return filterFactor;
    }

    public void setFilterFactor(float filterFactor) {
        if (filterFactor < 0) {
            throw new IllegalArgumentException("The filter factor cannot be smaller than zero.");
        }

        this.filterFactor = filterFactor;
    }

    // --------------------------------------------------------------------------------------------
    //  Uniqueness
    // --------------------------------------------------------------------------------------------

    /**
     * Gets the FieldSets that are unique
     * 获取唯一的 FieldSet
     *
     * @return List of FieldSet that are unique
     */
    public Set<FieldSet> getUniqueFields() {
        return this.uniqueFields;
    }

    /**
     * Adds a FieldSet to be unique
     * 添加一个 FieldSet 是唯一的
     *
     * @param uniqueFieldSet The unique FieldSet
     */
    public void addUniqueField(FieldSet uniqueFieldSet) {
        if (this.uniqueFields == null) {
            this.uniqueFields = new HashSet<FieldSet>();
        }
        this.uniqueFields.add(uniqueFieldSet);
    }

    /**
     * Adds a field as having only unique values.
     * 添加一个只有唯一值的字段。
     *
     * @param field The field with unique values.
     */
    public void addUniqueField(int field) {
        if (this.uniqueFields == null) {
            this.uniqueFields = new HashSet<FieldSet>();
        }
        this.uniqueFields.add(new FieldSet(field));
    }

    /**
     * Adds multiple FieldSets to be unique
     * 添加多个 FieldSet 是唯一的
     *
     * @param uniqueFieldSets A set of unique FieldSet
     */
    public void addUniqueFields(Set<FieldSet> uniqueFieldSets) {
        if (this.uniqueFields == null) {
            this.uniqueFields = new HashSet<FieldSet>();
        }
        this.uniqueFields.addAll(uniqueFieldSets);
    }

    public void clearUniqueFields() {
        this.uniqueFields = null;
    }

    // --------------------------------------------------------------------------------------------
    //  Miscellaneous
    // --------------------------------------------------------------------------------------------

    protected void copyFrom(CompilerHints source) {
        this.outputSize = source.outputSize;
        this.outputCardinality = source.outputCardinality;
        this.avgOutputRecordSize = source.avgOutputRecordSize;
        this.filterFactor = source.filterFactor;

        if (source.uniqueFields != null && source.uniqueFields.size() > 0) {
            if (this.uniqueFields == null) {
                this.uniqueFields = new HashSet<FieldSet>();
            } else {
                this.uniqueFields.clear();
            }

            this.uniqueFields.addAll(source.uniqueFields);
        }
    }
}
