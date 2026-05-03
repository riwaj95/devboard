package de.devboard;

import de.devboard.domain.Feedback;
import de.devboard.domain.FeedbackRepository;
import de.devboard.domain.Qa;
import de.devboard.domain.QaRepository;
import de.devboard.domain.User;
import de.devboard.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
    private final UserRepository userRepository;
    private final CorpusStateService corpusStateService;

    public ChatController(IngestionService ingestionService, ChatService chatService,
                          QaRepository qaRepository, FeedbackRepository feedbackRepository,
                          UserRepository userRepository, CorpusStateService corpusStateService) {
        this.ingestionService = ingestionService;
        this.chatService = chatService;
        this.qaRepository = qaRepository;
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
        this.corpusStateService = corpusStateService;
    }

    @GetMapping("/")
    public String index(@AuthenticationPrincipal UserDetails principal, Model model) {
        User user = currentUser(principal);
        model.addAttribute("userEmail", user.getEmail());
        model.addAttribute("history", qaRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()));
        addCorpusState(model);
        return "index";
    }

    @PostMapping("/ingest")
    public String ingest(@RequestParam String folderPath,
                         @AuthenticationPrincipal UserDetails principal, Model model) {
        User user = currentUser(principal);
        try {
            IngestionService.IngestResult result = ingestionService.ingest(folderPath);
            corpusStateService.updateAfterIngest(folderPath, result.fileCount(), result.chunkCount(), user.getId());
            model.addAttribute("ingestMessage",
                    "Ingested " + result.chunkCount() + " chunks from " + result.fileCount() + " files.");
        } catch (Exception e) {
            log.error("Ingestion failed", e);
            model.addAttribute("ingestError", "Something went wrong: " + e.getMessage());
        }
        model.addAttribute("userEmail", user.getEmail());
        model.addAttribute("history", qaRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()));
        addCorpusState(model);
        return "index";
    }

    @PostMapping("/ask")
    public String ask(@RequestParam String question,
                      @AuthenticationPrincipal UserDetails principal, Model model) {
        User user = currentUser(principal);
        try {
            ChatService.QaResult result = chatService.ask(question, user.getId());
            model.addAttribute("qaResult", result);
        } catch (Exception e) {
            log.error("Ask failed", e);
            model.addAttribute("askError", "Something went wrong: " + e.getMessage());
        }
        model.addAttribute("userEmail", user.getEmail());
        model.addAttribute("history", qaRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()));
        addCorpusState(model);
        return "index";
    }

    @PostMapping("/feedback")
    public String feedback(@RequestParam UUID qaId, @RequestParam boolean thumbsUp,
                           @AuthenticationPrincipal UserDetails principal, Model model) {
        User user = currentUser(principal);
        try {
            feedbackRepository.save(new Feedback(UUID.randomUUID(), qaId, thumbsUp, user.getId()));
        } catch (Exception e) {
            log.error("Feedback failed", e);
        }
        model.addAttribute("userEmail", user.getEmail());
        model.addAttribute("history", qaRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()));
        addCorpusState(model);
        return "index";
    }

    private void addCorpusState(Model model) {
        corpusStateService.getCorpusState().ifPresent(state -> {
            model.addAttribute("corpusState", state);
            model.addAttribute("corpusAdminEmail", corpusStateService.getLastIngestedByEmail(state));
        });
    }

    private User currentUser(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in DB"));
    }
}
