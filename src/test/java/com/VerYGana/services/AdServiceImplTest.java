package com.VerYGana.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.ad.requests.AdCreateDTO;
import com.verygana2.dtos.ad.responses.AdResponseDTO;
import com.verygana2.exceptions.adsExceptions.AdNotFoundException;
import com.verygana2.exceptions.adsExceptions.DuplicateLikeException;
import com.verygana2.exceptions.adsExceptions.InsufficientBudgetException;
import com.verygana2.exceptions.adsExceptions.InvalidAdStateException;
import com.verygana2.mappers.AdMapper;
import com.verygana2.models.Category;
import com.verygana2.models.User;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.userDetails.AdvertiserDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.AdLikeRepository;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.TransactionRepository;
import com.verygana2.repositories.UserRepository;
import com.verygana2.services.AdServiceImpl;

@ExtendWith(MockitoExtension.class)
class AdServiceImplTest {

    @Mock
    private AdRepository adRepository;

    @Mock
    private AdLikeRepository adLikeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AdMapper adMapper;

    @InjectMocks
    private AdServiceImpl adService;

    private User advertiser;
    private User user;
    private Ad ad;
    private AdCreateDTO AdCreateDTO;

    @BeforeEach
    void setUp() {
        // Configurar advertiser
        advertiser = new User();
        advertiser.setId(1L);
        advertiser.setEmail("advertiser@test.com");
        advertiser.getWallet().setBalance(BigDecimal.valueOf(1000));

        // Configurar usuario
        user = new User();
        user.setId(2L);
        user.setEmail("user@test.com");
        ((ConsumerDetails) user.getUserDetails()).setName("Test User");
        user.getWallet().setBalance(BigDecimal.ZERO);

        // Configurar anuncio, hay que inyectar el service de categorias para crear un anuncio con al menos una categoria
        ad = Ad.builder()
            .id(1L)
            .title("Test Ad")
            .description("Test Description")
            .rewardPerLike(BigDecimal.valueOf(0.50))
            .maxLikes(100)
            .currentLikes(0)
            .totalBudget(BigDecimal.valueOf(50))
            .spentBudget(BigDecimal.ZERO)
            .status(AdStatus.APPROVED)
            .advertiser((AdvertiserDetails)advertiser.getUserDetails())
            .createdAt(LocalDateTime.now())
            .categories(List.of(new Category()))
            .build();

        // Configurar DTO de creación, hay que inyectar el service de categorias para crear un anuncio con al menos una categoria
        AdCreateDTO = com.verygana2.dtos.ad.requests.AdCreateDTO.builder()
            .title("Test Ad")
            .description("Test Description")
            .rewardPerLike(BigDecimal.valueOf(0.50))
            .maxLikes(100)
            .totalBudget(BigDecimal.valueOf(50))
            .categories(List.of(new Category()))
            .build();
    }

