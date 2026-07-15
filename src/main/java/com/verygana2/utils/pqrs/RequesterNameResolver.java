package com.verygana2.utils.pqrs;

import org.springframework.stereotype.Component;

import com.verygana2.models.User;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.models.userDetails.GameDesignerDetails;
import com.verygana2.models.userDetails.UserDetails;

/**
 * Resuelve un nombre legible para el solicitante de un PQRS, sin importar su rol
 * (cada UserDetails guarda el nombre en un campo distinto).
 */
@Component
public class RequesterNameResolver {

    public String resolve(User user) {
        UserDetails details = user.getUserDetails();
        if (details instanceof ConsumerDetails consumer) {
            return consumer.getName() + " " + consumer.getLastName();
        }
        if (details instanceof CommercialDetails commercial) {
            return commercial.getCompanyName();
        }
        if (details instanceof GameDesignerDetails designer) {
            return designer.getName() + " " + designer.getLastName();
        }
        return user.getEmail();
    }
}
