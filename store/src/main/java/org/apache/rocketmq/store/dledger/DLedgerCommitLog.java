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
package org.apache.rocketmq.store.dledger;

import io.openmessaging.storage.dledger.AppendFuture;
import io.openmessaging.storage.dledger.DLedgerConfig;
import io.openmessaging.storage.dledger.DLedgerServer;
import io.openmessaging.storage.dledger.entry.DLedgerEntry;
import io.openmessaging.storage.dledger.protocol.AppendEntryRequest;
import io.openmessaging.storage.dledger.protocol.AppendEntryResponse;
import io.openmessaging.storage.dledger.protocol.DLedgerResponseCode;
import io.openmessaging.storage.dledger.store.file.DLedgerMmapFileStore;
import io.openmessaging.storage.dledger.store.file.MmapFile;
import io.openmessaging.storage.dledger.store.file.MmapFileList;
import io.openmessaging.storage.dledger.store.file.SelectMmapBufferResult;
import io.openmessaging.storage.dledger.utils.DLedgerUtils;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageExtBatch;
import org.apache.rocketmq.common.sysflag.MessageSysFlag;
import org.apache.rocketmq.store.AppendMessageResult;
import org.apache.rocketmq.store.AppendMessageStatus;
import org.apache.rocketmq.store.CommitLog;
import org.apache.rocketmq.store.DefaultMessageStore;
import org.apache.rocketmq.store.DispatchRequest;
import org.apache.rocketmq.store.MappedFile;
import org.apache.rocketmq.store.MessageExtBrokerInner;
import org.apache.rocketmq.store.PutMessageResult;
import org.apache.rocketmq.store.PutMessageStatus;
import org.apache.rocketmq.store.SelectMappedBufferResult;
import org.apache.rocketmq.store.StoreStatsService;
import org.apache.rocketmq.store.schedule.ScheduleMessageService;

/**
 *  1、 Store all metadata downtime for recovery, data protection reliability
 *      官方注释：保存了所有的无数据，在downtime时可以实现自我恢复，数据处于高可用状态。(Raft协议 + commitLog)
 *  2、 DLedgerCommitlog 继承自 Commitlog。
 *  3、 主要是增加了自动选主，主从复制多副本的一些特性（Reliability）。
 *  4、 DLedger 要整合 commitlog 文件，是不是可以把 rocketmq 消息，即一个个 commitlog 条目整体当成 DLedger 的 body 字段即可
 * 总结：
 *   1、DLedger 在整合时，使用 DLedger 条目包裹 RocketMQ 中的 commitlog 条目，即在 DLedger 条目的 body 字段来存储整条 commitlog 条目。
 *   2、引入 dividedCommitlogOffset 变量，表示物理偏移量小于该值的消息存在于旧的 commitlog 文件中，实现 升级 DLedger 集群后能访问到旧的数据。
 *   3、新 DLedger 集群启动后，会将最后一个 commitlog 填充，即新的数据不会再写入到 原先的 commitlog 文件。
 *   4、消息追加到 DLedger 数据日志文件中，返回的偏移量不是 DLedger 条目的起始偏移量，而是DLedger 条目中 body 字段的起始偏移量，即真实消息的起始偏移量，保证消息物理偏移量的语义与 RocketMQ Commitlog一样。
 */
