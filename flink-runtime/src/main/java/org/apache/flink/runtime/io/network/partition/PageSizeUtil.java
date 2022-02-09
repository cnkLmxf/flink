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

package org.apache.flink.runtime.io.network.partition;

import org.apache.flink.util.ExceptionUtils;

import org.apache.flink.shaded.netty4.io.netty.util.internal.PlatformDependent;
import org.apache.flink.shaded.netty4.io.netty.util.internal.shaded.org.jctools.util.UnsafeAccess;

import sun.misc.Unsafe;

import javax.annotation.Nullable;

import java.security.AccessController;
import java.security.PrivilegedAction;

/** Utility for accessing the system page size.
 * 用于访问系统页面大小的实用程序。
 * */
public final class PageSizeUtil {

    /** Value indicating an unknown page size. */
    public static final int PAGE_SIZE_UNKNOWN = -1;

    /** The default page size on most systems. */
    public static final int DEFAULT_PAGE_SIZE = 4 * 1024;

    /**
     * A conservative fallback value (64 KiBytes) that should be a multiple of the page size even in
     * some uncommon cases of servers installations with larger-than-usual page sizes.
     * 一个保守的备用值 (64 KiBytes)，即使在某些不常见的服务器安装的页面大小大于通常的情况下，它也应该是页面大小的倍数。
     */
    public static final int CONSERVATIVE_PAGE_SIZE_MULTIPLE = 64 * 1024;

    /**
     * Tries to get the system page size. If the page size cannot be determined, this returns -1.
     * 尝试获取系统页面大小。 如果无法确定页面大小，则返回 -1。
     *
     * <p>This internally relies on the presence of "unsafe" and the resolution via some Netty
     * utilities.
     * 这在内部依赖于“不安全”的存在以及通过一些 Netty 实用程序的解决方案。
     */
    public static int getSystemPageSize() {
        try {
            return PageSizeUtilInternal.getSystemPageSize();
        } catch (Throwable t) {
            ExceptionUtils.rethrowIfFatalError(t);
            return PAGE_SIZE_UNKNOWN;
        }
    }

    /**
     * Tries to get the system page size. If the page size cannot be determined, this returns the
     * {@link #DEFAULT_PAGE_SIZE}.
     * 尝试获取系统页面大小。 如果无法确定页面大小，则返回 {@link #DEFAULT_PAGE_SIZE}。
     */
    public static int getSystemPageSizeOrDefault() {
        final int pageSize = getSystemPageSize();
        return pageSize == PAGE_SIZE_UNKNOWN ? DEFAULT_PAGE_SIZE : pageSize;
    }

    /**
     * Tries to get the system page size. If the page size cannot be determined, this returns the
     * {@link #CONSERVATIVE_PAGE_SIZE_MULTIPLE}.
     * 尝试获取系统页面大小。 如果无法确定页面大小，则返回 {@link #CONSERVATIVE_PAGE_SIZE_MULTIPLE}。
     */
    public static int getSystemPageSizeOrConservativeMultiple() {
        final int pageSize = getSystemPageSize();
        return pageSize == PAGE_SIZE_UNKNOWN ? CONSERVATIVE_PAGE_SIZE_MULTIPLE : pageSize;
    }

    // ------------------------------------------------------------------------

    /** This class is not meant to be instantiated. */
    private PageSizeUtil() {}

    // ------------------------------------------------------------------------

    /**
     * All unsafe related code must be in a separate class, so that loading the outer class does not
     * implicitly try to resolve the unsafe class.
     * 所有与不安全相关的代码都必须在一个单独的类中，以便加载外部类不会隐式尝试解析不安全类。
     */
    @SuppressWarnings("all")
    private static final class PageSizeUtilInternal {

        static int getSystemPageSize() {
            Unsafe unsafe = unsafe();
            return unsafe == null ? PAGE_SIZE_UNKNOWN : unsafe.pageSize();
        }

        @Nullable
        private static Unsafe unsafe() {
            if (PlatformDependent.hasUnsafe()) {
                return (Unsafe)
                        AccessController.doPrivileged(
                                (PrivilegedAction<Object>) () -> UnsafeAccess.UNSAFE);
            } else {
                return null;
            }
        }
    }
}
