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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    @GetMapping("/ping")
    public ResponseEntity<?> ping() {
        log.debug("Ping request received");
        return ResponseEntity.ok("pong");
    }

    @GetMapping
    @Operation(summary = "Retrieve All videos (Paginated)", description = "Retrieve a paginated list of all available videos in the system.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the videos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "400", description = "Invalid pagination request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public ResponseEntity<Page<VideoDTO>> getVideos(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        log.debug("Fetching videos with pagination - page: {}, size: {}", page, size);
        Page<VideoDTO> videoPage = service.findAll(PageRequest.of(page, size));
        return ResponseEntity.ok(videoPage);
    }

    @GetMapping("/filter")
    @Operation(summary = "Retrieve Videos by Filters", description = "Retrieve a list of videos filtered by userId, title, or completion status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully found the videos matching the filters", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = VideoDTO.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters", content = @Content),
            @ApiResponse(responseCode = "404", description = "No videos found matching the criteria", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public ResponseEntity<List<VideoDTO>> getVideos(@RequestParam(required = false) UUID id, @RequestParam(required = false) String title, @RequestParam(required = false) Boolean completed) {
        log.debug("Fetching videos with filters - id: {}, title: {}, completed: {}", id, title, completed);
        List<VideoDTO> videos = service.findAllWithFilters(id, title, completed);
        return ResponseEntity.ok(videos);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve a video by ID", description = "Fetch a video from the system using its unique ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the video", content = @Content(mediaType = "application/json", schema = @Schema(implementation = VideoDTO.class))),
            @ApiResponse(responseCode = "404", description = "Video not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public ResponseEntity<VideoDTO> getVideoById(@PathVariable UUID id) {
        log.debug("Fetch video By Id: [Id: {}]", id);
        return ResponseEntity.ok(service.findById(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Mark a video as completed", description = "Marks a video as completed by updating its completion status to 'true'.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully marked the video as completed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = VideoDTO.class))),
            @ApiResponse(responseCode = "404", description = "Video not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public ResponseEntity<VideoDTO> setCompleted(@PathVariable UUID id) {
        log.debug("video: setCompleted[Id: {}]", id);
        VideoDTO result = service.findById(id);
        result.setCompleted(true);
        VideoDTO saved = service.save(result);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().buildAndExpand(result.getId()).toUri();

        return ResponseEntity.status(HttpStatus.OK).location(location).body(saved);
    }

    @RequestMapping(method = {RequestMethod.POST, RequestMethod.PUT})
    @Operation(summary = "Create or Update a video", description = "Allows saving a new video or updating an existing video in the system.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully saved or updated the video", content = @Content(mediaType = "application/json", schema = @Schema(implementation = VideoDTO.class))),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public ResponseEntity<VideoDTO> saveVideo(@Valid @RequestBody VideoDTO video) {
        log.debug("save: [{}]", video.toString());
        VideoDTO result = service.save(video);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(result.getId()).toUri();
        return ResponseEntity.status(HttpStatus.OK).location(location).body(result);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a video by ID", description = "Deletes a video from the system using its unique ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully deleted the video", content = @Content(mediaType = "application/json", schema = @Schema(implementation = VideoDTO.class))),
            @ApiResponse(responseCode = "404", description = "Video not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public ResponseEntity<VideoDTO> deleteVideo(@PathVariable UUID id) {
        log.debug("Delete video: [Id: {}]", id);
        VideoDTO deleted = service.delete(id);
        return ResponseEntity.status(HttpStatus.OK).body(deleted);
    }
}