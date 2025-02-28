/*
 * Copyright (c) 2010-2019 Evolveum
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

package com.evolveum.midpoint.provisioning.ucf.impl.builtin.async;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.provisioning.ucf.api.AsyncUpdateMessageListener;
import com.evolveum.midpoint.provisioning.ucf.api.AsyncUpdateSource;
import com.evolveum.midpoint.provisioning.ucf.api.ListeningActivity;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.rabbitmq.client.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

/**
 *  Async Update source for AMQP 0.9.1 brokers.
 *
 *  An experimental implementation.
 */
public class Amqp091AsyncUpdateSource implements AsyncUpdateSource {

	private static final Trace LOGGER = TraceManager.getTrace(Amqp091AsyncUpdateSource.class);
	private static final int DEFAULT_PREFETCH = 10;

	@NotNull private final Amqp091SourceType sourceConfiguration;
	@NotNull private final PrismContext prismContext;
	@NotNull private final AsyncUpdateConnectorInstance connectorInstance;
	@NotNull private final ConnectionFactory connectionFactory;

	private static final long CONNECTION_CLOSE_TIMEOUT = 5000L;

	private Amqp091AsyncUpdateSource(@NotNull Amqp091SourceType sourceConfiguration, @NotNull AsyncUpdateConnectorInstance connectorInstance) {
		this.sourceConfiguration = sourceConfiguration;
		this.prismContext = connectorInstance.getPrismContext();
		this.connectorInstance = connectorInstance;
		this.connectionFactory = createConnectionFactory();
	}

	private enum State {
		PREPARING, OPEN, CLOSING, CLOSED
	}

	private class ListeningActivityImpl implements ListeningActivity {

		// the following items are initialized only once; in the constructor
		private Connection activeConnection;
		private Channel activeChannel;          // in the future we could create more channels to increase throughput
		private String activeConsumerTag;

		private volatile State state;

		private final AtomicInteger messagesBeingProcessed = new AtomicInteger(0);

		@Override
		public AsyncUpdateListeningActivityInformationType getInformation() {
			AsyncUpdateListeningActivityInformationType rv = new AsyncUpdateListeningActivityInformationType();
			rv.setName(sourceConfiguration.getName());
			if (activeConnection == null) {
				rv.setStatus(AsyncUpdateListeningActivityStatusType.DOWN);
			} else if (activeConnection.isOpen()) {
				rv.setStatus(AsyncUpdateListeningActivityStatusType.ALIVE);
			} else {
				rv.setStatus(AsyncUpdateListeningActivityStatusType.RECONNECTING);
			}
			return rv;
		}

		private ListeningActivityImpl(AsyncUpdateMessageListener listener) {
			try {
				state = State.PREPARING;
				activeConnection = connectionFactory.newConnection();
				activeChannel = activeConnection.createChannel();
				activeChannel.basicQos(defaultIfNull(sourceConfiguration.getPrefetch(), DEFAULT_PREFETCH));
				LOGGER.info("Opened AMQP connection = {}, channel = {}", activeConnection, activeChannel);  // todo debug
				DeliverCallback deliverCallback = (consumerTag, message) -> {
					try {
						messagesBeingProcessed.incrementAndGet();
						if (state != State.OPEN) {
							LOGGER.info("Ignoring message on {} because the state is {}", consumerTag, state);
							return;
						}
						byte[] body = message.getBody();
						LOGGER.info("Received a message on {}", consumerTag);   // todo debug
						LOGGER.info("Message is:\n{}", new String(body, StandardCharsets.UTF_8)); // todo trace
						boolean successful = listener.onMessage(createAsyncUpdateMessage(message));
						if (successful) {
							activeChannel.basicAck(message.getEnvelope().getDeliveryTag(), false);
						} else {
							rejectMessage(message);
						}
					} catch (RuntimeException | SchemaException e) {
						LoggingUtils.logUnexpectedException(LOGGER, "Got exception while processing message", e);
						rejectMessage(message);
					} finally {
						messagesBeingProcessed.decrementAndGet();
					}
				};
				state = State.OPEN;
				activeConsumerTag = activeChannel
						.basicConsume(sourceConfiguration.getQueue(), false, deliverCallback, consumerTag -> {});
				activeChannel.addShutdownListener(cause -> {
					// This is currently just for diagnostics (will log an error when the channel is unexpectedly closed)
					if (state == State.CLOSING || state == State.CLOSED) {
						LOGGER.debug("AMQP channel {} is going down (on application request)", activeChannel, cause);
					} else {
						LOGGER.error("AMQP channel {} is unexpectedly going down", activeChannel, cause);
					}
				});
				LOGGER.info("Opened consumer {}", activeConsumerTag);       // todo debug
			} catch (RuntimeException | IOException | TimeoutException e) {
				silentlyCloseActiveConnection();
				throw new SystemException("Couldn't start listening on " + listener + ": " + e.getMessage(), e);
			}
		}

		@Override
		public void stop() {
			stopInternal(false);
		}

		private void stopInternal(boolean withinMessageProcessing) {
			if (state != State.CLOSED) {
				state = State.CLOSING;
			}
			cancelConsumer();
			closeConnectionGracefully(withinMessageProcessing);
		}