public class DLedgerCommitLog extends CommitLog {
    /**
     * 基于 raft 协议实现的集群内的一个节点，用 DLedgerServer 实例表示。
     */
    private final DLedgerServer dLedgerServer;
    /**
     * DLedger 的配置信息。
     */
    private final DLedgerConfig dLedgerConfig;
    /**
     * DLedger 基于文件映射的存储实现。
     */
    private final DLedgerMmapFileStore dLedgerFileStore;
    /**
     * DLedger 所管理的存储文件集合，对比 RocketMQ 中的 MappedFileQueue。
     */
    private final MmapFileList dLedgerFileList;
    //The id identifies the broker role, 0 means master, others means slave
    // 节点ID，0 表示主节点，非0表示从节点
    private final int id;
    // 消息序列器
    private final MessageSerializer messageSerializer;
    // 用于记录\消息追加的时耗(日志追加所持有锁时间)。
    private volatile long beginTimeInDledgerLock = 0;
    //  This offset separate the old commitlog from dledger commitlog
    //  翻译：旧的commitlog文件与新的dledger commitlog的offset分割符。
    //  表示物理偏移量小于该值的消息存在于旧的 commitlog 文件中，实现升级 DLedger 集群后能访问到旧的数据
    //  即为了兼容旧版的CommitLog文件，将小于dividedCommitlogOffset文件在CommitLog文件中查找（旧的），
    //  最新的offset 大于dividedCommitlogOffset则保存在对应的DLedgerCommmitLog中。
    private long dividedCommitlogOffset = -1;
    // 是否正在恢复旧的 commitlog 文件。
    private boolean isInrecoveringOldCommitlog = false;
    /**
     * 在DefaultMessageStore类的构造函数完成对象创建，并且将DefaultMessageStore通过this传入到DLedgerCommitLog构造函数
     * @param defaultMessageStore
     */
    public DLedgerCommitLog(final DefaultMessageStore defaultMessageStore) {
        //调用父类 即 CommitLog 的构造函数，加载 ${ROCKETMQ_HOME}/store/ comitlog 下的 commitlog 文件，以便兼容升级 DLedger 的消息
        super(defaultMessageStore);
        //  DLedger相关配置
        dLedgerConfig = new DLedgerConfig();
        dLedgerConfig.setEnableDiskForceClean(defaultMessageStore.getMessageStoreConfig().isCleanFileForciblyEnable());
        dLedgerConfig.setStoreType(DLedgerConfig.FILE);
        dLedgerConfig.setSelfId(defaultMessageStore.getMessageStoreConfig().getdLegerSelfId());
        dLedgerConfig.setGroup(defaultMessageStore.getMessageStoreConfig().getdLegerGroup());
        dLedgerConfig.setPeers(defaultMessageStore.getMessageStoreConfig().getdLegerPeers());
        dLedgerConfig.setStoreBaseDir(defaultMessageStore.getMessageStoreConfig().getStorePathRootDir());
        dLedgerConfig.setMappedFileSizeForEntryData(defaultMessageStore.getMessageStoreConfig().getMappedFileSizeCommitLog());
        dLedgerConfig.setDeleteWhen(defaultMessageStore.getMessageStoreConfig().getDeleteWhen());
        dLedgerConfig.setFileReservedHours(defaultMessageStore.getMessageStoreConfig().getFileReservedTime() + 1);
        id = Integer.valueOf(dLedgerConfig.getSelfId().substring(1)) + 1;
        /**
         * 根据 DLedger 配置信息创建 DLedgerServer，即创建 DLedger 集群节点，集群内各个节点启动后，就会触发选主。
         */
        dLedgerServer = new DLedgerServer(dLedgerConfig);
        dLedgerFileStore = (DLedgerMmapFileStore) dLedgerServer.getdLedgerStore();
        //创建DlegerFileStore钩子函数: 兼容 Commitlog 文件的关键所在
        DLedgerMmapFileStore.AppendHook appendHook = (entry, buffer, bodyOffset) -> {
            assert bodyOffset == DLedgerEntry.BODY_OFFSET;
            buffer.position(buffer.position() + bodyOffset + MessageDecoder.PHY_POS_POSITION);
            buffer.putLong(entry.getPos() + bodyOffset);
        };
        // 注入钩子函数
        dLedgerFileStore.addAppendHook(appendHook);
        dLedgerFileList = dLedgerFileStore.getDataFileList();
        // 获取序列化器
        this.messageSerializer = new MessageSerializer(defaultMessageStore.getMessageStoreConfig().getMaxMessageSize());

    }

    /**
     * 启动时加载（调用父类）
     * @return
     */
    @Override
    public boolean load() {
        return super.load();
    }

    /**
     * 更新配置文件
     */
    private void refreshConfig() {
        dLedgerConfig.setEnableDiskForceClean(defaultMessageStore.getMessageStoreConfig().isCleanFileForciblyEnable());
        dLedgerConfig.setDeleteWhen(defaultMessageStore.getMessageStoreConfig().getDeleteWhen());
        dLedgerConfig.setFileReservedHours(defaultMessageStore.getMessageStoreConfig().getFileReservedTime() + 1);
    }

    /**
     * 如果存在旧的 commitlog 文件，则禁止删除 DLedger 文件，其具体做法就是禁止强制删除文件，并将文件的有效存储时间设置为 10 年
     */
    private void disableDeleteDledger() {
        dLedgerConfig.setEnableDiskForceClean(false);
        dLedgerConfig.setFileReservedHours(24 * 365 * 10);
    }

    @Override
    public void start() {
        dLedgerServer.startup();
    }

    @Override
    public void shutdown() {
        dLedgerServer.shutdown();
    }

    @Override
    public long flush() {
        dLedgerFileStore.flush();
        return dLedgerFileList.getFlushedWhere();
    }

    @Override
    public long getMaxOffset() {
        if (dLedgerFileStore.getCommittedPos() > 0) {
            return dLedgerFileStore.getCommittedPos();
        }
        if (dLedgerFileList.getMinOffset() > 0) {
            return dLedgerFileList.getMinOffset();
        }
        return 0;
    }

    @Override
    public long getMinOffset() {
        if (!mappedFileQueue.getMappedFiles().isEmpty()) {
            return mappedFileQueue.getMinOffset();
        }
        return dLedgerFileList.getMinOffset();
    }

    @Override
    public long getConfirmOffset() {
        return this.getMaxOffset();
    }

    @Override
    public void setConfirmOffset(long phyOffset) {
        log.warn("Should not set confirm offset {} for dleger commitlog", phyOffset);
    }

    @Override
    public long remainHowManyDataToCommit() {
        return dLedgerFileList.remainHowManyDataToCommit();
    }

    @Override
    public long remainHowManyDataToFlush() {
        return dLedgerFileList.remainHowManyDataToFlush();
    }

