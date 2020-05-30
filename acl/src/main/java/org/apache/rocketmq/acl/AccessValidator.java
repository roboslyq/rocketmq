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

package org.apache.rocketmq.acl;

import java.util.List;
import org.apache.rocketmq.common.AclConfig;
import org.apache.rocketmq.common.PlainAccessConfig;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;

/**
 * Access control  list
 * ACL校验器，可以自行扩展，使用SPI机制加载
 */
public interface AccessValidator {

    /**
     * Parse to get the AccessResource(user, resource, needed permission)
     * 从请求对象RemotingCommand中解析出对应的资源抽象
     * @param request
     * @param remoteAddr
     * @return Plain access resource result,include access key,signature and some other access attributes.
     */
    AccessResource parse(RemotingCommand request, String remoteAddr);

    /**
     * Validate the access resource.
     * 权限校验
     * @param accessResource
     */
    void validate(AccessResource accessResource);

    /**
     * Update the access resource config
     * 更新资源权限配置，不用重启
     * @param plainAccessConfig
     * @return
     */
    boolean updateAccessConfig(PlainAccessConfig plainAccessConfig);

    /**
     * Delete the access resource config
     * 删除权限配置
     * @return
     */
    boolean deleteAccessConfig(String accesskey);

    /**
     * Get the access resource config version information
     * 获取权限配置版本
     * @return
     */
    String getAclConfigVersion();

    /**
     * Update globalWhiteRemoteAddresses in acl yaml config file
     * globalWhiteRemoteAddresses：全局IP白名单 ，例如*;192.168.*.*;192.168.0.1
     * @return
     */
    boolean updateGlobalWhiteAddrsConfig(List<String> globalWhiteAddrsList);

    /**
     * get broker cluster acl config information
     * @return
     */
    AclConfig getAllAclConfig();
}
