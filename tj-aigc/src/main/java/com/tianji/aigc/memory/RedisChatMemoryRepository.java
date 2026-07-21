package com.tianji.aigc.memory;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

/**
 * 基于Redis实现的ChatMemoryRepository
 */
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    // 默认redis中key的前缀
    public static final String DEFAULT_PREFIX = "CHAT:";
    private final String prefix;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public RedisChatMemoryRepository() {
        this(DEFAULT_PREFIX);
    }

    public RedisChatMemoryRepository(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public List<String> findConversationIds() {
        Set<String> keys = stringRedisTemplate.keys(prefix + "*");
        if (keys != null) {
            return StreamUtil.of(keys)
                    .map(key -> StrUtil.replace(key, prefix,""))
                    .toList();
        }
        return List.of();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String key = getKey(conversationId);
        var listOps = stringRedisTemplate.boundListOps(key);
        List<String> messages = listOps.range(0, -1);
        return CollStreamUtil.toList(messages, MessageUtil::toMessage);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        //注意，message是全量的消息列表
        String redisKey = getKey(conversationId);
        var listOps = stringRedisTemplate.boundListOps(redisKey);

        //将原有消息删除
        deleteByConversationId(conversationId);

        //将新的消息列表保存到redis中
        messages.forEach(message -> {
            listOps.rightPush(MessageUtil.toJson(message));
        });
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        var redisKey = this.getKey(conversationId);
        this.stringRedisTemplate.delete(redisKey);
    }

    private String getKey(String conversationId) {
        return prefix + conversationId;
    }
}