    /**
     * 清理过期文件
     * @param expiredTime
     * @param deleteFilesInterval
     * @param intervalForcibly
     * @param cleanImmediately
     * @return
     */
    @Override
    public int deleteExpiredFile(
        final long expiredTime,
        final int deleteFilesInterval,
        final long intervalForcibly,
        final boolean cleanImmediately
    ) {
        if (mappedFileQueue.getMappedFiles().isEmpty()) {
            refreshConfig();
            //To prevent too much log in defaultMessageStore
            // 因为此方法是在DefautlMessageStore中被调用的，当返回值小于0时，表示失败，
            // 失败之后会打印日志  log.warn("disk space will be full soon, but delete file failed.");
            // 所以当数据量为0时，返回最大Integer，防止打印太多日志
            return Integer.MAX_VALUE;
        } else {
            // 如果存在CommitLog文件，则禁止删除Dledger相关文件(保证offset的连续性)
            disableDeleteDledger();
        }
        // 调用父类方法，删除CommitLog文件
        int count = super.deleteExpiredFile(expiredTime, deleteFilesInterval, intervalForcibly, cleanImmediately);
        if (count > 0 || mappedFileQueue.getMappedFiles().size() != 1) {
            return count;
        }
        //the old logic will keep the last file, here to delete it
        // 旧的逻辑不会删除最后一个文件（即使已经过时），我们这里优化把它删除掉
        MappedFile mappedFile = mappedFileQueue.getLastMappedFile();
        log.info("Try to delete the last old commitlog file {}", mappedFile.getFileName());
        long liveMaxTimestamp = mappedFile.getLastModifiedTimestamp() + expiredTime;
        if (System.currentTimeMillis() >= liveMaxTimestamp || cleanImmediately) {
            while (!mappedFile.destroy(10 * 1000)) {
                DLedgerUtils.sleep(1000);
            }
            mappedFileQueue.getMappedFiles().remove(mappedFile);
        }
        return 1;
    }

    /**
     *
     * @param sbr SelectMmapBufferResult
     * @return
     */
    public SelectMappedBufferResult convertSbr(SelectMmapBufferResult sbr) {
        if (sbr == null) {
            return null;
        } else {
            return new DLedgerSelectMappedBufferResult(sbr);
        }

    }

    public SelectMmapBufferResult truncate(SelectMmapBufferResult sbr) {
        long committedPos = dLedgerFileStore.getCommittedPos();
        if (sbr == null || sbr.getStartOffset() == committedPos) {
            return null;
        }
        if (sbr.getStartOffset() + sbr.getSize() <= committedPos) {
            return sbr;
        } else {
            sbr.setSize((int) (committedPos - sbr.getStartOffset()));
            return sbr;
        }
    }

    /**
     * 通过偏移量来获取对应的数据
     * @param offset
     * @return
     */
    @Override
    public SelectMappedBufferResult getData(final long offset) {
        // 表示数据在旧的CommitLog文件中，还没保存到新的DLedgerCommitLog文件中，兼容CommitLog平滑升级迁移
        if (offset < dividedCommitlogOffset) {
            return super.getData(offset);
        }
        return this.getData(offset, offset == 0);
    }

    @Override
    public SelectMappedBufferResult getData(final long offset, final boolean returnFirstOnNotFound) {
        if (offset < dividedCommitlogOffset) {
            return super.getData(offset, returnFirstOnNotFound);
        }
        if (offset >= dLedgerFileStore.getCommittedPos()) {
            return null;
        }
        int mappedFileSize = this.dLedgerServer.getdLedgerConfig().getMappedFileSizeForEntryData();
        MmapFile mappedFile = this.dLedgerFileList.findMappedFileByOffset(offset, returnFirstOnNotFound);
        if (mappedFile != null) {
            int pos = (int) (offset % mappedFileSize);
            SelectMmapBufferResult sbr = mappedFile.selectMappedBuffer(pos);
            return convertSbr(truncate(sbr));
        }

        return null;
    }

