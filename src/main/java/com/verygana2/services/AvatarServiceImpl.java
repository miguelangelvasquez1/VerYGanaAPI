package com.verygana2.services;

import java.time.Instant;
import java.util.List;

import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.exceptions.InvalidAvatarException;
import com.verygana2.models.Avatar;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.AvatarRepository;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.services.interfaces.AvatarService;
import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvatarServiceImpl implements AvatarService {

    private final AvatarRepository avatarRepository;
    private final ConsumerDetailsRepository consumerDetailsRepository;

    public AvatarServiceImpl(AvatarRepository avatarRepository, ConsumerDetailsRepository consumerDetailsRepository) {
        this.avatarRepository = avatarRepository;
        this.consumerDetailsRepository = consumerDetailsRepository;
    }

    @Override
    public Avatar getActiveAvatarOrThrow(Long avatarId) {
        if (avatarId == null) {
            throw new InvalidAvatarException("Avatar is required");
        }

        Avatar avatar = avatarRepository.findById(avatarId)
                .orElseThrow(() -> new InvalidAvatarException("Avatar inválido"));

        if (!avatar.isActive()) {
            throw new InvalidAvatarException("Avatar inactivo");
        }

        return avatar;
    }

    @Override
    public List<Avatar> listActiveAvatars() {
        return avatarRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    @Override
    @Transactional
    public EntityUpdatedResponseDTO updateConsumerAvatar(Long consumerId, Long avatarId) {
        ConsumerDetails consumer = consumerDetailsRepository.findById(consumerId)
                .orElseThrow(() -> new ObjectNotFoundException("Consumer with id: " + consumerId + " not found", ConsumerDetails.class));
        Avatar avatar = getActiveAvatarOrThrow(avatarId);
        consumer.setAvatar(avatar);
        consumerDetailsRepository.save(consumer);
        return EntityUpdatedResponseDTO.builder()
                .id(consumerId)
                .message("Avatar updated successfully")
                .timestamp(Instant.now())
                .build();
    }
}
