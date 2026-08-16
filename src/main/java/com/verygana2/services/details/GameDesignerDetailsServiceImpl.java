package com.verygana2.services.details;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.user.admin.gameDesigners.GameDesignerResponseDTO;
import com.verygana2.dtos.user.admin.gameDesigners.GameDesignerSummaryResponseDTO;
import com.verygana2.mappers.UserMapper;
import com.verygana2.models.enums.UserState;
import com.verygana2.repositories.details.GameDesignerDetailsRepository;
import com.verygana2.services.interfaces.details.GameDesignerDetailsService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GameDesignerDetailsServiceImpl implements GameDesignerDetailsService{
    private final GameDesignerDetailsRepository gameDesignerDetailsRepository;
    private final UserMapper userMapper;

    @Override
    public PagedResponse<GameDesignerSummaryResponseDTO> getGameDesigners(String search, UserState userState,
            Pageable pageable) {

            return PagedResponse.from(gameDesignerDetailsRepository.findGameDesigners(search, userState,
            pageable).map(userMapper::toGameDesignerSummaryResponseDTO));

    }

    @Override
    public GameDesignerResponseDTO getGameDesigner(UUID publicId) {
        
        return userMapper.toGameDesignerResponseDTO(gameDesignerDetailsRepository.findByPublicId(publicId).orElseThrow(() -> new EntityNotFoundException("Game designer with public id: " + publicId + " not found")));
    }

    
}