    /**
     * 具体的 recover 方法实现
     * @param maxPhyOffsetOfConsumeQueue
     */
    private void recover(long maxPhyOffsetOfConsumeQueue) {
        // 加载 DLedger 相关的存储文件，并一一构建对应的 MmapFile，
        // 其初始化三个重要的指针 wrotePosition、flushedPosition、committedPosition 三个指针为文件的大小。
        dLedgerFileStore.load();
        if (dLedgerFileList.getMappedFiles().size() > 0) {
            // 如果存在mappedFile，恢复管辖的 MMapFile
            dLedgerFileStore.recover();
            // 设置 dividedCommitlogOffset 的值为 DLedger 中所有物理文件的最小偏移量。
            // 操作消息的物理偏移量小于该值，则从 commitlog 文件中查找。
            // 物理偏移量大于等于该值的话则从 DLedger 相关的文件中查找消息。
            dividedCommitlogOffset = dLedgerFileList.getFirstMappedFile().getFileFromOffset();

            MappedFile mappedFile = this.mappedFileQueue.getLastMappedFile();
            if (mappedFile != null) {
                // 如果存在旧的文件，则禁止删除DLedger 文件
                disableDeleteDledger();
            }
            long maxPhyOffset = dLedgerFileList.getMaxWrotePosition();
            // Clear ConsumeQueue redundant data
            // 如果 consumequeue 中存储的最大物理偏移量大于 DLedger 中最大的物理偏移量，则删除多余的 consumequeue 文件
            if (maxPhyOffsetOfConsumeQueue >= maxPhyOffset) {
                log.warn("[TruncateCQ]maxPhyOffsetOfConsumeQueue({}) >= processOffset({}), truncate dirty logic files", maxPhyOffsetOfConsumeQueue, maxPhyOffset);
                this.defaultMessageStore.truncateDirtyLogicFiles(maxPhyOffset);
            }
            return;
        }
        //Indicate that, it is the first time to load mixed commitlog, need to recover the old commitlog
        // 如果启用了 DLedger 并且是初次启动(还未生成 DLedger 相关的日志文件)，则需要恢复 旧的 commitlog 文件。
        isInrecoveringOldCommitlog = true;
        //No need the abnormal recover
        super.recoverNormally(maxPhyOffsetOfConsumeQueue);
        isInrecoveringOldCommitlog = false;

//     文件恢复整体过程如下：
/*      1、尝试查找最后一个 commitlog 文件，如果未找到，则结束。
        2、从最后一个文件的最后写入点(原 commitlog 文件的 待写入位点)尝试去查找写入的魔数，如果存在魔数并等于 CommitLog.BLANK_MAGIC_CODE，则无需再写入魔数，在升级 DLedger 第一次启动时，魔数为空，故需要写入魔数。
        3、初始化 dividedCommitlogOffset ，等于最后一个文件的起始偏移量加上文件的大小，即该指针指向最后一个文件的结束位置。
        4、将最后一个 commitlog 未写满的数据全部写入，其方法为 设置消息体的 size 与 魔数即可。
        5、设置最后一个文件的 wrotePosition、flushedPosition、committedPosition 为文件的大小，同样有意味者最后一个文件已经写满，下一条消息将写入 DLedger 中。
 */
        MappedFile mappedFile = this.mappedFileQueue.getLastMappedFile();
        if (mappedFile == null) {
            // 尝试查找最后一个 commitlog 文件，如果未找到，则结束。
            return;
        }
        ByteBuffer byteBuffer = mappedFile.sliceByteBuffer();
        byteBuffer.position(mappedFile.getWrotePosition());
        boolean needWriteMagicCode = true;
        // 1 TOTAL SIZE
        byteBuffer.getInt(); //size
        int magicCode = byteBuffer.getInt();
        if (magicCode == CommitLog.BLANK_MAGIC_CODE) {
            needWriteMagicCode = false;
        } else {
            log.info("Recover old commitlog found a illegal magic code={}", magicCode);
        }
        dLedgerConfig.setEnableDiskForceClean(false);
        dividedCommitlogOffset = mappedFile.getFileFromOffset() + mappedFile.getFileSize();
        log.info("Recover old commitlog needWriteMagicCode={} pos={} file={} dividedCommitlogOffset={}", needWriteMagicCode, mappedFile.getFileFromOffset() + mappedFile.getWrotePosition(), mappedFile.getFileName(), dividedCommitlogOffset);
        if (needWriteMagicCode) {
            byteBuffer.position(mappedFile.getWrotePosition());
            byteBuffer.putInt(mappedFile.getFileSize() - mappedFile.getWrotePosition());
            byteBuffer.putInt(BLANK_MAGIC_CODE);
            mappedFile.flush(0);
        }
        mappedFile.setWrotePosition(mappedFile.getFileSize());
        mappedFile.setCommittedPosition(mappedFile.getFileSize());
        mappedFile.setFlushedPosition(mappedFile.getFileSize());
        dLedgerFileList.getLastMappedFile(dividedCommitlogOffset);
        log.info("Will set the initial commitlog offset={} for dledger", dividedCommitlogOffset);
    }

    /**
     * 在 Broker 启动时会加载 commitlog、consumequeue等文件，需要恢复其相关是数据结构，特别是与写入、刷盘、提交等指针，其具体调用 recover 方法
     * @param maxPhyOffsetOfConsumeQueue
     */
    @Override
    public void recoverNormally(long maxPhyOffsetOfConsumeQueue) {
        recover(maxPhyOffsetOfConsumeQueue);
    }

    @Override
    public void recoverAbnormally(long maxPhyOffsetOfConsumeQueue) {
        recover(maxPhyOffsetOfConsumeQueue);
    }

    @Override
    public DispatchRequest checkMessageAndReturnSize(ByteBuffer byteBuffer, final boolean checkCRC) {
        return this.checkMessageAndReturnSize(byteBuffer, checkCRC, true);
    }

