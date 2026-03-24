package com.verygana2.services.interfaces;

import com.verygana2.models.Avatar;

import java.util.List;

public interface AvatarService {
    Avatar getActiveAvatarOrThrow(Long avatarId);
    List<Avatar> listActiveAvatars();
}