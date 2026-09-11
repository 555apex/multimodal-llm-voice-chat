package cn.fj.roadagent.application.speech;

public record SynthesizeSpeechCommand(String text, String requestId) {
    public SynthesizeSpeechCommand(String text) { this(text, null); }
    public SynthesizeSpeechCommand {
        if (requestId != null && !java.util.UUID.fromString(requestId).toString().equals(requestId)) {
            throw new IllegalArgumentException("Invalid speech request id");
        }
    }
}
