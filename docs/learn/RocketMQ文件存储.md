# RocketMQ文件存储

RocketMQ参考了Kafka的设计 ，是基于文件进行存储的。那么一个RocketMQ到底会生成哪些文件？

## 文件目录

> 文件默认保存地址在当前用户家目录`store`路径下。比如有一个用户叫`robos`。那么在linux上就是保存在`/home/robos/store`路径下。如果是在windows上，那么就保存在`C:\Users\robos\store`中

**Windows文件保存**

```c
C:\Users\robos\store>
|   abort
|   checkpoint
|   lock
|
+---commitlog
|       00000000000000000000
|
+---config
|       consumerFilter.json
|       consumerFilter.json.bak
|       consumerOffset.json
|       consumerOffset.json.bak
|       delayOffset.json
|       delayOffset.json.bak
|       topics.json
|       topics.json.bak
|
+---consumequeue
|   +---LUOYQ_TEST1
|   |   +---2
|   |   |       00000000000000000000
|   |   |
|   |   \---3
|   |           00000000000000000000
|   |
|   +---RMQ_SYS_TRANS_HALF_TOPIC
|   |   \---0
|   |           00000000000000000000
|   |
|   +---TOPIC_TEST1
|   |   \---15
|   |           00000000000000000000
|   |
|   \---TRANS_CHECK_MAX_TIME_TOPIC
|       \---0
|               00000000000000000000
|
\---index
        20200523075736564
```

## abort文件

​		`abort`中文意思是`中断、中止`意思。在rocketMQ中，此文件在系统启动时创建，系统正常停止时删除。如果在系统停止后还存在abort文件，表明系统是非正常关闭的。

## checkpoint文件

​		`checkpiont`：检查点(时间点)。存储了commitlog文件最后一次刷盘时间戳、consumequeue最后一次刷盘时间、index索引文件最后一次刷盘时间戳。

## lock文件

​		锁文件，通过此文件判断系统是否已经启动。在启动时创建此文件，并且锁住此文件。

## **commitlog目录**

消息的存储目录

## **consumequeue目录**

​	消息消费队列存储目录

## index目录

​	此目录下保存了消息索引文件

## config目录

​	运行期间一些配置信息

