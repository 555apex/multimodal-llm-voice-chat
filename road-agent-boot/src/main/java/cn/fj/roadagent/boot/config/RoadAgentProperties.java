package cn.fj.roadagent.boot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "roadagent")
public class RoadAgentProperties {

    private final Traffic traffic = new Traffic();
    private final Model model = new Model();

    public Traffic getTraffic() {
        return traffic;
    }

    public Model getModel() {
        return model;
    }

    public static class Traffic {
        private String provider = "mock";
        private String mockScenario = "normal";
        private long staleAfterMinutes = 5;
        private final Amap amap = new Amap();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getMockScenario() {
            return mockScenario;
        }

        public void setMockScenario(String mockScenario) {
            this.mockScenario = mockScenario;
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
        private String apiKey = "bfdc3415643ef41dc3ea90fbe89888ab";
        private int roadLevel = 5;
        private int timeoutSeconds = 5;

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
    }

    public static class Model {
        private String provider = "rule";
        private String endpoint = "https://api.deepseek.com/chat/completions";
        private String apiKey = "";
        private String modelName = "deepseek-v4-flash";
        private int timeoutSeconds = 10;

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

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
