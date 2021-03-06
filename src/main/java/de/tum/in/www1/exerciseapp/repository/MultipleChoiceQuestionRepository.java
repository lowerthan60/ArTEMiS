package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.MultipleChoiceQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data JPA repository for the MultipleChoiceQuestion entity.
 */
@SuppressWarnings("unused")
@Repository
public interface MultipleChoiceQuestionRepository extends JpaRepository<MultipleChoiceQuestion, Long> {

}
