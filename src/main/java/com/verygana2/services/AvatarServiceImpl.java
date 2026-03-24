package com.verygana2.services;

import java.util.List;

import com.verygana2.exceptions.InvalidAvatarException;
import com.verygana2.models.Avatar;
import com.verygana2.repositories.AvatarRepository;
import com.verygana2.services.interfaces.AvatarService;
import org.springframework.stereotype.Service;

@Service
public class AvatarServiceImpl implements AvatarService {

    private final AvatarRepository avatarRepository;

    public AvatarServiceImpl(AvatarRepository avatarRepository) {
        this.avatarRepository = avatarRepository;
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
}
