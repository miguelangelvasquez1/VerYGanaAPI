package com.verygana2.utils.specifications;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.verygana2.models.ads.Ad;
import com.verygana2.models.enums.AdStatus;

public class AdSpecifications {

    public static Specification<Ad> hasAdvertiser(Long advertiserId) {
        return (root, query, cb) ->
            cb.equal(root.get("advertiser").get("id"), advertiserId);
    }

    public static Specification<Ad> hasStatus(AdStatus status) {
        return (root, query, cb) ->
            status != null ? cb.equal(root.get("status"), status) : cb.conjunction();
    }

    public static Specification<Ad> hasSearchTerm(String term) {
        return (root, query, cb) -> {
            if (term == null || term.isBlank()) return cb.conjunction();
            String likePattern = "%" + term.toLowerCase() + "%";
            return cb.like(cb.lower(root.get("title")), likePattern);
        };
    }

    public static Specification<Ad> inDateRange(ZonedDateTime start, ZonedDateTime end) {
        return (root, query, cb) -> {
            if (start == null && end == null) return cb.conjunction();
            if (start != null && end != null)
                return cb.between(root.get("createdAt"), start, end);
            if (start != null)
                return cb.greaterThanOrEqualTo(root.get("createdAt"), start);
            return cb.lessThanOrEqualTo(root.get("createdAt"), end);
        };
    }

    public static Specification<Ad> inCategories(List<Long> categoryIds) {
        return (root, query, cb) -> {
            if (categoryIds == null || categoryIds.isEmpty()) {
                return cb.conjunction();
            }
            // Delete duplicate items
            if (query != null) {
                query.distinct(true);
            }
            return root.join("categories").get("id").in(categoryIds);
        };
    }
}