    @Override
    public DispatchRequest checkMessageAndReturnSize(ByteBuffer byteBuffer, final boolean checkCRC,
        final boolean readBody) {
        if (isInrecoveringOldCommitlog) {
            return super.checkMessageAndReturnSize(byteBuffer, checkCRC, readBody);
        }
        try {
            int bodyOffset = DLedgerEntry.BODY_OFFSET;
            int pos = byteBuffer.position();
            int magic = byteBuffer.getInt();
            //In dledger, this field is size, it must be gt 0, so it could prevent collision
            int magicOld = byteBuffer.getInt();
            if (magicOld == CommitLog.BLANK_MAGIC_CODE || magicOld == CommitLog.MESSAGE_MAGIC_CODE) {
                byteBuffer.position(pos);
                return super.checkMessageAndReturnSize(byteBuffer, checkCRC, readBody);
            }
            if (magic == MmapFileList.BLANK_MAGIC_CODE) {
                return new DispatchRequest(0, true);
            }
            byteBuffer.position(pos + bodyOffset);
            DispatchRequest dispatchRequest = super.checkMessageAndReturnSize(byteBuffer, checkCRC, readBody);
            if (dispatchRequest.isSuccess()) {
                dispatchRequest.setBufferSize(dispatchRequest.getMsgSize() + bodyOffset);
            } else if (dispatchRequest.getMsgSize() > 0) {
                dispatchRequest.setBufferSize(dispatchRequest.getMsgSize() + bodyOffset);
            }
            return dispatchRequest;
        } catch (Throwable ignored) {
        }

        return new DispatchRequest(-1, false /* success */);
    }

    @Override
    public boolean resetOffset(long offset) {
        //currently, it seems resetOffset has no use
        return false;
    }

    @Override
    public long getBeginTimeInLock() {
        return beginTimeInDledgerLock;
    }

