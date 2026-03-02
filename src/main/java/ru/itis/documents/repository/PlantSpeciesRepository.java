package ru.itis.documents.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.itis.documents.domain.entity.PlantSpecies;

import java.util.List;
import java.util.Optional;

public interface PlantSpeciesRepository extends JpaRepository<PlantSpecies, Long>, PlantSpeciesRepositoryCustom {

    @Override
    @EntityGraph(attributePaths = {"careProfile", "tags"})
    List<PlantSpecies> findAll();

    @Override
    @EntityGraph(attributePaths = {"careProfile", "tags"})
    Optional<PlantSpecies> findById(Long id);

    @EntityGraph(attributePaths = {"careProfile", "tags"})
    Optional<PlantSpecies> findByExternalId(Long externalId);

    boolean existsByExternalId(Long externalId);

    /**
     * Этап 9.2 (P0): нестандартный метод через @Query (JPQL).
     *
     * "Топ капризных видов" с фильтрами. Метод не дублирует findAll/findById:
     * - добавлены опциональные фильтры
     * - добавлена сортировка по "капризности" (тег + параметры ухода)
     * - ограничение результата задаётся Pageable (top N)
     */
    @EntityGraph(attributePaths = {"careProfile", "tags"})
    @Query("""
            select distinct ps
            from PlantSpecies ps
            left join ps.careProfile cp
            left join ps.tags t
            where (:q is null or lower(ps.name) like concat('%', lower(:q), '%'))
              and (:tag is null or lower(t.name) = lower(:tag))
              and (:light is null or (cp.lightLevel is not null and lower(cp.lightLevel) like concat('%', lower(:light), '%')))
              and (:minHumidity is null or (cp.humidityPercent is not null and cp.humidityPercent >= :minHumidity))
              and (:maxWaterInterval is null or (cp.waterIntervalDays is not null and cp.waterIntervalDays <= :maxWaterInterval))
            order by
              case when lower(t.name) = 'капризное' then 1 else 0 end desc,
              coalesce(cp.humidityPercent, 0) desc,
              coalesce(cp.waterIntervalDays, 9999) asc,
              ps.name asc
            """)
    List<PlantSpecies> findTopCapriciousSpecies(
            @Param("q") String q,
            @Param("tag") String tag,
            @Param("light") String light,
            @Param("minHumidity") Integer minHumidity,
            @Param("maxWaterInterval") Integer maxWaterInterval,
            Pageable pageable
    );
}