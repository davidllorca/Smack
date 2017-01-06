/**
 *
 * Copyright 2010 Jive Software.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.smack;

import java.io.IOException;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.Nonza;
import org.jivesoftware.smack.packet.TopLevelStreamElement;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.JidTestUtil;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

/**
 * A dummy implementation of {@link XMPPConnection}, intended to be used during
 * unit tests.
 * 
 * Instances store any packets that are delivered to be send using the
 * {@link #sendStanza(Stanza)} method in a blocking queue. The content of this queue
 * can be inspected using {@link #getSentPacket()}. Typically these queues are
 * used to retrieve a message that was generated by the client.
 * 
 * Packets that should be processed by the client to simulate a received stanza
 * can be delivered using the {@linkplain #processStanza(Stanza)} method.
 * It invokes the registered stanza(/packet) interceptors and listeners.
 * 
 * @see XMPPConnection
 * @author Guenther Niess
 */
public class DummyConnection extends AbstractXMPPConnection {

    private boolean reconnect = false;

    private final BlockingQueue<TopLevelStreamElement> queue = new LinkedBlockingQueue<TopLevelStreamElement>();

    public static ConnectionConfiguration.Builder<?,?> getDummyConfigurationBuilder() {
        return DummyConnectionConfiguration.builder().setXmppDomain(JidTestUtil.EXAMPLE_ORG).setUsernameAndPassword("dummy",
                        "dummypass");
    }

    public DummyConnection() {
        this(getDummyConfigurationBuilder().build());
    }

    private EntityFullJid getUserJid() {
        try {
            return JidCreate.entityFullFrom(config.getUsername()
                            + "@"
                            + config.getXMPPServiceDomain()
                            + "/"
                            + (config.getResource() != null ? config.getResource() : "Test"));
        }
        catch (XmppStringprepException e) {
            throw new IllegalStateException(e);
        }
    }

    public DummyConnection(ConnectionConfiguration configuration) {
        super(configuration);

        for (ConnectionCreationListener listener : XMPPConnectionRegistry.getConnectionCreationListeners()) {
            listener.connectionCreated(this);
        }
        user = getUserJid();
    }

    @Override
    protected void connectInternal() {
        connected = true;
        saslFeatureReceived.reportSuccess();
        tlsHandled.reportSuccess();
        streamId = "dummy-" + new Random(new Date().getTime()).nextInt();

        if (reconnect) {
            notifyReconnection();
        }
    }

    @Override
    protected void shutdown() {
        user = null;
        authenticated = false;

        callConnectionClosedListener();
        reconnect = true;
    }

    @Override
    public boolean isSecureConnection() {
        return false;
    }

    @Override
    public boolean isUsingCompression() {
        return false;
    }

    @Override
    protected void loginInternal(String username, String password, Resourcepart resource)
            throws XMPPException {
        user = getUserJid();
        authenticated = true;
    }

    @Override
    public void sendNonza(Nonza element) {
        queue.add(element);
    }

    @Override
    protected void sendStanzaInternal(Stanza packet) {
        queue.add(packet);
    }

    /**
     * Returns the number of packets that's sent through {@link #sendStanza(Stanza)} and
     * that has not been returned by {@link #getSentPacket()}.
     * 
     * @return the number of packets which are in the queue.
     */
    public int getNumberOfSentPackets() {
        return queue.size();
    }

    /**
     * Returns the first stanza(/packet) that's sent through {@link #sendStanza(Stanza)}
     * and that has not been returned by earlier calls to this method.
     * 
     * @return a sent packet.
     */
    public <P extends TopLevelStreamElement> P getSentPacket() {
        return getSentPacket(5 * 60);
    }

    /**
     * Returns the first stanza(/packet) that's sent through {@link #sendStanza(Stanza)}
     * and that has not been returned by earlier calls to this method. This
     * method will block for up to the specified number of seconds if no packets
     * have been sent yet.
     * 
     * @return a sent packet.
     */
    @SuppressWarnings("unchecked")
    public <P extends TopLevelStreamElement> P getSentPacket(int wait) {
        try {
            return (P) queue.poll(wait, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Processes a stanza(/packet) through the installed stanza(/packet) collectors and listeners
     * and letting them examine the stanza(/packet) to see if they are a match with the
     * filter.
     *
     * @param packet the stanza(/packet) to process.
     */
    public void processStanza(Stanza packet) {
        invokeStanzaCollectorsAndNotifyRecvListeners(packet);
    }

    /**
     * Enable stream feature.
     *
     * @param streamFeature the stream feature.
     * @since 4.2
     */
    public void enableStreamFeature(ExtensionElement streamFeature) {
        addStreamFeature(streamFeature);
    }

    public static DummyConnection newConnectedDummyConnection() {
        DummyConnection dummyConnection = new DummyConnection();
        try {
            dummyConnection.connect();
            dummyConnection.login();
        }
        catch (InterruptedException | SmackException | IOException | XMPPException e) {
            throw new IllegalStateException(e);
        }
        return dummyConnection;
    }

    public static class DummyConnectionConfiguration extends ConnectionConfiguration {
        protected DummyConnectionConfiguration(Builder builder) {
            super(builder);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder
                        extends
                        ConnectionConfiguration.Builder<Builder, DummyConnectionConfiguration> {

            private Builder() {
            }

            @Override
            public DummyConnectionConfiguration build() {
                return new DummyConnectionConfiguration(this);
            }

            @Override
            protected Builder getThis() {
                return this;
            }
        }
    }
}
