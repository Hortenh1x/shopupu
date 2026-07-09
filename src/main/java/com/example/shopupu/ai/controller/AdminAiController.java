package com.example.shopupu.ai.controller;

import com.example.shopupu.ai.service.ProductEmbeddingService;
import com.example.shopupu.ai.service.RecommendationComputationJob;
import com.example.shopupu.ai.service.ReviewSummaryService;
import com.example.shopupu.common.audit.AuditService;
import com.example.shopupu.common.security.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin/manager triggers for the AI maintenance jobs (all run async on the AI
 * pool and return 202). Protected by the /api/v1/admin/** rule in
 * SecurityConfig; every trigger lands in the audit trail.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ai")
public class AdminAiController {

    private final ProductEmbeddingService productEmbeddingService;
    private final RecommendationComputationJob recommendationComputationJob;
    private final ReviewSummaryService reviewSummaryService;
    private final AccessControlService accessControlService;
    private final AuditService auditService;

    @PostMapping("/embeddings/backfill")
    public ResponseEntity<Void> backfillEmbeddings() {
        auditService.record(accessControlService.currentEmail(), "AI_EMBEDDINGS_BACKFILL",
                "ai", "product_embeddings", "backfill requested");
        productEmbeddingService.backfillMissingAsync();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/recommendations/recompute")
    public ResponseEntity<Void> recomputeRecommendations() {
        auditService.record(accessControlService.currentEmail(), "AI_RECOMMENDATIONS_RECOMPUTE",
                "ai", "product_recommendations", "recompute requested");
        recommendationComputationJob.recomputeAsync();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/review-summaries/refresh")
    public ResponseEntity<Void> refreshReviewSummaries() {
        auditService.record(accessControlService.currentEmail(), "AI_REVIEW_SUMMARIES_REFRESH",
                "ai", "product_review_summary", "refresh requested");
        reviewSummaryService.refreshAllAsync();
        return ResponseEntity.accepted().build();
    }
}
