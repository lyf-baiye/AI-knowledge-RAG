package com.knowledgemanager.common.constant;

public class Constants {

    public static final class UserRole {
        public static final String ADMIN = "ADMIN";
        public static final String USER = "USER";
        public static final String VIEWER = "VIEWER";
    }

    public static final class UserStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String INACTIVE = "INACTIVE";
        public static final String LOCKED = "LOCKED";
    }

    public static final class KnowledgeBaseStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String ARCHIVED = "ARCHIVED";
        public static final String DELETED = "DELETED";
    }

    public static final class Visibility {
        public static final String PRIVATE = "PRIVATE";
        public static final String PUBLIC = "PUBLIC";
        public static final String SHARED = "SHARED";
    }

    public static final class FileFormat {
        public static final String PDF = "PDF";
        public static final String WORD = "WORD";
        public static final String MARKDOWN = "MD";
    }

    public static final class ProcessStatus {
        public static final String PENDING = "PENDING";
        public static final String PROCESSING = "PROCESSING";
        public static final String COMPLETED = "COMPLETED";
        public static final String FAILED = "FAILED";
    }

    public static final class TaskStatus {
        public static final String PENDING = "PENDING";
        public static final String RUNNING = "RUNNING";
        public static final String COMPLETED = "COMPLETED";
        public static final String FAILED = "FAILED";
    }

    public static final class TaskType {
        public static final String FILE_UPLOAD = "FILE_UPLOAD";
        public static final String VECTORIZE = "VECTORIZE";
        public static final String CHUNK_MERGE = "CHUNK_MERGE";
    }

    public static final class ChunkStrategy {
        public static final String FIXED_SIZE = "FIXED_SIZE";
        public static final String SEMANTIC = "SEMANTIC";
        public static final String CUSTOM = "CUSTOM";
    }

    public static final class ResourceType {
        public static final String KNOWLEDGE_BASE = "KNOWLEDGE_BASE";
        public static final String FILE = "FILE";
        public static final String CHUNK = "CHUNK";
    }

    public static final class Permission {
        public static final String READ = "READ";
        public static final String WRITE = "WRITE";
        public static final String DELETE = "DELETE";
        public static final String MANAGE = "MANAGE";
    }

    public static final class ApplicationStatus {
        public static final String PENDING = "PENDING";
        public static final String APPROVED = "APPROVED";
        public static final String REJECTED = "REJECTED";
    }

    public static final class QueryStatus {
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";
        public static final String TIMEOUT = "TIMEOUT";
    }

    public static final class RedisKey {
        public static final String USER_TOKEN = "user:token:";
        public static final String USER_PERMISSION = "user:permission:";
        public static final String KNOWLEDGE_BASE = "knowledge:base:";
        public static final String RAG_QUERY_CACHE = "rag:query:";
    }
}
