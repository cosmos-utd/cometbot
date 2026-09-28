package com.cometbot.controller;

import com.cometbot.dto.DeadlineDto;
import com.cometbot.dto.SyllabusRequest;
import com.cometbot.model.Deadline;
import com.cometbot.model.Syllabus;
import com.cometbot.service.SyllabusService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/guilds/{guildId}")
public class SyllabusController {

    private final SyllabusService syllabusService;

    public SyllabusController(SyllabusService syllabusService) {
        this.syllabusService = syllabusService;
    }

    @PostMapping("/syllabus")
    @ResponseStatus(HttpStatus.CREATED)
    public List<DeadlineDto> setSyllabus(@PathVariable String guildId,
                                         @Valid @RequestBody SyllabusRequest request) {
        return syllabusService.setSyllabus(guildId, request.content())
                .stream().map(DeadlineDto::from).toList();
    }

    @PostMapping(path = "/syllabus/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<DeadlineDto> uploadSyllabus(@PathVariable String guildId,
                                            @RequestParam("file") MultipartFile file) throws IOException {
        return syllabusService.setSyllabusFromFile(
                        guildId, file.getOriginalFilename(), file.getContentType(), file.getBytes())
                .stream().map(DeadlineDto::from).toList();
    }

    @GetMapping(path = "/syllabus", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getSyllabus(@PathVariable String guildId) {
        Syllabus syllabus = syllabusService.getSyllabus(guildId);
        return syllabus.getContent();
    }

    @PostMapping("/scan")
    public List<DeadlineDto> rescan(@PathVariable String guildId) {
        List<Deadline> deadlines = syllabusService.rescan(guildId);
        return deadlines.stream().map(DeadlineDto::from).toList();
    }
}