    /**
     * 存储消息的核心方法
     * @param msg
     * @return
     */
    @Override
    public PutMessageResult putMessage(final MessageExtBrokerInner msg) {
        // Set the storage time 设置存储时间戳
        msg.setStoreTimestamp(System.currentTimeMillis());
        // Set the message body BODY CRC (consider the most appropriate setting  on the client)
        // 设置消息体的CRC校验
        msg.setBodyCRC(UtilAll.crc32(msg.getBody()));
        // 获取具体的存储服务实现
        StoreStatsService storeStatsService = this.defaultMessageStore.getStoreStatsService();
        // 获取对应的消息主题
        String topic = msg.getTopic();
        // 获取对应的QueueId
        int queueId = msg.getQueueId();

        //should be consistent with the old version
        // 和旧版本保持一致(即与CommitLog中的PutMessage代码保持一致)
        final int tranType = MessageSysFlag.getTransactionValue(msg.getSysFlag());
        // 非事务消息或者事务消息COMMIT
        if (tranType == MessageSysFlag.TRANSACTION_NOT_TYPE
            || tranType == MessageSysFlag.TRANSACTION_COMMIT_TYPE) {
            // Delay Delivery(延迟发送)
            if (msg.getDelayTimeLevel() > 0) {
                if (msg.getDelayTimeLevel() > this.defaultMessageStore.getScheduleMessageService().getMaxDelayLevel()) {
                    msg.setDelayTimeLevel(this.defaultMessageStore.getScheduleMessageService().getMaxDelayLevel());
                }
                // 将延迟消息存入主题SCHEDULE_TOPIC_XXXX中
                topic = ScheduleMessageService.SCHEDULE_TOPIC;
                queueId = ScheduleMessageService.delayLevel2QueueId(msg.getDelayTimeLevel());

                // Backup real topic, queueId
                MessageAccessor.putProperty(msg, MessageConst.PROPERTY_REAL_TOPIC, msg.getTopic());
                MessageAccessor.putProperty(msg, MessageConst.PROPERTY_REAL_QUEUE_ID, String.valueOf(msg.getQueueId()));
                msg.setPropertiesString(MessageDecoder.messageProperties2String(msg.getProperties()));

                msg.setTopic(topic);
                msg.setQueueId(queueId);
            }
        }

        // Back to Results: 定义dledger存储结果
        AppendMessageResult appendResult;
        AppendFuture<AppendEntryResponse> dledgerFuture;
        EncodeResult encodeResult;
        // 获取写锁（通过自旋方式，防止并发写，实现顺序读写）
        putMessageLock.lock(); //spin or ReentrantLock ,depending on store config
        long elapsedTimeInLock;
        long queueOffset;
        try {
            beginTimeInDledgerLock = this.defaultMessageStore.getSystemClock().now();
            // 序列化消息（因为DLedger与RocketMQ整合原理就是将RokcetMQ整个消息保存在body字节码中，因此需要序列化）
            encodeResult = this.messageSerializer.serialize(msg);
            // 获取queue偏移量
            queueOffset = topicQueueTable.get(encodeResult.queueOffsetKey);
            if (encodeResult.status != AppendMessageStatus.PUT_OK) {
                return new PutMessageResult(PutMessageStatus.MESSAGE_ILLEGAL, new AppendMessageResult(encodeResult.status));
            }
            AppendEntryRequest request = new AppendEntryRequest();
            request.setGroup(dLedgerConfig.getGroup());
            request.setRemoteId(dLedgerServer.getMemberState().getSelfId());
            request.setBody(encodeResult.data);
            // ====>dLedger追加消息:
            // 该方法会有集群内的 Leader 节点负责消息追加以及在消息复制，只有超过集群内的半数节点成功写入消息后，
            // 才会返回写入成功。如果追加成功，将会返回本次追加成功后的起始偏移量，
            // 即 pos 属性，即类似于 rocketmq 中 commitlog 的偏移量，即物理偏移量。
            dledgerFuture = (AppendFuture<AppendEntryResponse>) dLedgerServer.handleAppend(request);
            if (dledgerFuture.getPos() == -1) {
                return new PutMessageResult(PutMessageStatus.OS_PAGECACHE_BUSY, new AppendMessageResult(AppendMessageStatus.UNKNOWN_ERROR));
            }
            long wroteOffset = dledgerFuture.getPos() + DLedgerEntry.BODY_OFFSET;

            int msgIdLength = (msg.getSysFlag() & MessageSysFlag.STOREHOSTADDRESS_V6_FLAG) == 0 ? 4 + 4 + 8 : 16 + 4 + 8;
            ByteBuffer buffer = ByteBuffer.allocate(msgIdLength);

            String msgId = MessageDecoder.createMessageId(buffer, msg.getStoreHostBytes(), wroteOffset);
            elapsedTimeInLock = this.defaultMessageStore.getSystemClock().now() - beginTimeInDledgerLock;
            appendResult = new AppendMessageResult(AppendMessageStatus.PUT_OK, wroteOffset, encodeResult.data.length, msgId, System.currentTimeMillis(), queueOffset, elapsedTimeInLock);
            switch (tranType) {
                case MessageSysFlag.TRANSACTION_PREPARED_TYPE:
                case MessageSysFlag.TRANSACTION_ROLLBACK_TYPE:
                    break;
                case MessageSysFlag.TRANSACTION_NOT_TYPE:
                case MessageSysFlag.TRANSACTION_COMMIT_TYPE:
                    // The next update ConsumeQueue information
                    // 更新topicQueueTable,因为ConsumeQueue将需要使用
                    DLedgerCommitLog.this.topicQueueTable.put(encodeResult.queueOffsetKey, queueOffset + 1);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.error("Put message error", e);
            return new PutMessageResult(PutMessageStatus.UNKNOWN_ERROR, new AppendMessageResult(AppendMessageStatus.UNKNOWN_ERROR));
        } finally {
            beginTimeInDledgerLock = 0;
            putMessageLock.unlock();
        }

        if (elapsedTimeInLock > 500) {
            log.warn("[NOTIFYME]putMessage in lock cost time(ms)={}, bodyLength={} AppendMessageResult={}", elapsedTimeInLock, msg.getBody().length, appendResult);
        }

        PutMessageStatus putMessageStatus = PutMessageStatus.UNKNOWN_ERROR;
        try {
            // 获取消息追加结果
            AppendEntryResponse appendEntryResponse = dledgerFuture.get(3, TimeUnit.SECONDS);
            switch (DLedgerResponseCode.valueOf(appendEntryResponse.getCode())) {
                case SUCCESS:
                    putMessageStatus = PutMessageStatus.PUT_OK;
                    break;
                case INCONSISTENT_LEADER:
                case NOT_LEADER:
                case LEADER_NOT_READY:
                case DISK_FULL:
                    putMessageStatus = PutMessageStatus.SERVICE_NOT_AVAILABLE;
                    break;
                case WAIT_QUORUM_ACK_TIMEOUT:
                    //Do not return flush_slave_timeout to the client, for the ons client will ignore it.
                    putMessageStatus = PutMessageStatus.OS_PAGECACHE_BUSY;
                    break;
                case LEADER_PENDING_FULL:
                    putMessageStatus = PutMessageStatus.OS_PAGECACHE_BUSY;
                    break;
            }
        } catch (Throwable t) {
            log.error("Failed to get dledger append result", t);
        }
        // 获取消息追加结果
        PutMessageResult putMessageResult = new PutMessageResult(putMessageStatus, appendResult);
        if (putMessageStatus == PutMessageStatus.PUT_OK) {
            // Statistics
            storeStatsService.getSinglePutMessageTopicTimesTotal(msg.getTopic()).incrementAndGet();
            storeStatsService.getSinglePutMessageTopicSizeTotal(topic).addAndGet(appendResult.getWroteBytes());
        }
        return putMessageResult;
    }

    @Override
    public PutMessageResult putMessages(final MessageExtBatch messageExtBatch) {
        return new PutMessageResult(PutMessageStatus.MESSAGE_ILLEGAL, null);
    }

    @Override
    public CompletableFuture<PutMessageResult> asyncPutMessage(MessageExtBrokerInner msg) {
        return CompletableFuture.completedFuture(this.putMessage(msg));
    }

    @Override
    public CompletableFuture<PutMessageResult> asyncPutMessages(MessageExtBatch messageExtBatch) {
        return CompletableFuture.completedFuture(putMessages(messageExtBatch));
    }

    @Override
    public SelectMappedBufferResult getMessage(final long offset, final int size) {
        if (offset < dividedCommitlogOffset) {
            return super.getMessage(offset, size);
        }
        int mappedFileSize = this.dLedgerServer.getdLedgerConfig().getMappedFileSizeForEntryData();
        MmapFile mappedFile = this.dLedgerFileList.findMappedFileByOffset(offset, offset == 0);
        if (mappedFile != null) {
            int pos = (int) (offset % mappedFileSize);
            return convertSbr(mappedFile.selectMappedBuffer(pos, size));
        }
        return null;
    }

    @Override
    public long rollNextFile(final long offset) {
        int mappedFileSize = this.defaultMessageStore.getMessageStoreConfig().getMappedFileSizeCommitLog();
        return offset + mappedFileSize - offset % mappedFileSize;
    }

    @Override
    public HashMap<String, Long> getTopicQueueTable() {
        return topicQueueTable;
    }

    @Override
    public void setTopicQueueTable(HashMap<String, Long> topicQueueTable) {
        this.topicQueueTable = topicQueueTable;
    }

    @Override
    public void destroy() {
        super.destroy();
        dLedgerFileList.destroy();
    }

    @Override
    public boolean appendData(long startOffset, byte[] data) {
        //the old ha service will invoke method, here to prevent it
        return false;
    }

    @Override
    public void checkSelf() {
        dLedgerFileList.checkSelf();
    }

    @Override
    public long lockTimeMills() {
        long diff = 0;
        long begin = this.beginTimeInDledgerLock;
        if (begin > 0) {
            diff = this.defaultMessageStore.now() - begin;
        }

        if (diff < 0) {
            diff = 0;
        }

        return diff;
    }

    class EncodeResult {
        private String queueOffsetKey;
        private byte[] data;
        private AppendMessageStatus status;

        public EncodeResult(AppendMessageStatus status, byte[] data, String queueOffsetKey) {
            this.data = data;
            this.status = status;
            this.queueOffsetKey = queueOffsetKey;
        }
    }

    class MessageSerializer {
        // File at the end of the minimum fixed length empty
        private static final int END_FILE_MIN_BLANK_LENGTH = 4 + 4;
        private final ByteBuffer msgIdMemory;
        private final ByteBuffer msgIdV6Memory;
        // Store the message content
        private final ByteBuffer msgStoreItemMemory;
        // The maximum length of the message
        private final int maxMessageSize;
        // Build Message Key
        private final StringBuilder keyBuilder = new StringBuilder();

        private final StringBuilder msgIdBuilder = new StringBuilder();

//        private final ByteBuffer hostHolder = ByteBuffer.allocate(8);

        MessageSerializer(final int size) {
            this.msgIdMemory = ByteBuffer.allocate(4 + 4 + 8);
            this.msgIdV6Memory = ByteBuffer.allocate(16 + 4 + 8);
            this.msgStoreItemMemory = ByteBuffer.allocate(size + END_FILE_MIN_BLANK_LENGTH);
            this.maxMessageSize = size;
        }

        public ByteBuffer getMsgStoreItemMemory() {
            return msgStoreItemMemory;
        }

        public EncodeResult serialize(final MessageExtBrokerInner msgInner) {
            // STORETIMESTAMP + STOREHOSTADDRESS + OFFSET <br>

            // PHY OFFSET
            long wroteOffset = 0;

            int sysflag = msgInner.getSysFlag();

            int bornHostLength = (sysflag & MessageSysFlag.BORNHOST_V6_FLAG) == 0 ? 4 + 4 : 16 + 4;
            int storeHostLength = (sysflag & MessageSysFlag.STOREHOSTADDRESS_V6_FLAG) == 0 ? 4 + 4 : 16 + 4;
            ByteBuffer bornHostHolder = ByteBuffer.allocate(bornHostLength);
            ByteBuffer storeHostHolder = ByteBuffer.allocate(storeHostLength);

            // Record ConsumeQueue information
            keyBuilder.setLength(0);
            keyBuilder.append(msgInner.getTopic());
            keyBuilder.append('-');
            keyBuilder.append(msgInner.getQueueId());
            String key = keyBuilder.toString();

            Long queueOffset = DLedgerCommitLog.this.topicQueueTable.get(key);
            if (null == queueOffset) {
                queueOffset = 0L;
                DLedgerCommitLog.this.topicQueueTable.put(key, queueOffset);
            }

            // Transaction messages that require special handling
            final int tranType = MessageSysFlag.getTransactionValue(msgInner.getSysFlag());
            switch (tranType) {
                // Prepared and Rollback message is not consumed, will not enter the
                // consumer queuec
                case MessageSysFlag.TRANSACTION_PREPARED_TYPE:
                case MessageSysFlag.TRANSACTION_ROLLBACK_TYPE:
                    queueOffset = 0L;
                    break;
                case MessageSysFlag.TRANSACTION_NOT_TYPE:
                case MessageSysFlag.TRANSACTION_COMMIT_TYPE:
                default:
                    break;
            }

            /**
             * Serialize message
             */
            final byte[] propertiesData =
                msgInner.getPropertiesString() == null ? null : msgInner.getPropertiesString().getBytes(MessageDecoder.CHARSET_UTF8);

            final int propertiesLength = propertiesData == null ? 0 : propertiesData.length;

            if (propertiesLength > Short.MAX_VALUE) {
                log.warn("putMessage message properties length too long. length={}", propertiesData.length);
                return new EncodeResult(AppendMessageStatus.PROPERTIES_SIZE_EXCEEDED, null, key);
            }

            final byte[] topicData = msgInner.getTopic().getBytes(MessageDecoder.CHARSET_UTF8);
            final int topicLength = topicData.length;

            final int bodyLength = msgInner.getBody() == null ? 0 : msgInner.getBody().length;

            final int msgLen = calMsgLength(msgInner.getSysFlag(), bodyLength, topicLength, propertiesLength);

            // Exceeds the maximum message
            if (msgLen > this.maxMessageSize) {
                DLedgerCommitLog.log.warn("message size exceeded, msg total size: " + msgLen + ", msg body size: " + bodyLength
                    + ", maxMessageSize: " + this.maxMessageSize);
                return new EncodeResult(AppendMessageStatus.MESSAGE_SIZE_EXCEEDED, null, key);
            }
            // Initialization of storage space
            this.resetByteBuffer(msgStoreItemMemory, msgLen);
            // 1 TOTALSIZE
            this.msgStoreItemMemory.putInt(msgLen);
            // 2 MAGICCODE
            this.msgStoreItemMemory.putInt(DLedgerCommitLog.MESSAGE_MAGIC_CODE);
            // 3 BODYCRC
            this.msgStoreItemMemory.putInt(msgInner.getBodyCRC());
            // 4 QUEUEID
            this.msgStoreItemMemory.putInt(msgInner.getQueueId());
            // 5 FLAG
            this.msgStoreItemMemory.putInt(msgInner.getFlag());
            // 6 QUEUEOFFSET
            this.msgStoreItemMemory.putLong(queueOffset);
            // 7 PHYSICALOFFSET
            this.msgStoreItemMemory.putLong(wroteOffset);
            // 8 SYSFLAG
            this.msgStoreItemMemory.putInt(msgInner.getSysFlag());
            // 9 BORNTIMESTAMP
            this.msgStoreItemMemory.putLong(msgInner.getBornTimestamp());
            // 10 BORNHOST
            this.resetByteBuffer(bornHostHolder, bornHostLength);
            this.msgStoreItemMemory.put(msgInner.getBornHostBytes(bornHostHolder));
            // 11 STORETIMESTAMP
            this.msgStoreItemMemory.putLong(msgInner.getStoreTimestamp());
            // 12 STOREHOSTADDRESS
            this.resetByteBuffer(storeHostHolder, storeHostLength);
            this.msgStoreItemMemory.put(msgInner.getStoreHostBytes(storeHostHolder));
            //this.msgBatchMemory.put(msgInner.getStoreHostBytes());
            // 13 RECONSUMETIMES
            this.msgStoreItemMemory.putInt(msgInner.getReconsumeTimes());
            // 14 Prepared Transaction Offset
            this.msgStoreItemMemory.putLong(msgInner.getPreparedTransactionOffset());
            // 15 BODY
            this.msgStoreItemMemory.putInt(bodyLength);
            if (bodyLength > 0) {
                this.msgStoreItemMemory.put(msgInner.getBody());
            }
            // 16 TOPIC
            this.msgStoreItemMemory.put((byte) topicLength);
            this.msgStoreItemMemory.put(topicData);
            // 17 PROPERTIES
            this.msgStoreItemMemory.putShort((short) propertiesLength);
            if (propertiesLength > 0) {
                this.msgStoreItemMemory.put(propertiesData);
            }
            byte[] data = new byte[msgLen];
            this.msgStoreItemMemory.clear();
            this.msgStoreItemMemory.get(data);
            return new EncodeResult(AppendMessageStatus.PUT_OK, data, key);
        }

        private void resetByteBuffer(final ByteBuffer byteBuffer, final int limit) {
            byteBuffer.flip();
            byteBuffer.limit(limit);
        }

    }

    public static class DLedgerSelectMappedBufferResult extends SelectMappedBufferResult {

        private SelectMmapBufferResult sbr;

        public DLedgerSelectMappedBufferResult(SelectMmapBufferResult sbr) {
            super(sbr.getStartOffset(), sbr.getByteBuffer(), sbr.getSize(), null);
            this.sbr = sbr;
        }

        public synchronized void release() {
            super.release();
            if (sbr != null) {
                sbr.release();
            }
        }

    }

    public DLedgerServer getdLedgerServer() {
        return dLedgerServer;
    }

    public int getId() {
        return id;
    }

    public long getDividedCommitlogOffset() {
        return dividedCommitlogOffset;
    }
}
