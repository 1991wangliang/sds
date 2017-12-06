package com.lorne.sds.server.service.impl;

import com.lorne.sds.server.service.DeliveryServerSendEventService;
import com.lorne.sds.server.service.DeliveryServerService;
import com.lorne.sds.server.service.DeliveryService;
import com.lorne.sds.server.service.RedisService;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Set;


/**
 * create by lorne on 2017/10/13
 */
@Service
public class DeliveryServiceImpl implements DeliveryService {


    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RedisService redisService;

    @Autowired
    private DeliveryServerSendEventService deliveryServerSendEventService;


    private Logger logger = LoggerFactory.getLogger(DeliveryServiceImpl.class);

    @Override
    public void delivery(ChannelHandlerContext ctx, Object msg) {
        deliveryServerSendEventService.onDeliveryListener(ctx,msg);
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        deliveryServerSendEventService.onConnectionListener(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        deliveryServerSendEventService.onDisConnectionListener(ctx);
    }

    @Override
    public void checkSocket() {
        logger.info("check - socket server start ");
        List<ServiceInstance> instances =  discoveryClient.getInstances(DeliveryServerService.SOCKET_SERVER_KEY);
        for(ServiceInstance instance:instances){

            String ip = instance.getHost();
            int port = instance.getPort();
            String ipPort = String.format("%s:%d",ip,port);

            logger.info("check - server:"+ipPort+" start ");

            String resVal = restTemplate.getForObject(instance.getUri()+"/socket/index",String.class);

            logger.info("check - server:"+ipPort+" end ,res->"+resVal);

            if(!"success".equals(resVal)){
                redisService.removeAll(ipPort);

                logger.info("remove -> server:"+ipPort );
            }

            Set<String> values = redisService.all(ipPort);

            for(String uniqueKey:values){
                logger.info("checkChannel -> server:"+ipPort +",uniqueKey->"+uniqueKey+" start .");
                boolean res =  restTemplate.postForObject(instance.getUri()+"/socket/checkChannel?uniqueKey={uniqueKey}",uniqueKey,Boolean.class,uniqueKey);
                logger.info("checkChannel -> server:"+ipPort +",uniqueKey->"+uniqueKey+" end . res->"+res);
                if(!res){
                    redisService.remove(ipPort,uniqueKey);
                }
            }

        }
    }
}
