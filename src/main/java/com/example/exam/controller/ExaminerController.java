package com.example.exam.controller;

import com.example.exam.model.Exam;
import com.example.exam.model.Question;
import com.example.exam.repository.ExamRepository;
import com.example.exam.repository.QuestionRepository;
import com.example.exam.service.SyllabusParsingService;
import com.example.exam.service.QuestionGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.exam.repository.ExamResultRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Controller
@RequestMapping("/examiner")
public class ExaminerController {

    @Autowired
    private SyllabusParsingService parsingService;

    @Autowired
    private QuestionGeneratorService generatorService;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private ExamResultRepository examResultRepository;

    @GetMapping("/dashboard")
    public String examinerDashboard(Model model) {
        long totalExams = examRepository.count();
        long totalQuestions = questionRepository.count();

        model.addAttribute("totalExams", totalExams);
        model.addAttribute("totalQuestions", totalQuestions);
        return "examiner/dashboard";
    }

    @GetMapping("/exams")
    public String managedExams(Model model) {
        model.addAttribute("exams", examRepository.findAll());
        return "examiner/manage_exams";
    }

    @GetMapping("/exam/add")
    public String showAddExamForm(Model model) {
        model.addAttribute("exam", new Exam());
        return "examiner/add_exam";
    }

    @PostMapping("/exam/add")
    public String addExam(@ModelAttribute Exam exam) {
        examRepository.save(exam);
        return "redirect:/examiner/exams";
    }

    @GetMapping("/generate-questions")
    public String showGenerateQuestionsPage(Model model) {
        model.addAttribute("exams", examRepository.findAll());
        return "examiner/generate_questions";
    }

    @PostMapping("/generate-questions")
    public String generateQuestions(@RequestParam(value = "syllabusFile", required = false) MultipartFile file,
            @RequestParam("examId") Long examId,
            @RequestParam("numQuestions") int numQuestions,
            @RequestParam("timeLimit") int timeLimit,
            @RequestParam("easyCount") int easyCount,
            @RequestParam("mediumCount") int mediumCount,
            @RequestParam("hardCount") int hardCount,
            @RequestParam(value = "specificTopic", required = false) String topic,
            @RequestParam(value = "useAdvancedAI", defaultValue = "false") boolean useAdvancedAI,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            long startTime = System.currentTimeMillis();
            System.out.println(">>> Request received for examiner question generation. Exam ID: " + examId + ", Topic: " + topic);
            
            String syllabusText = "";
            if (file != null && !file.isEmpty()) {
                syllabusText = parsingService.parseSyllabus(file);
                System.out.println(">>> Syllabus parsed. Length: " + syllabusText.length());
            }
            
            List<Question> generatedQuestions = generatorService.generateQuestions(syllabusText, topic, easyCount, mediumCount,
                    hardCount, useAdvancedAI);
            System.out.println(">>> Questions generated. Count: " + generatedQuestions.size());

            Exam exam = examRepository.findById(examId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid exam Id:" + examId));

            // Update exam time limit
            exam.setDurationInMinutes(timeLimit);
            examRepository.save(exam);

            // For demo: automatically save generated questions to the exam
            for (Question q : generatedQuestions) {
                q.setExam(exam);
                questionRepository.save(q);
            }

            long endTime = System.currentTimeMillis();
            System.out.println(">>> Examiner question generation and storage completed in " + (endTime - startTime) + "ms");
            
            redirectAttributes.addFlashAttribute("successMessage",
                    "Successfully generated " + generatedQuestions.size() + " questions from syllabus!");
            return "redirect:/examiner/exam/manage-questions/" + examId;

        } catch (Exception e) {
            System.err.println("Error generating questions: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("errorMessage", "Error parsing syllabus: " + e.getMessage());
            model.addAttribute("exams", examRepository.findAll());
            return "examiner/generate_questions";
        }
    }

    @GetMapping("/exam/manage-questions/{examId}")
    public String showManageQuestionsPage(@PathVariable("examId") Long examId, Model model) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid exam Id:" + examId));
        model.addAttribute("exam", exam);
        model.addAttribute("questions", exam.getQuestions());
        return "examiner/manage_questions";
    }

    @GetMapping("/exam/{examId}/question/add")
    public String showAddQuestionForm(@PathVariable("examId") Long examId, Model model) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid exam Id:" + examId));
        Question question = new Question();
        question.setExam(exam);
        model.addAttribute("question", question);
        model.addAttribute("examId", examId);
        return "examiner/add_question";
    }

    @PostMapping("/exam/question/add")
    public String addQuestion(@ModelAttribute Question question,
            @RequestParam("examId") Long examId,
            RedirectAttributes redirectAttributes) {

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid exam Id:" + examId));

        question.setExam(exam);
        questionRepository.save(question);

        redirectAttributes.addFlashAttribute("successMessage", "Question added successfully!");

        return "redirect:/examiner/exam/" + examId + "/question/add";
    }

    @GetMapping("/question/edit/{questionId}")
    public String showEditQuestionForm(@PathVariable("questionId") Long questionId, Model model) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid question Id:" + questionId));
        model.addAttribute("question", question);
        return "examiner/edit_question";
    }

    @PostMapping("/question/update/{questionId}")
    public String updateQuestion(@PathVariable("questionId") Long questionId, @ModelAttribute Question updatedQuestion) {
        Question existingQuestion = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid question Id:" + questionId));

        existingQuestion.setText(updatedQuestion.getText());
        existingQuestion.setOption1(updatedQuestion.getOption1());
        existingQuestion.setOption2(updatedQuestion.getOption2());
        existingQuestion.setOption3(updatedQuestion.getOption3());
        existingQuestion.setOption4(updatedQuestion.getOption4());
        existingQuestion.setCorrectAnswer(updatedQuestion.getCorrectAnswer());
        existingQuestion.setMarks(updatedQuestion.getMarks());
        existingQuestion.setDifficulty(updatedQuestion.getDifficulty());

        questionRepository.save(existingQuestion);

        return "redirect:/examiner/exam/manage-questions/" + existingQuestion.getExam().getId();
    }

    @GetMapping("/exam/{examId}/question/delete/{questionId}")
    public String deleteQuestion(@PathVariable("examId") Long examId,
            @PathVariable("questionId") Long questionId,
            RedirectAttributes redirectAttributes) {
        try {
            questionRepository.deleteById(questionId);
            redirectAttributes.addFlashAttribute("successMessage", "Question deleted successfully.");
        } catch (DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Cannot delete this question. It has already been answered by one or more students.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An unexpected error occurred while trying to delete the question.");
        }
        return "redirect:/examiner/exam/manage-questions/" + examId;
    }

    @GetMapping("/exam/edit/{examId}")
    public String showEditExamForm(@PathVariable("examId") Long examId, Model model) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid exam Id:" + examId));
        model.addAttribute("exam", exam);
        return "examiner/edit_exam";
    }

    @PostMapping("/exam/update/{examId}")
    public String updateExam(@PathVariable("examId") Long examId, @ModelAttribute Exam updatedExam) {
        Exam existingExam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid exam Id:" + examId));

        existingExam.setTitle(updatedExam.getTitle());
        existingExam.setDurationInMinutes(updatedExam.getDurationInMinutes());

        examRepository.save(existingExam);
        return "redirect:/examiner/exams";
    }

    @GetMapping("/exam/delete/{examId}")
    @Transactional
    public String deleteExam(@PathVariable("examId") Long examId) {
        examRepository.findById(examId).ifPresent(exam -> {
            examResultRepository.deleteByExam(exam);
            examRepository.delete(exam);
        });
        return "redirect:/examiner/exams";
    }
}
