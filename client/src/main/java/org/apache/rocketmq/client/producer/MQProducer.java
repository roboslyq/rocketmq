/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.client.producer;

import java.util.Collection;
import java.util.List;
import org.apache.rocketmq.client.MQAdmin;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.exception.RequestTimeoutException;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.exception.RemotingException;

/**
 * MQ生产者接口
 */
public interface MQProducer extends MQAdmin {
    void start() throws MQClientException;

    void shutdown();

    /**
     * 根据主题获取对应的MessageQueue
     * @param topic
     * @return
     * @throws MQClientException
     */
    List<MessageQueue> fetchPublishMessageQueues(final String topic) throws MQClientException;

    /**
     * 发送消息
     * @param msg
     * @return
     * @throws MQClientException
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     */
    SendResult send(final Message msg) throws MQClientException, RemotingException, MQBrokerException,
        InterruptedException;

    /**
     * 发送消息，指定超时时间
     * @param msg
     * @param timeout
     * @return
     * @throws MQClientException
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     */
    SendResult send(final Message msg, final long timeout) throws MQClientException,
        RemotingException, MQBrokerException, InterruptedException;

    /**
     * 发送消息，指定回调函数
     * @param msg
     * @param sendCallback
     * @throws MQClientException
     * @throws RemotingException
     * @throws InterruptedException
     */
    void send(final Message msg, final SendCallback sendCallback) throws MQClientException,
        RemotingException, InterruptedException;

    /**
     * 发送消息，指定回调函数和超时时间
     * @param msg
     * @param sendCallback
     * @param timeout
     * @throws MQClientException
     * @throws RemotingException
     * @throws InterruptedException
     */
    void send(final Message msg, final SendCallback sendCallback, final long timeout)
        throws MQClientException, RemotingException, InterruptedException;

    /**
     * 单向发送消息（不需要确认）
     * @param msg
     * @throws MQClientException
     * @throws RemotingException
     * @throws InterruptedException
     */
    void sendOneway(final Message msg) throws MQClientException, RemotingException,
        InterruptedException;

    /**
     * 指定MessageQueue，顺序消息生产需要
     * @param msg
     * @param mq
     * @return
     * @throws MQClientException
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     */
    SendResult send(final Message msg, final MessageQueue mq) throws MQClientException,
        RemotingException, MQBrokerException, InterruptedException;

    SendResult send(final Message msg, final MessageQueue mq, final long timeout)
        throws MQClientException, RemotingException, MQBrokerException, InterruptedException;

    void send(final Message msg, final MessageQueue mq, final SendCallback sendCallback)
        throws MQClientException, RemotingException, InterruptedException;

    void send(final Message msg, final MessageQueue mq, final SendCallback sendCallback, long timeout)
        throws MQClientException, RemotingException, InterruptedException;

    void sendOneway(final Message msg, final MessageQueue mq) throws MQClientException,
        RemotingException, InterruptedException;

    SendResult send(final Message msg, final MessageQueueSelector selector, final Object arg)
        throws MQClientException, RemotingException, MQBrokerException, InterruptedException;

    SendResult send(final Message msg, final MessageQueueSelector selector, final Object arg,
        final long timeout) throws MQClientException, RemotingException, MQBrokerException,
        InterruptedException;

    void send(final Message msg, final MessageQueueSelector selector, final Object arg,
        final SendCallback sendCallback) throws MQClientException, RemotingException,
        InterruptedException;

    void send(final Message msg, final MessageQueueSelector selector, final Object arg,
        final SendCallback sendCallback, final long timeout) throws MQClientException, RemotingException,
        InterruptedException;

    void sendOneway(final Message msg, final MessageQueueSelector selector, final Object arg)
        throws MQClientException, RemotingException, InterruptedException;

    TransactionSendResult sendMessageInTransaction(final Message msg,
        final LocalTransactionExecuter tranExecuter, final Object arg) throws MQClientException;

    TransactionSendResult sendMessageInTransaction(final Message msg,
        final Object arg) throws MQClientException;

    //for batch
    SendResult send(final Collection<Message> msgs) throws MQClientException, RemotingException, MQBrokerException,
        InterruptedException;

    SendResult send(final Collection<Message> msgs, final long timeout) throws MQClientException,
        RemotingException, MQBrokerException, InterruptedException;

    SendResult send(final Collection<Message> msgs, final MessageQueue mq) throws MQClientException,
        RemotingException, MQBrokerException, InterruptedException;

    SendResult send(final Collection<Message> msgs, final MessageQueue mq, final long timeout)
        throws MQClientException, RemotingException, MQBrokerException, InterruptedException;

    //for rpc

    /**
     * Rpc方式发送消息
     * @param msg
     * @param timeout
     * @return
     * @throws RequestTimeoutException
     * @throws MQClientException
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     */
    Message request(final Message msg, final long timeout) throws RequestTimeoutException, MQClientException,
        RemotingException, MQBrokerException, InterruptedException;

    void request(final Message msg, final RequestCallback requestCallback, final long timeout)
        throws MQClientException, RemotingException, InterruptedException, MQBrokerException;

    Message request(final Message msg, final MessageQueueSelector selector, final Object arg,
        final long timeout) throws RequestTimeoutException, MQClientException, RemotingException, MQBrokerException,
        InterruptedException;

    void request(final Message msg, final MessageQueueSelector selector, final Object arg,
        final RequestCallback requestCallback,
        final long timeout) throws MQClientException, RemotingException,
        InterruptedException, MQBrokerException;

    Message request(final Message msg, final MessageQueue mq, final long timeout)
        throws RequestTimeoutException, MQClientException, RemotingException, MQBrokerException, InterruptedException;

    void request(final Message msg, final MessageQueue mq, final RequestCallback requestCallback, long timeout)
        throws MQClientException, RemotingException, MQBrokerException, InterruptedException;
}
