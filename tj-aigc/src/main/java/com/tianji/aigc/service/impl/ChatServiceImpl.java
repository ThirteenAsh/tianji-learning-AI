package com.tianji.aigc.service.impl;

import cn.hutool.core.date.DateUtil;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    public final ChatClient chatClient;
    public final SystemPromptConfig systemPromptConfig;
    // 存储大模型的生成状态，这里采用ConcurrentHashMap是确保线程安全
    // 目前的版本暂时用Map实现，如果考虑分布式环境的话，可以考虑用redis来实现
    private static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        // 获取对话id
        var conversationId = ChatService.getConversationId(sessionId);
        return chatClient.prompt()
                .system(promptSystem -> promptSystem
                        //系统提示词
                        .text(systemPromptConfig.getChatSystemMessage().get())
                        //系统提示中的参数
                        .params(Map.of("now", DateUtil.now()))
                )
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId)) //设置对话记忆中的对话id
                .user(question)
                .stream()
                .chatResponse()
                .doFirst(() -> {
                    //设置大模型生成状态为true，表示正在生成
                    GENERATE_STATUS.put(sessionId, true);
                })
                .doOnError(error -> {
                    log.error("大模型生成异常", error);
                    GENERATE_STATUS.remove(sessionId);
                })
                .doOnComplete(() -> GENERATE_STATUS.remove(sessionId))
                //当大模型生成状态为false时，停止生成
                .takeWhile(chatResponse -> GENERATE_STATUS.getOrDefault(sessionId, false))
                .map(chatResponse -> {
                    //拿到大模型生成的内容
                    var text = chatResponse.getResult().getOutput().getText();
                    return ChatEventVO.builder()
                            .eventData(text)
                            .eventType(ChatEventTypeEnum.DATA.getValue())
                            .build();
                })
                //结束标识
                .concatWith(Flux.just(ChatEventVO.builder()
                        .eventType(ChatEventTypeEnum.STOP.getValue())
                        .build()));
    }

    @Override
    public void stop(String sessionId) {
        GENERATE_STATUS.remove(sessionId);
    }
}
