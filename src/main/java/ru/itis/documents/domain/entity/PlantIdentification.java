package ru.itis.documents.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import ru.itis.documents.domain.enums.PlantIdentificationStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "plant_identifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"user", "candidates"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PlantIdentification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    // связь с пользователем
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PlantIdentificationStatus status;

    @Column(name = "source_photo_path", nullable = false, length = 512)
    private String sourcePhotoPath;

    // SHA-256 (hex) для кэширования распознаваний (экономия квоты Pl@ntNet)
    @Column(name = "photo_hash", length = 64)
    private String photoHash;

    @Column(name = "selected_scientific_name")
    private String selectedScientificName;

    @Column(name = "best_match")
    private String bestMatch;

    @Column(name = "best_match_score")
    private Double bestMatchScore;

    // Pl@ntNet: сколько запросов осталось на сегодня (можно логировать/показывать админу)
    @Column(name = "plantnet_remaining_requests")
    private Integer plantnetRemainingRequests;

    // ВАЖНО: TEXT для Postgres (иначе validate может ожидать OID из-за @Lob)
    @Column(name = "raw_response_json", columnDefinition = "text")
    private String rawResponseJson;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Builder.Default
    @OneToMany(mappedBy = "identification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IdentificationCandidate> candidates = new ArrayList<>();

    // --- helper methods (чтобы bidirectional связь была корректной) ---
    public void addCandidate(IdentificationCandidate c) {
        candidates.add(c);
        c.setIdentification(this);
    }

    public void clearCandidates() {
        for (IdentificationCandidate c : candidates) {
            c.setIdentification(null);
        }
        candidates.clear();
    }
}