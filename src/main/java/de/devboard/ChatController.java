package de.devboard;

import de.devboard.domain.Feedback;
import de.devboard.domain.FeedbackRepository;
import de.devboard.domain.Qa;
import de.devboard.domain.QaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Controller
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final IngestionService ingestionService;
    private final ChatService chatService;
    private final QaRepository qaRepository;
    private final FeedbackRepository feedbackRepository;

    public ChatController(IngestionService ingestionService, ChatService chatService,
                          QaRepository qaRepository, FeedbackRepository feedbackRepository) {
        this.ingestionService = ingestionService;
        this.chatService = chatService;
        this.qaRepository = qaRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("history", qaRepository.findAllByOrderByCreatedAtDesc());
        return "index";
    }

    @PostMapping("/ingest")
    public String ingest(@RequestParam String folderPath, Model model) {
        try {
            IngestionService.IngestResult result = ingestionService.ingest(folderPath);
            model.addAttribute("ingestMessage",
                    "Ingested " + result.chunkCount() + " chunks from " + result.fileCount() + " files.");
        } catch (Exception e) {
            log.error("Ingestion failed", e);
            model.addAttribute("ingestError", "Something went wrong: " + e.getMessage());
        }
        model.addAttribute("history", qaRepository.findAllByOrderByCreatedAtDesc());
        return "index";
    }

    @PostMapping("/ask")
    public String ask(@RequestParam String question, Model model) {
        try {
            ChatService.QaResult result = chatService.ask(question);
            model.addAttribute("qaResult", result);
        } catch (Exception e) {
            log.error("Ask failed", e);
            model.addAttribute("askError", "Something went wrong: " + e.getMessage());
        }
        model.addAttribute("history", qaRepository.findAllByOrderByCreatedAtDesc());
        return "index";
    }

    @PostMapping("/feedback")
    public String feedback(@RequestParam UUID qaId, @RequestParam boolean thumbsUp, Model model) {
        try {
            feedbackRepository.save(new Feedback(UUID.randomUUID(), qaId, thumbsUp));
        } catch (Exception e) {
            log.error("Feedback failed", e);
        }
        model.addAttribute("history", qaRepository.findAllByOrderByCreatedAtDesc());
        return "index";
    }
}
