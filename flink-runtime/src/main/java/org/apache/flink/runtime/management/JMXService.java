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

package org.apache.flink.runtime.management;

import org.apache.flink.util.NetUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

/** Provide a JVM-wide singleton JMX Service.
 * 提供 JVM 范围的单例 JMX 服务。
 * */
public class JMXService {
    private static final Logger LOG = LoggerFactory.getLogger(JMXService.class);
    private static JMXServer jmxServer = null;

    /** Acquire the global singleton JMXServer instance.
     * 获取全局单例 JMXServer 实例。
     * */
    public static Optional<JMXServer> getInstance() {
        return Optional.ofNullable(jmxServer);
    }

    /**
     * Start the JMV-wide singleton JMX server.
     * 启动 JMV 范围的单例 JMX 服务器。
     *
     * <p>If JMXServer static instance is already started, it will not be started again. Instead a
     * warning will be logged indicating which port the existing JMXServer static instance is
     * exposing.
     * 如果 JMXServer 静态实例已经启动，则不会再次启动。
     * 相反，将记录一个警告，指示现有 JMXServer 静态实例正在公开哪个端口。
     *
     * @param portsConfig port configuration of the JMX server.
     */
    public static synchronized void startInstance(String portsConfig) {
        if (jmxServer == null) {
            if (portsConfig != null) {
                Iterator<Integer> ports = NetUtils.getPortRangeFromString(portsConfig);
                if (ports.hasNext()) {
                    jmxServer = startJMXServerWithPortRanges(ports);
                }
                if (jmxServer == null) {
                    LOG.error(
                            "Could not start JMX server on any configured port(s) in: "
                                    + portsConfig);
                }
            }
        } else {
            LOG.warn("JVM-wide JMXServer already started at port: " + jmxServer.getPort());
        }
    }

    /** Stop the JMX server. */
    public static synchronized void stopInstance() throws IOException {
        if (jmxServer != null) {
            jmxServer.stop();
            jmxServer = null;
        }
    }

    public static synchronized Optional<Integer> getPort() {
        return Optional.ofNullable(jmxServer).map(JMXServer::getPort);
    }

    private static JMXServer startJMXServerWithPortRanges(Iterator<Integer> ports) {
        JMXServer successfullyStartedServer = null;
        while (ports.hasNext() && successfullyStartedServer == null) {
            JMXServer server = new JMXServer();
            int port = ports.next();
            try {
                server.start(port);
                LOG.info("Started JMX server on port " + port + ".");
                successfullyStartedServer = server;
            } catch (IOException ioe) { // assume port conflict
                LOG.debug("Could not start JMX server on port " + port + ".", ioe);
                try {
                    server.stop();
                } catch (Exception e) {
                    LOG.debug("Could not stop JMX server.", e);
                }
            }
        }
        return successfullyStartedServer;
    }
}
