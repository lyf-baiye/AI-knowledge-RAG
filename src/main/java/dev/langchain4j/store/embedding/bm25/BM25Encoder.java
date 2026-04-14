package dev.langchain4j.store.embedding.bm25;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import lombok.Getter;
import lombok.Builder;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class BM25Encoder {

    private static final double DEFAULT_K1 = 1.2;
    private static final double DEFAULT_B = 0.75;
    
    @Getter
    private final Function<String, List<String>> tokenizer;
    
    private final double k1;
    private final double b;
    
    private List<String> documentIds;
    private Map<String, Map<String, Double>> idfCache; // cache of inverse document frequency
    private Map<String, Map<String, Integer>> termFrequencyCache; // cache of term frequency
    private Map<String, Integer> documentLengthCache; // cache of document lengths
    private double averageDocumentLength;
    
    @Builder
    public BM25Encoder(Function<String, List<String>> tokenizer, Double k1, Double b) {
        this.tokenizer = tokenizer != null ? tokenizer : this::defaultTokenizer;
        this.k1 = k1 != null ? k1 : DEFAULT_K1;
        this.b = b != null ? b : DEFAULT_B;
    }
    
    public void train(List<TextSegment> documents) {
        if (documents == null) {
            throw new IllegalArgumentException("documents cannot be null");
        }
        
        documentIds = new ArrayList<>();
        Map<String, Map<String, Integer>> termFrequencyInDocuments = new HashMap<>();
        documentLengthCache = new HashMap<>();
        int totalDocumentLength = 0;
        
        for (int i = 0; i < documents.size(); i++) {
            TextSegment document = documents.get(i);
            String documentId = "doc_" + i;
            documentIds.add(documentId);
            
            List<String> tokens = tokenizer.apply(document.text());
            Map<String, Integer> termFrequencyInCurrentDocument = new HashMap<>();
            
            for (String token : tokens) {
                termFrequencyInCurrentDocument.merge(token, 1, Integer::sum);
            }
            
            termFrequencyCache.put(documentId, termFrequencyInCurrentDocument);
            int documentLength = tokens.size();
            documentLengthCache.put(documentId, documentLength);
            totalDocumentLength += documentLength;
            
            termFrequencyInDocuments.put(documentId, termFrequencyInCurrentDocument);
        }
        
        if (documentIds.isEmpty()) {
            averageDocumentLength = 0;
        } else {
            averageDocumentLength = (double) totalDocumentLength / documentIds.size();
        }
        
        // Calculate IDF for each unique term
        Set<String> uniqueTerms = new HashSet<>();
        for (Map<String, Integer> termFreq : termFrequencyInDocuments.values()) {
            uniqueTerms.addAll(termFreq.keySet());
        }
        
        idfCache = new HashMap<>();
        for (String term : uniqueTerms) {
            long documentsContainingTerm = termFrequencyInDocuments.values().stream()
                    .mapToLong(termFreq -> termFreq.getOrDefault(term, 0))
                    .filter(freq -> freq > 0)
                    .count();
            
            double idf = Math.log(1.0 + (documentIds.size() - documentsContainingTerm + 0.5) / (documentsContainingTerm + 0.5));
            Map<String, Double> termIdf = new HashMap<>();
            termIdf.put(term, idf);
            idfCache.put(term, termIdf);
        }
    }
    
    public Embedding embed(TextSegment textSegment) {
        if (documentIds == null || documentIds.isEmpty()) {
            // 如果未训练，则返回零向量
            return Embedding.from(new float[0]);
        }
        
        List<String> tokens = tokenizer.apply(textSegment.text());
        Map<String, Integer> termFrequencies = new HashMap<>();
        
        for (String token : tokens) {
            termFrequencies.merge(token, 1, Integer::sum);
        }
        
        // 获取所有可能的维度（训练期间看到的所有术语）
        Set<String> allTerms = idfCache.keySet();
        List<String> sortedTerms = new ArrayList<>(allTerms);
        Collections.sort(sortedTerms);
        
        // 创建基于所有术语的分数数组
        double[] scores = new double[sortedTerms.size()];
        
        for (int i = 0; i < sortedTerms.size(); i++) {
            String term = sortedTerms.get(i);
            int termFreq = termFrequencies.getOrDefault(term, 0);
            double idf = idfCache.get(term).get(term);
            
            // 计算此术语的BM25分数
            double score = 0.0;
            if (idf > 0 && termFreq > 0) {
                score = (termFreq * idf * (k1 + 1)) / (termFreq + k1);
            }
            
            scores[i] = score;
        }
        
        // 转换为float数组，这是Embedding期望的格式
        float[] floatScores = new float[scores.length];
        for (int i = 0; i < scores.length; i++) {
            floatScores[i] = (float) scores[i];
        }
        
        return new CustomEmbedding(floatScores);
    }
    
    public List<String> defaultTokenizer(String text) {
        // 简单的分词器，按空白和标点符号分割
        String[] tokens = text.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5\\s]", " ")
                .split("\\s+");
        return asList(tokens);
    }
    
    // 将CustomEmbedding设为公共静态类以供外部访问
    public static class CustomEmbedding extends Embedding {
        private final float[] vector;
        
        public CustomEmbedding(float[] vector) {
            super(vector);
            this.vector = vector;
        }
        
        public float[] vectorAsArray() {
            return vector;
        }
    }
}