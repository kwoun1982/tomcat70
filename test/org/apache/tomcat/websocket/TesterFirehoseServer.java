/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.websocket;

import java.io.IOException;

import javax.servlet.ServletContextEvent;
import javax.websocket.DeploymentException;
import javax.websocket.OnMessage;
import javax.websocket.RemoteEndpoint.Basic;
import javax.websocket.Session;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpoint;

import org.apache.tomcat.websocket.server.Constants;
import org.apache.tomcat.websocket.server.WsListener;

/**
 * Sends {@link #MESSAGE_COUNT} messages of size {@link #MESSAGE_SIZE} bytes as
 * quickly as possible after the client sends its first message.
 */
public class TesterFirehoseServer {

    public static final int MESSAGE_COUNT = 100000;
    public static final String MESSAGE;
    public static final int MESSAGE_SIZE = 1024;
    public static final int WAIT_TIME_MILLIS = 60000;

    static {
        StringBuilder sb = new StringBuilder(MESSAGE_SIZE);
        for (int i = 0; i < MESSAGE_SIZE; i++) {
            sb.append('x');
        }
        MESSAGE = sb.toString();
    }


    public static class Config extends WsListener {

        public static final String PATH = "/firehose";

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            super.contextInitialized(sce);
            ServerContainer sc =
                    (ServerContainer) sce.getServletContext().getAttribute(
                            Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
            try {
                sc.addEndpoint(Endpoint.class);
            } catch (DeploymentException e) {
                throw new IllegalStateException(e);
            }
        }
    }


    @ServerEndpoint(Config.PATH)
    public static class Endpoint {

        private volatile boolean started = false;

        @OnMessage
        public void onMessage(Session session,
                @SuppressWarnings("unused") String msg) throws IOException {

            if (started) {
                return;
            }
            synchronized (this) {
                if (started) {
                    return;
                } else {
                    started = true;
                }
            }

            session.getUserProperties().put(
                    "org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT",
                    Long.valueOf(WAIT_TIME_MILLIS));

            Basic remote = session.getBasicRemote();
            remote.setBatchingAllowed(true);

            for (int i = 0; i < MESSAGE_COUNT; i++) {
                remote.sendText(MESSAGE);
            }

            // Ensure remaining messages are flushed
            remote.setBatchingAllowed(false);
        }
    }
}
