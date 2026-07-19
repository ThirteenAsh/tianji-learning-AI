package com.tianji.aigc.service.impl;

import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    public final ChatClient chatClient;

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        return chatClient.prompt()
                .user(question)
                .stream()
                .chatResponse()
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
}
