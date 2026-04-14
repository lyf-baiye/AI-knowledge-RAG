package com.knowledgemanager.common.util;

import java.util.UUID;

public class IdGenerator {

    public static String generateTaskId() {
        return "TASK-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String generateRagId() {
        return "RAG-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String generateAppId() {
        return "APP-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String generateVectorId() {
        return UUID.randomUUID().toString();
    }
}
