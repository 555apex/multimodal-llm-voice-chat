package cn.fj.roadagent.boot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml中roadagent前缀配置的Java映射。 */
@ConfigurationProperties(prefix = "roadagent")
public class RoadAgentProperties {

    private final Traffic traffic = new Traffic();
    private final Model model = new Model();
    private final Memory memory = new Memory();
    private final Dispatch dispatch = new Dispatch();
    private final Speech speech = new Speech();

    public Traffic getTraffic() {
        return traffic;
    }

    public Model getModel() {
        return model;
    }

    public Memory getMemory() {
        return memory;
    }

    public Dispatch getDispatch() {
        return dispatch;
    }

    public Speech getSpeech() {
        return speech;
    }

    public static class Traffic {
        private long snapshotPollSeconds = 5;
        private long snapshotStableSeconds = 30;

        public long getSnapshotPollSeconds() {
            return snapshotPollSeconds;
        }

        public void setSnapshotPollSeconds(long snapshotPollSeconds) {
            this.snapshotPollSeconds = snapshotPollSeconds;
        }

        public long getSnapshotStableSeconds() {
            return snapshotStableSeconds;
        }

        public void setSnapshotStableSeconds(long snapshotStableSeconds) {
            this.snapshotStableSeconds = snapshotStableSeconds;
        }
    }

    public static class Model {
        private String provider = "openai-compatible";
        private String endpoint = "https://api.deepseek.com/chat/completions";
        private String apiKey = "";
        private String modelName = "deepseek-v4-flash";
        private boolean authEnabled = true;
        private Boolean enableThinking;
        private int timeoutSeconds = 60;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public boolean isAuthEnabled() {
            return authEnabled;
        }

        public void setAuthEnabled(boolean authEnabled) {
            this.authEnabled = authEnabled;
        }

        public Boolean getEnableThinking() {
            return enableThinking;
        }

        public void setEnableThinking(Boolean enableThinking) {
            this.enableThinking = enableThinking;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Memory {
        private int maxMessages = 20;
        private long idleMinutes = 60;

        public int getMaxMessages() {
            return maxMessages;
        }

        public void setMaxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
        }

        public long getIdleMinutes() {
            return idleMinutes;
        }

        public void setIdleMinutes(long idleMinutes) {
            this.idleMinutes = idleMinutes;
        }
    }

    public static class Dispatch {
        private long staleGeneratingSeconds = 120;

        public long getStaleGeneratingSeconds() {
            return staleGeneratingSeconds;
        }

        public void setStaleGeneratingSeconds(long staleGeneratingSeconds) {
            this.staleGeneratingSeconds = staleGeneratingSeconds;
        }
    }

    public static class Speech {
        private boolean enabled = true;
        private String serviceUrl = "http://localhost:8091";
        private int connectTimeoutSeconds = 3;
        private int requestTimeoutSeconds = 90;
        private int maxRecordingSeconds = 60;
        private long maxAudioBytes = 10 * 1024 * 1024L;
        private int maxTtsCharacters = 500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getServiceUrl() {
            return serviceUrl;
        }

        public void setServiceUrl(String serviceUrl) {
            this.serviceUrl = serviceUrl;
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public int getRequestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }

        public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
        }

        public int getMaxRecordingSeconds() {
            return maxRecordingSeconds;
        }

        public void setMaxRecordingSeconds(int maxRecordingSeconds) {
            this.maxRecordingSeconds = maxRecordingSeconds;
        }

        public long getMaxAudioBytes() {
            return maxAudioBytes;
        }

        public void setMaxAudioBytes(long maxAudioBytes) {
            this.maxAudioBytes = maxAudioBytes;
        }

        public int getMaxTtsCharacters() {
            return maxTtsCharacters;
        }

        public void setMaxTtsCharacters(int maxTtsCharacters) {
            this.maxTtsCharacters = maxTtsCharacters;
        }
    }
}
