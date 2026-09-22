package cn.fj.roadagent.application.port;

import cn.fj.roadagent.application.rag.RagSearchResult;
import java.util.List;

public interface RagSearchPort {
    List<RagSearchResult> search(String query, int topK);
}
