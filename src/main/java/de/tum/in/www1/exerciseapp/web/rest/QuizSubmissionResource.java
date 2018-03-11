package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.domain.enumeration.SubmissionType;
import de.tum.in.www1.exerciseapp.service.*;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * REST controller for managing QuizSubmission.
 */
@RestController
@RequestMapping("/api")
public class QuizSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(QuizSubmissionResource.class);

    private static final String ENTITY_NAME = "quizSubmission";

    private final QuizExerciseService quizExerciseService;
    private final QuizSubmissionService quizSubmissionService;
    private final ParticipationService participationService;

    public QuizSubmissionResource(QuizExerciseService quizExerciseService,
                                  QuizSubmissionService quizSubmissionService,
                                  ParticipationService participationService) {
        this.quizExerciseService = quizExerciseService;
        this.quizSubmissionService = quizSubmissionService;
        this.participationService = participationService;
    }

    /**
     * POST  /courses/:courseId/exercises/:exerciseId/submissions/practice : Submit a new quizSubmission for practice mode.
     *
     * @param courseId       only included for API consistency, not actually used
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param principal      the current user principal
     * @param quizSubmission the quizSubmission to submit
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("/courses/{courseId}/exercises/{exerciseId}/submissions/practice")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Result> submitForPractice(@PathVariable Long courseId, @PathVariable Long exerciseId, Principal principal, @RequestBody QuizSubmission quizSubmission) {
        log.debug("REST request to submit QuizSubmission for practice : {}", quizSubmission);

        // recreate pointers back to submission in each submitted answer
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            submittedAnswer.setSubmission(quizSubmission);
        }

        if (quizSubmission.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new quizSubmission cannot already have an ID.")).body(null);
        }

        QuizExercise quizExercise = quizExerciseService.findOneWithQuestions(exerciseId);
        if (quizExercise == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        if (!quizExerciseService.userIsAllowedToSeeExercise(quizExercise)) {
            return ResponseEntity.status(403).headers(HeaderUtil.createFailureAlert("submission", "Forbidden", "You are not allowed to participate in this exercise.")).body(null);
        }

        Participation participation = participationService.init(quizExercise, principal.getName());
        participation.setExercise(quizExercise);
        if (!quizExercise.isEnded() || !quizExercise.isIsOpenForPractice()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseNotOpenForPractice", "The exercise is not open for practice or hasn't ended yet.")).body(null);
        }

        // update and save submission
        Result result = quizSubmissionService.submitForPractice(quizSubmission, quizExercise, participation);

        // return quizSubmission
        quizSubmission.setSubmissionDate(result.getCompletionDate());
        return ResponseEntity.ok(result);
    }

    /**
     * POST  /courses/:courseId/exercises/:exerciseId/submissions/preview : Submit a new quizSubmission for preview mode.
     * Nothing will be saved in database.
     *
     * @param courseId       only included for API consistency, not actually used
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param quizSubmission the quizSubmission to submit
     * @return the ResponseEntity with status 200 and body the result or the appropriate error code.
     */
    @PostMapping("/courses/{courseId}/exercises/{exerciseId}/submissions/preview")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Result> getResultForSubmission(@PathVariable Long courseId, @PathVariable Long exerciseId, @RequestBody QuizSubmission quizSubmission) {
        log.debug("REST request to submit QuizSubmission for preview : {}", quizSubmission);

        if (quizSubmission.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new quizSubmission cannot already have an ID.")).body(null);
        }

        QuizExercise quizExercise = quizExerciseService.findOneWithQuestions(exerciseId);
        if (quizExercise == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        if (!quizExerciseService.userHasTAPermissions(quizExercise)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // update submission
        quizSubmission.setSubmitted(true);
        quizSubmission.setType(SubmissionType.MANUAL);
        quizSubmission.calculateAndUpdateScores(quizExercise);

        // create Participation stub
        Participation participation = new Participation().exercise(quizExercise);

        // create result
        Result result = new Result().participation(participation).submission(quizSubmission);
        result.setRated(false);
        result.setCompletionDate(ZonedDateTime.now());
        // calculate score and update result accordingly
        result.evaluateSubmission();

        return ResponseEntity.ok(result);
    }

    /**
     * GET  /quiz-submissions : get all the quizSubmissions.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of quizSubmissions in body
     */
    @GetMapping("/quiz-submissions")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Timed
    public List<QuizSubmission> getAllQuizSubmissions() {
        log.debug("REST request to get all QuizSubmissions");
        return quizSubmissionService.findAll();
    }

    /**
     * GET  /quiz-submissions/:id : get the "id" quizSubmission.
     *
     * @param id the id of the quizSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the quizSubmission, or with status 404 (Not Found)
     */
    @GetMapping("/quiz-submissions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Timed
    public ResponseEntity<QuizSubmission> getQuizSubmission(@PathVariable Long id) {
        log.debug("REST request to get QuizSubmission : {}", id);
        QuizSubmission quizSubmission = quizSubmissionService.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(quizSubmission));
    }

    /**
     * DELETE  /quiz-submissions/:id : delete the "id" quizSubmission.
     *
     * @param id the id of the quizSubmission to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/quiz-submissions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Timed
    public ResponseEntity<Void> deleteQuizSubmission(@PathVariable Long id) {
        log.debug("REST request to delete QuizSubmission : {}", id);
        quizSubmissionService.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
