ALTER TABLE grading_results DROP CONSTRAINT grading_results_status_check;
ALTER TABLE grading_results ADD CONSTRAINT grading_results_status_check
    CHECK (status IN ('PENDING_GRADING','AUTO_GRADED','AI_SUGGESTED','TEACHER_GRADED'));
