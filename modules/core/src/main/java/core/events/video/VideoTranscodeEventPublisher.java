package core.events.video;

public interface VideoTranscodeEventPublisher {
    void publish(VideoTranscodeRequestedEvent event);
}