		private void cancelConsumer() {
			if (activeConnection != null && activeChannel != null && activeConsumerTag != null) {
				LOGGER.info("Cancelling consumer {} on {}", activeConsumerTag, activeChannel);  // todo debug
				try {
					activeChannel.basicCancel(activeConsumerTag);
				} catch (IOException e) {
					LoggingUtils.logUnexpectedException(LOGGER, "Couldn't cancel consumer {} on channel {}", e, activeConsumerTag, activeChannel);
				}
				activeConsumerTag = null;
			} else {
				LOGGER.info("Consumer seems to be already cancelled: state={}, activeConnection={}, activeChannel={}, activeConsumerTag={}",
						state, activeConnection, activeChannel, activeConsumerTag);    // todo debug
			}
		}

		private void closeConnectionGracefully(boolean withinMessageProcessing) {
			if (activeConnection == null) {
				return;
			}
			LOGGER.info("Going to close connection gracefully (within processing: {}, messages being processed: {})",
					withinMessageProcessing, messagesBeingProcessed);
			// wait until remaining messages are processed (at least try so)
			int steadyState = withinMessageProcessing ? 1 : 0;

			long start = System.currentTimeMillis();
			while (messagesBeingProcessed.get() > steadyState
					&& System.currentTimeMillis() - start < CONNECTION_CLOSE_TIMEOUT) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					LOGGER.warn("Waiting for connection to be closed was interrupted");
					break;
				}
			}
			if (messagesBeingProcessed.get() > steadyState) {
				LOGGER.warn("Closing the connection even if {} messages are being processed; they will be unacknowledged",
						messagesBeingProcessed.get() - steadyState);
			}

			silentlyCloseActiveConnection();
		}

		@Override
		public String toString() {
			return "AMQP091-ListeningActivityImpl{" +
					"connection=" + activeConnection +
					", consumerTag='" + activeConsumerTag + '\'' +
					'}';
		}

		private void rejectMessage(Delivery message) throws IOException {
			AsyncUpdateErrorHandlingActionType action = getErrorHandlingAction();
			switch (action) {
				case RETRY:
					throw new UnsupportedEncodingException();
				case SKIP_UPDATE:
					activeChannel.basicReject(message.getEnvelope().getDeliveryTag(), false);
					break;
				case STOP_PROCESSING:
					stopInternal(true);
					break;
				default:
					throw new AssertionError(action);
			}
		}

		private void silentlyCloseActiveConnection() {
			try {
				if (state != State.CLOSED) {
					state = State.CLOSING;
				}
				if (activeConnection != null) {
					LOGGER.info("Closing {}", activeConnection);        // todo debug
					activeConnection.close();
					LOGGER.info("Closed {}", activeConnection);
				}
			} catch (Throwable t) {
				LoggingUtils.logUnexpectedException(LOGGER, "Couldn't close active connection {}", t, activeConnection);
			}
			state = State.CLOSED;
			activeConnection = null;
			activeChannel = null;
			activeConsumerTag = null;
		}
	}

	private Amqp091MessageType createAsyncUpdateMessage(Delivery message) {
		return new Amqp091MessageType()
				.sourceName(sourceConfiguration.getName())
				.body(message.getBody());
		// todo other attributes here
	}

	public static Amqp091AsyncUpdateSource create(AsyncUpdateSourceType configuration, AsyncUpdateConnectorInstance connectorInstance) {
		if (!(configuration instanceof Amqp091SourceType)) {
			throw new IllegalArgumentException("AMQP source requires " + Amqp091SourceType.class.getName() + " but got " +
					configuration.getClass().getName());
		}
		return new Amqp091AsyncUpdateSource((Amqp091SourceType) configuration, connectorInstance);
	}

	@Override
	public ListeningActivity startListening(AsyncUpdateMessageListener listener) {
		return new ListeningActivityImpl(listener);
	}

	@NotNull
	private ConnectionFactory createConnectionFactory() {
		try {
			ConnectionFactory connectionFactory = new ConnectionFactory();
			connectionFactory.setUri(sourceConfiguration.getUri());
			connectionFactory.setUsername(sourceConfiguration.getUsername());
			if (sourceConfiguration.getPassword() != null) {
				connectionFactory.setPassword(prismContext.getDefaultProtector().decryptString(sourceConfiguration.getPassword()));
			}
			if (sourceConfiguration.getVirtualHost() != null) {
				connectionFactory.setVirtualHost(sourceConfiguration.getVirtualHost());
			}
			return connectionFactory;
		} catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException | EncryptionException e) {
			throw new SystemException("Couldn't create connection factory: " + e.getMessage(), e);
		}
	}

	@Override
	public void test(OperationResult parentResult) {
		OperationResult result = parentResult.createSubresult(getClass().getName() + ".test");
		result.addParam("sourceName", sourceConfiguration.getName());
		try (Connection connection = connectionFactory.newConnection();
				Channel channel = connection.createChannel()) {
			LOGGER.info("Connection and channel created OK: {}", channel);
			int messageCount = channel.queueDeclarePassive(sourceConfiguration.getQueue()).getMessageCount();
			LOGGER.info("# of messages in queue {}: {}", sourceConfiguration.getQueue(), messageCount);
			result.recordSuccess();
		} catch (TimeoutException | IOException e) {
			result.recordFatalError("Couldn't connect to AMQP queue: " + e.getMessage(), e);
			throw new SystemException("Couldn't connect to AMQP queue: " + e.getMessage(), e);
		}
	}

	@NotNull
	private AsyncUpdateErrorHandlingActionType getErrorHandlingAction() {
		// TODO make overridable per source
		return connectorInstance.getErrorHandlingAction();
	}
}
