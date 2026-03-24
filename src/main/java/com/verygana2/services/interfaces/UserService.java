package com.verygana2.services.interfaces;

import com.verygana2.dtos.user.CommercialRegisterDTO;
import com.verygana2.dtos.user.ConsumerRegisterDTO;
import com.verygana2.dtos.user.SellerRegisterDTO;
import com.verygana2.models.User;

public interface UserService {
    User registerSeller(SellerRegisterDTO dto);
    User registerConsumer(ConsumerRegisterDTO dto);
    User registerCommercial(CommercialRegisterDTO dto);

    User getUserById(Long id);
    User getUserByEmail(String email);
    boolean emailExists(String email);
    boolean phoneExists(String phoneNumber);
    void deleteById(Long id);
}
