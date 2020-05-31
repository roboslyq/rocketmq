package org.apache.rocketmq.example.acl;

import ch.qos.logback.core.util.TimeUtil;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.util.Timer;

/**
 * @author roboslyq
 * @data 2020/5/31 0:05
 * @desc :
 **/
public class AclProducer {
    public static void main(String[] args) throws MQClientException, InterruptedException {
        DefaultMQProducer producer = new DefaultMQProducer( "groupB" );
        producer.setNamesrvAddr( "127.0.0.1:9876" );
        producer.start();
        for (int i = 0; i <10 ; i++) {
            Message message = new Message( "topicB","tagB","hello world".getBytes() );
            try {
                producer.send( message);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                producer.shutdown();
            }
            Thread.sleep( 1000 );
        }
    }
}
