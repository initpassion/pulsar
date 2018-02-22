/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.functions.runtime.instance.producers;

import static org.apache.pulsar.functions.runtime.instance.producers.MultiConsumersOneSinkTopicProducers.makeProducerName;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.apache.bookkeeper.common.concurrent.FutureUtils;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerConfiguration;
import org.apache.pulsar.client.api.PulsarClient;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Unit test of {@link MultiConsumersOneSinkTopicProducers}.
 */
public class MultiConsumersOneSinkTopicProducersTest {

    private static final String TEST_SINK_TOPIC = "test-sink-topic";

    private PulsarClient mockClient;
    private final Map<String, Producer> mockProducers = new HashMap<>();
    private MultiConsumersOneSinkTopicProducers producers;

    @BeforeMethod
    public void setup() throws Exception {
        this.mockClient = mock(PulsarClient.class);

        when(mockClient.createProducer(anyString(), any(ProducerConfiguration.class)))
            .thenAnswer(invocationOnMock -> {
                ProducerConfiguration conf = invocationOnMock.getArgumentAt(1, ProducerConfiguration.class);
                String producerName = conf.getProducerName();

                synchronized (mockProducers) {
                    Producer producer = mockProducers.get(producerName);
                    if (null == producer) {
                        producer = createMockProducer(producerName);
                        mockProducers.put(producerName, producer);
                    }
                    return producer;
                }
            });

        producers = new MultiConsumersOneSinkTopicProducers(mockClient, TEST_SINK_TOPIC);
        producers.initialize();
    }

    private Producer createMockProducer(String topic) {
        Producer producer = mock(Producer.class);
        when(producer.closeAsync())
            .thenAnswer(invocationOnMock -> {
                synchronized (mockProducers) {
                    mockProducers.remove(topic);
                }
                return FutureUtils.Void();
            });
        return producer;
    }

    @Test
    public void testGetCloseProducer() throws Exception {
        String srcTopic = "test-src-topic";
        int ptnIdx = 1234;
        Producer producer = producers.getProducer(srcTopic, ptnIdx);

        String producerName = makeProducerName(srcTopic, ptnIdx);

        assertSame(mockProducers.get(producerName), producer);
        verify(mockClient, times(1))
            .createProducer(
                eq(TEST_SINK_TOPIC),
                any(ProducerConfiguration.class)
            );
        assertTrue(producers.getProducers().containsKey(srcTopic));
        assertEquals(1, producers.getProducers().get(srcTopic).size());
        assertTrue(producers.getProducers().get(srcTopic).containsKey(ptnIdx));

        // second get will not create a new producer
        assertSame(mockProducers.get(producerName), producer);
        verify(mockClient, times(1))
            .createProducer(
                eq(TEST_SINK_TOPIC),
                any(ProducerConfiguration.class)
            );
        assertTrue(producers.getProducers().containsKey(srcTopic));
        assertEquals(1, producers.getProducers().get(srcTopic).size());
        assertTrue(producers.getProducers().get(srcTopic).containsKey(ptnIdx));

        // close
        producers.closeProducer(srcTopic, ptnIdx);
        verify(producer, times(1)).closeAsync();
        assertFalse(producers.getProducers().containsKey(srcTopic));
    }

}
