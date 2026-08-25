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
        private String provider = "amap";
        private long staleAfterMinutes = 5;
        private final Amap amap = new Amap();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public long getStaleAfterMinutes() {
            return staleAfterMinutes;
        }

        public void setStaleAfterMinutes(long staleAfterMinutes) {
            this.staleAfterMinutes = staleAfterMinutes;
        }

        public Amap getAmap() {
            return amap;
        }
    }

    public static class Amap {
        private String endpoint = "https://restapi.amap.com/v3/traffic/status/road";
        private String areaEndpoint = "https://restapi.amap.com/v3/traffic/status/rectangle";
        private String districtEndpoint = "https://restapi.amap.com/v3/config/district";
        private String apiKey = "";
        private int roadLevel = 5;
        private int timeoutSeconds = 8;
        private double areaTileSizeKm = 6.0;
        private int areaMaxTiles = 500;
        private int areaConcurrency = 4;
        private long areaCacheSeconds = 120;
        private long districtCacheHours = 24;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAreaEndpoint() {
            return areaEndpoint;
        }

        public void setAreaEndpoint(String areaEndpoint) {
            this.areaEndpoint = areaEndpoint;
        }

        public String getDistrictEndpoint() {
            return districtEndpoint;
        }

        public void setDistrictEndpoint(String districtEndpoint) {
            this.districtEndpoint = districtEndpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getRoadLevel() {
            return roadLevel;
        }

        public void setRoadLevel(int roadLevel) {
            this.roadLevel = roadLevel;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public double getAreaTileSizeKm() {
            return areaTileSizeKm;
        }

        public void setAreaTileSizeKm(double areaTileSizeKm) {
            this.areaTileSizeKm = areaTileSizeKm;
        }

        public int getAreaMaxTiles() {
            return areaMaxTiles;
        }

        public void setAreaMaxTiles(int areaMaxTiles) {
            this.areaMaxTiles = areaMaxTiles;
        }

        public int getAreaConcurrency() {
            return areaConcurrency;
        }

        public void setAreaConcurrency(int areaConcurrency) {
            this.areaConcurrency = areaConcurrency;
        }

        public long getAreaCacheSeconds() {
            return areaCacheSeconds;
        }

        public void setAreaCacheSeconds(long areaCacheSeconds) {
            this.areaCacheSeconds = areaCacheSeconds;
        }

        public long getDistrictCacheHours() {
            return districtCacheHours;
        }

        public void setDistrictCacheHours(long districtCacheHours) {
            this.districtCacheHours = districtCacheHours;
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
