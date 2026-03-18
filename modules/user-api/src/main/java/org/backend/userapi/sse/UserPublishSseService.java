package org.backend.userapi.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserPublishSseService {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public SseEmitter subscribe(Long userContentId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(userContentId, emitter);

        emitter.onCompletion(() -> emitters.remove(userContentId));
        emitter.onTimeout(() -> emitters.remove(userContentId));
        emitter.onError(e -> emitters.remove(userContentId));

        try {
            emitter.send(SseEmitter.event().name("CONNECTED").data("ok"));
        } catch (IOException ignored) {}

        scheduler.scheduleAtFixedRate(() -> {
            SseEmitter em = emitters.get(userContentId);
            if (em == null) return;
            try {
                em.send(SseEmitter.event().name("PING").data("keep-alive"));
            } catch (Exception ex) {
                emitters.remove(userContentId);
            }
        }, 10, 10, TimeUnit.SECONDS);

        return emitter;
    }

    public void publish(Long userContentId, String eventName, String data) {
        SseEmitter emitter = emitters.get(userContentId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            emitters.remove(userContentId);
        }
    }

    public void pingAll(Long contentId) {
        publish(contentId, "PING", "keep-alive");
    }
}