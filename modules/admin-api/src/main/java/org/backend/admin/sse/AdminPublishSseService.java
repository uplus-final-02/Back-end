package org.backend.admin.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class AdminPublishSseService {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public SseEmitter subscribe(Long contentId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(contentId, emitter);

        emitter.onCompletion(() -> emitters.remove(contentId));
        emitter.onTimeout(() -> emitters.remove(contentId));
        emitter.onError(e -> emitters.remove(contentId));

        try { emitter.send(SseEmitter.event().name("CONNECTED").data("ok")); } catch (IOException ignored) {}

        scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().name("PING").data("keep-alive"));
            } catch (Exception e) {
                emitters.remove(contentId);
            }
        }, 10, 10, TimeUnit.SECONDS);

        return emitter;
    }

    public void publish(Long contentId, String eventName, String data) {
        SseEmitter emitter = emitters.get(contentId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            emitters.remove(contentId);
        }
    }
}