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

public enum LocalTransactionState {
    // 本地事务提交成功，需要MQ给Prepared的消息进行提交，消费都可以正常消费
    COMMIT_MESSAGE,
    // 本地事务执行失败，需要MQ给Prepared的消息进行回滚
    ROLLBACK_MESSAGE,
    // 本地事务状态未知，MQ服务器会定时查询本地事务状态(即发送事务消息时最初状态)
    UNKNOW,
}
