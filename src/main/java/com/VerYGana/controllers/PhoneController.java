package com.VerYGana.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.VerYGana.models.Phone;
import com.VerYGana.services.interfaces.PhoneService;

@RestController
@RequestMapping("/phones")
public class PhoneController {
    
    @Autowired
    private PhoneService phoneService;

    // Obtener la lista de teléfonos que tengan estado "true", es decir que estén disponibles para sortear
    @GetMapping("/available")
    public ResponseEntity<List<Phone>> getByState(){
        List<Phone> foundPhones = phoneService.getByAvailabilityTrue();
        return ResponseEntity.ok(foundPhones);
    }

    // Obtener la lista de teléfonos que coincidan con la marca que pasan por argumento
    @GetMapping("/mark/{mark}")
    public ResponseEntity<List<Phone>> getByMarkContainingIgnoreCase (@PathVariable String mark){
        List<Phone> foundPhones = phoneService.getByMarkContainingIgnoreCase(mark);
        return ResponseEntity.ok(foundPhones);
    }

    // Obtener la lista de teléfonos que coincidan con la versión que pasan por argumento
    @GetMapping("/version/{version}")
    public ResponseEntity<List<Phone>> getByVersionContainingIgnoreCase (@PathVariable String version){
        List<Phone> foundPhones = phoneService.getByVersionContainingIgnoreCase(version);
        return ResponseEntity.ok(foundPhones);
    }
}
