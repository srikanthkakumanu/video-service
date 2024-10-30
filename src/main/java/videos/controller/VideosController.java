package videos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import videos.dto.VideoDTO;
import videos.service.VideoService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/videos")
public class VideosController {

    private final VideoService service;

    @Autowired
    public VideosController(VideoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Retrieve All videos")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Found zero or more videos",
                    content = {@Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = VideoDTO.class)))})})
    public ResponseEntity<List<VideoDTO>> getVideos() {
        log.debug("Fetch all videos");
        return ResponseEntity.status(HttpStatus.OK).body(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<VideoDTO> getVideoById(@PathVariable UUID id) {
        log.debug("Fetch video By Id: [Id: {}]", id);
        return ResponseEntity.ok(service.findById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<VideoDTO> setCompleted(@PathVariable UUID id) {
        log.debug("video: setCompleted[Id: {}]", id);
        VideoDTO result = service.findById(id);
        result.setCompleted(true);
        VideoDTO saved = service.save(result);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .buildAndExpand(result.getId())
                .toUri();

        return ResponseEntity
                .status(HttpStatus.OK)
                .location(location)
                .body(saved);
    }

    @RequestMapping(method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<VideoDTO> saveVideo(
            @Valid @RequestBody VideoDTO video) {

        log.debug("save: [{}]", video.toString());
        VideoDTO result = service.save(video);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.getId()).toUri();

        return ResponseEntity.status(HttpStatus.OK).location(location).body(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<VideoDTO> deleteVideo(@PathVariable UUID id) {

        // repository.delete(VideoBuilder.create().withId(id).build());
        // return ResponseEntity.noContent().build();
        log.debug("Delete video: [Id: {}]", id);
        VideoDTO deleted = service.delete(id);
        return ResponseEntity.status(HttpStatus.OK).body(deleted);
    }
}