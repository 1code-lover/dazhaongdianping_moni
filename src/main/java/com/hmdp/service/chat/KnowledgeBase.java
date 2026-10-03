package com.hmdp.service.chat;

import java.util.List;

public interface KnowledgeBase {
    String getSystemPrompt();
    List<String> searchRelevantKnowledge(String query, String method);

    /**
     * FAQ 直达：无大模型可用时，按语义相似度直接返回答案
     *
     * @param query 用户原始输入
     * @return 命中的 FAQ 答案；未命中返回 null
     */
    String matchFaq(String query);
}
