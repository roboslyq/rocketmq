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
package org.apache.rocketmq.acl.common;

import java.lang.reflect.Field;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.rocketmq.remoting.CommandCustomHeader;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;

import static org.apache.rocketmq.acl.common.SessionCredentials.ACCESS_KEY;
import static org.apache.rocketmq.acl.common.SessionCredentials.SECURITY_TOKEN;
import static org.apache.rocketmq.acl.common.SessionCredentials.SIGNATURE;

/**
 * ACL钩子函数。使用方式如下：
 *   DefaultMQProducer producer = new DefaultMQProducer("Test_Group", new AclClientRPCHook(new SessionCredentials(ACL_ACCESS_KEY,ACL_SECRET_KEY));
 * 即通过钩子函数将AlcLientRPCHook通过构造函数的参数传入到Producer中。
 */
public class AclClientRPCHook implements RPCHook {
    private final SessionCredentials sessionCredentials;
    /** 缓存对应的CommandCustomHeader的field,提高反射效率*/
    protected ConcurrentHashMap<Class<? extends CommandCustomHeader>, Field[]> fieldCache =
        new ConcurrentHashMap<Class<? extends CommandCustomHeader>, Field[]>();

    public AclClientRPCHook(SessionCredentials sessionCredentials) {
        this.sessionCredentials = sessionCredentials;
    }

    /**
     * RPC调用前，对ACL进行处理，即添加额外的参数
     * @param remoteAddr
     * @param request
     */
    @Override
    public void doBeforeRequest(String remoteAddr, RemotingCommand request) {
        /** 将消息转换成字节码 */
        byte[] total = AclUtils.combineRequestContent(request,
            parseRequestContent(request, sessionCredentials.getAccessKey(), sessionCredentials.getSecurityToken()));
        // 对字节码进行签名
        String signature = AclUtils.calSignature(total, sessionCredentials.getSecretKey());
        //添加扩展字段：SIGNATURE，即签名的结果
        request.addExtField(SIGNATURE, signature);
        //添加用户名扩展字段
        request.addExtField(ACCESS_KEY, sessionCredentials.getAccessKey());
        
        // The SecurityToken value is unneccessary,user can choose this one.
        // 添加密码字段
        if (sessionCredentials.getSecurityToken() != null) {
            request.addExtField(SECURITY_TOKEN, sessionCredentials.getSecurityToken());
        }
    }

    @Override
    public void doAfterResponse(String remoteAddr, RemotingCommand request, RemotingCommand response) {

    }

    /**
     * 解析RemotingCommand内容，转换成一个有序的map，因为签名结果与顺序有关。
     * @param request
     * @param ak
     * @param securityToken
     * @return
     */
    protected SortedMap<String, String> parseRequestContent(RemotingCommand request, String ak, String securityToken) {
        CommandCustomHeader header = request.readCustomHeader();
        // Sort property
        SortedMap<String, String> map = new TreeMap<String, String>();
        map.put(ACCESS_KEY, ak);
        if (securityToken != null) {
            map.put(SECURITY_TOKEN, securityToken);
        }
        try {
            // Add header properties
            if (null != header) {
                //从缓存中获取
                Field[] fields = fieldCache.get(header.getClass());
                //如果缓存中为空，则进行初始化
                if (null == fields) {
                    fields = header.getClass().getDeclaredFields();
                    for (Field field : fields) {
                        field.setAccessible(true);
                    }
                    //获取对应的field
                    Field[] tmp = fieldCache.putIfAbsent(header.getClass(), fields);
                    if (null != tmp) {
                        fields = tmp;
                    }
                }
                //循环遍列field，取对应的值放到map中
                for (Field field : fields) {
                    Object value = field.get(header);
                    if (null != value && !field.isSynthetic()) {
                        map.put(field.getName(), value.toString());
                    }
                }
            }
            return map;
        } catch (Exception e) {
            throw new RuntimeException("incompatible exception.", e);
        }
    }

    /**
     * 获取用户信息
     * @return
     */
    public SessionCredentials getSessionCredentials() {
        return sessionCredentials;
    }
}
