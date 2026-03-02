package ru.itis.documents.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import ru.itis.documents.domain.entity.CareProfile;
import ru.itis.documents.domain.entity.PlantSpecies;
import ru.itis.documents.domain.entity.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Этап 9.3 (P0): реализация CriteriaBuilder.
 *
 * Здесь реально используется "чистая JPA" — CriteriaBuilder + динамические Predicate.
 */
@Repository
public class PlantSpeciesRepositoryImpl implements PlantSpeciesRepositoryCustom {

    private static final int DEFAULT_LIMIT = 50;

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<PlantSpecies> findSuitableForApartment(
            String q,
            String roomLightLevel,
            Integer minHumidity,
            Integer maxWaterInterval,
            String tag,
            Integer limit
    ) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<PlantSpecies> cq = cb.createQuery(PlantSpecies.class);
        Root<PlantSpecies> ps = cq.from(PlantSpecies.class);

        // fetch — чтобы при показе карточек не ловить N+1
        ps.fetch("careProfile", JoinType.LEFT);
        ps.fetch("tags", JoinType.LEFT);

        // joins для фильтров
        Join<PlantSpecies, CareProfile> cp = ps.join("careProfile", JoinType.LEFT);
        Join<PlantSpecies, Tag> t = ps.join("tags", JoinType.LEFT);

        List<Predicate> predicates = new ArrayList<>();

        if (StringUtils.hasText(q)) {
            String like = "%" + q.trim().toLowerCase() + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(ps.get("name")), like),
                    cb.like(cb.lower(ps.get("latinName")), like)
            ));
        }

        if (StringUtils.hasText(tag)) {
            predicates.add(cb.equal(cb.lower(t.get("name")), tag.trim().toLowerCase()));
        }

        if (StringUtils.hasText(roomLightLevel)) {
            String like = "%" + roomLightLevel.trim().toLowerCase() + "%";
            predicates.add(cb.isNotNull(cp.get("lightLevel")));
            predicates.add(cb.like(cb.lower(cp.get("lightLevel")), like));
        }

        if (minHumidity != null) {
            predicates.add(cb.isNotNull(cp.get("humidityPercent")));
            predicates.add(cb.greaterThanOrEqualTo(cp.get("humidityPercent"), minHumidity));
        }

        if (maxWaterInterval != null) {
            predicates.add(cb.isNotNull(cp.get("waterIntervalDays")));
            predicates.add(cb.lessThanOrEqualTo(cp.get("waterIntervalDays"), maxWaterInterval));
        }

        cq.select(ps).distinct(true);

        if (!predicates.isEmpty()) {
            cq.where(cb.and(predicates.toArray(new Predicate[0])));
        }

        // Сортировка: "наиболее подходящие" сверху
        Expression<Integer> hasProfile = cb.<Integer>selectCase()
                .when(cb.isNotNull(cp.get("id")), 1)
                .otherwise(0);

        cq.orderBy(
                cb.desc(hasProfile),
                cb.desc(cb.coalesce(cp.get("humidityPercent"), 0)),
                cb.asc(cb.coalesce(cp.get("waterIntervalDays"), 9999)),
                cb.asc(ps.get("name"))
        );

        TypedQuery<PlantSpecies> query = em.createQuery(cq);
        int max = (limit == null || limit <= 0) ? DEFAULT_LIMIT : limit;
        query.setMaxResults(max);
        return query.getResultList();
    }
}