    @Test
    void testCreateAd_Success() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(advertiser));
        when(adRepository.existsByAdvertiserIdAndTitle(1L, "Test Ad"))
            .thenReturn(false);
        when(adMapper.toEntity(AdCreateDTO)).thenReturn(ad);
        when(adRepository.save(any(Ad.class))).thenReturn(ad);
        
        AdResponseDTO expectedDto = new AdResponseDTO();
        expectedDto.setId(1L);
        when(adMapper.toDto(ad)).thenReturn(expectedDto);

        // Act
        AdResponseDTO result = adService.createAd(AdCreateDTO, 1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(adRepository, times(1)).save(any(Ad.class));
    }

    @Test
    void testCreateAd_AdvertiserNotFound() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AdNotFoundException.class, () -> {
            adService.createAd(AdCreateDTO, 1L);
        });
    }

    @Test
    void testCreateAd_DuplicateTitle() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(advertiser));
        when(adRepository.existsByAdvertiserIdAndTitle(1L, "Test Ad"))
            .thenReturn(true);

        // Act & Assert
        assertThrows(InvalidAdStateException.class, () -> {
            adService.createAd(AdCreateDTO, 1L);
        });
    }

    @Test
    void testCreateAd_InsufficientBudget() {
        // Arrange
        AdCreateDTO.setTotalBudget(BigDecimal.valueOf(10)); // Menor al necesario
        when(userRepository.findById(1L)).thenReturn(Optional.of(advertiser));
        when(adRepository.existsByAdvertiserIdAndTitle(1L, "Test Ad"))
            .thenReturn(false);

        // Act & Assert
        assertThrows(InsufficientBudgetException.class, () -> {
            adService.createAd(AdCreateDTO, 1L);
        });
    }

    @Test
    void testProcessAdLike_Success() {
        // Arrange
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(adRepository.findById(1L)).thenReturn(Optional.of(ad));
        when(adRepository.hasUserSeenAd(2L, 1L)).thenReturn(false);
        when(adRepository.save(any(Ad.class))).thenReturn(ad);
        when(userRepository.save(any(User.class))).thenReturn(user);
        
        AdResponseDTO expectedDto = new AdResponseDTO();
        when(adMapper.toDto(ad)).thenReturn(expectedDto);

        // Act
        AdResponseDTO result = adService.processAdLike(1L, 2L, "127.0.0.1", "Test Agent");

        // Assert
        assertNotNull(result);
        verify(adLikeRepository, times(1)).save(any());
        verify(transactionRepository, times(1)).save(any());
        verify(userRepository, times(1)).save(user);
        assertEquals(BigDecimal.valueOf(0.50), user.getWallet().getBalance());
    }

    @Test
    void testProcessAdLike_DuplicateLike() {
        // Arrange
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(adRepository.findById(1L)).thenReturn(Optional.of(ad));
        when(adRepository.hasUserSeenAd(2L, 1L)).thenReturn(true);

        // Act & Assert
        assertThrows(DuplicateLikeException.class, () -> {
            adService.processAdLike(1L, 2L, "127.0.0.1", "Test Agent");
        });
    }

    @Test
    void testProcessAdLike_AdNotAvailable() {
        // Arrange
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(adRepository.findById(1L)).thenReturn(Optional.of(ad));
        when(adRepository.hasUserSeenAd(2L, 1L)).thenReturn(false);

        // Act & Assert
        assertThrows(InvalidAdStateException.class, () -> {
            adService.processAdLike(1L, 2L, "127.0.0.1", "Test Agent");
        });
    }

    @Test
    void testActivateAd_Success() {
        // Arrange
        ad.setStatus(AdStatus.APPROVED);
        when(adRepository.findByIdAndAdvertiserId(1L, 1L)).thenReturn(Optional.of(ad));
        when(adRepository.save(any(Ad.class))).thenReturn(ad);
        
        AdResponseDTO expectedDto = new AdResponseDTO();
        when(adMapper.toDto(ad)).thenReturn(expectedDto);

        // Act
        AdResponseDTO result = adService.activateAd(1L, 1L);

        // Assert
        assertNotNull(result);
        assertEquals(AdStatus.ACTIVE, ad.getStatus());
        verify(adRepository, times(1)).save(ad);
    }

    @Test
    void testActivateAd_NotApproved() {
        // Arrange
        ad.setStatus(AdStatus.PENDING);
        when(adRepository.findByIdAndAdvertiserId(1L, 1L)).thenReturn(Optional.of(ad));

        // Act & Assert
        assertThrows(InvalidAdStateException.class, () -> {
            adService.activateAd(1L, 1L);
        });
    }

    @Test
    void testDeactivateAd_Success() {
        // Arrange
        when(adRepository.findByIdAndAdvertiserId(1L, 1L)).thenReturn(Optional.of(ad));
        when(adRepository.save(any(Ad.class))).thenReturn(ad);
        
        AdResponseDTO expectedDto = new AdResponseDTO();
        when(adMapper.toDto(ad)).thenReturn(expectedDto);

        // Act
        AdResponseDTO result = adService.deactivateAd(1L, 1L);

        // Assert
        assertNotNull(result);
        verify(adRepository, times(1)).save(ad);
    }

    @Test
    void testApproveAd_Success() {
        // Arrange
        ad.setStatus(AdStatus.PENDING);
        when(adRepository.findById(1L)).thenReturn(Optional.of(ad));
        when(adRepository.save(any(Ad.class))).thenReturn(ad);
        
        AdResponseDTO expectedDto = new AdResponseDTO();
        when(adMapper.toDto(ad)).thenReturn(expectedDto);

        // Act
        AdResponseDTO result = adService.approveAd(1L, 99L);

        // Assert
        assertNotNull(result);
        assertEquals(AdStatus.APPROVED, ad.getStatus());
        verify(adRepository, times(1)).save(ad);
    }

    @Test
    void testRejectAd_Success() {
        // Arrange
        ad.setStatus(AdStatus.PENDING);
        String reason = "Contenido inapropiado";
        when(adRepository.findById(1L)).thenReturn(Optional.of(ad));
        when(adRepository.save(any(Ad.class))).thenReturn(ad);
        
        AdResponseDTO expectedDto = new AdResponseDTO();
        when(adMapper.toDto(ad)).thenReturn(expectedDto);

        // Act
        AdResponseDTO result = adService.rejectAd(1L, reason, 99L);

        // Assert
        assertNotNull(result);
        assertEquals(AdStatus.REJECTED, ad.getStatus());
        assertEquals(reason, ad.getRejectionReason());
        verify(adRepository, times(1)).save(ad);
    }

    @Test
    void testGetAdById_Success() {
        // Arrange
        when(adRepository.findById(1L)).thenReturn(Optional.of(ad));
        
        AdResponseDTO expectedDto = new AdResponseDTO();
        expectedDto.setId(1L);
        when(adMapper.toDto(ad)).thenReturn(expectedDto);

        // Act
        AdResponseDTO result = adService.getAdById(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void testGetAdById_NotFound() {
        // Arrange
        when(adRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AdNotFoundException.class, () -> {
            adService.getAdById(1L);
        });
    }

    @Test
    void testCanAdReceiveLike_Success() {
        // Arrange
        when(adRepository.findById(1L)).thenReturn(Optional.of(ad));

        // Act
        boolean result = adService.canAdReceiveLike(1L);

        // Assert
        assertTrue(result);
    }

    @Test
    void testCanAdReceiveLike_AdCompleted() {
        // Arrange
        ad.setCurrentLikes(100); // Máximo alcanzado
        when(adRepository.findById(1L)).thenReturn(Optional.of(ad));

        // Act
        boolean result = adService.canAdReceiveLike(1L);

        // Assert
        assertFalse(result);
    }
}
