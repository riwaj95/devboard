package de.devboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final IngestionService ingestionService;

    public ChatController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @GetMapping("/")
    public String index(Model model) {
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
        return "index";
    }
}
