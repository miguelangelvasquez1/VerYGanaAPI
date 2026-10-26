package com.verygana2.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.dtos.impactStory.StoryMediaResponseDTO;
import com.verygana2.models.ImpactStory.StoryMediaAsset;
import com.verygana2.testsupport.PublicUrlBuilders;

@DisplayName("ImpactStoryMapper")
class ImpactStoryMapperTest {

    private final ImpactStoryMapperImpl mapper = newMapper();

    private static ImpactStoryMapperImpl newMapper() {
        ImpactStoryMapperImpl impl = new ImpactStoryMapperImpl();
        ReflectionTestUtils.setField(impl, "publicUrlBuilder", PublicUrlBuilders.forDomain("cdn-beta.verygana.com"));
        return impl;
    }

    @Test
    @DisplayName("la URL se reconstruye en cada lectura desde objectKey, ignorando el publicUrl grabado")
    void mediaUrlIsRebuiltWithConfiguredCdn() {
        StoryMediaAsset media = new StoryMediaAsset();
        media.setObjectKey("x");
        media.setPublicUrl("https://cdn.verygana.com/public/x");

        StoryMediaResponseDTO dto = mapper.toMediaResponse(media);

        assertThat(dto.getPublicUrl()).isEqualTo("https://cdn-beta.verygana.com/public/x");
    }

    @Test
    @DisplayName("sin objectKey la URL es null")
    void mediaWithoutObjectKeyHasNullUrl() {
        StoryMediaAsset media = new StoryMediaAsset();
        media.setPublicUrl("https://cdn.verygana.com/public/x");

        assertThat(mapper.toMediaResponse(media).getPublicUrl()).isNull();
    }
}
