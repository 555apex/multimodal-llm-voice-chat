package cn.fj.roadagent.application.rag;

public record RagSearchResult(String id, String content, String knowledgeTitle,
                              String knowledgeFilename, double score) {
    public RagSearchResult {
        id = normalize(id);
        content = normalize(content);
        knowledgeTitle = normalize(knowledgeTitle);
        knowledgeFilename = normalize(knowledgeFilename);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
