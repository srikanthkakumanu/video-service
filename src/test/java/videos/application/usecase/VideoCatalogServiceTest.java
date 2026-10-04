package videos.application.usecase;

import videos.domain.model.*;
import videos.domain.port.out.VideoStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoCatalogServiceTest {
    @Mock VideoStore store;
    @InjectMocks VideoCatalogService catalog;

    @Test
    void create_withoutId_shouldSaveAndUseJwtOwner() {
        var actor = new VideoActor(UUID.randomUUID(), false);
        when(store.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var saved = catalog.save(null, new VideoChanges("Example", null, UUID.randomUUID(), null, false), actor);
        assertThat(saved.userId()).isEqualTo(actor.userId());
        assertThat(saved.title()).isEqualTo("Example");
    }

    @Test
    void complete_byAnotherUser_shouldDenyWithoutSave() {
        var video = new Video(UUID.randomUUID(), null, null, "Example", null, UUID.randomUUID(), null, false);
        when(store.findById(video.id())).thenReturn(Optional.of(video));
        assertThatThrownBy(() -> catalog.complete(video.id(), new VideoActor(UUID.randomUUID(), false)))
                .isInstanceOf(VideoAccessDeniedException.class);
        verify(store, never()).save(any());
    }

    @Test
    void update_missingId_shouldNotCreateReplacement() {
        var id = UUID.randomUUID();
        when(store.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> catalog.save(id, new VideoChanges("Example", null, null, null, false),
                new VideoActor(UUID.randomUUID(), true))).isInstanceOf(VideoNotFoundException.class);
        verify(store, never()).save(any());
    }

    @Test
    void query_withZeroSize_shouldReject() {
        assertThatThrownBy(() -> new VideoQuery(null, null, null, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
