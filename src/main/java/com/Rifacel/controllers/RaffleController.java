package com.Rifacel.controllers;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Rifacel.models.Raffle;
import com.Rifacel.models.Enums.RaffleState;
import com.Rifacel.services.interfaces.RaffleService;

@RestController
@RequestMapping("/raffles")
public class RaffleController {
    
    @Autowired
    private RaffleService raffleService;

    // Obtener la lista de rifas según el estado que pasen como argumento
    @GetMapping("/state/{raffleState}")
    public ResponseEntity<List<Raffle>> getByState(@PathVariable RaffleState raffleState){
        List<Raffle> foundRaffles = raffleService.getByState(raffleState);
        return ResponseEntity.ok(foundRaffles);
    }

    // Obtener la rifa que coincida con el nombre que le pasen por argumento
    @GetMapping("/name/{name}")
    public ResponseEntity<Raffle> getByName(@PathVariable String name){
        Raffle foundRaffle = raffleService.getByName(name);
        return ResponseEntity.ok(foundRaffle);
    }

    // Obtener la lista de rifas que hayan ocurrido antes de la fecha que pasen como argumento
    @GetMapping("/dateBefore/{date}")
    public ResponseEntity<List<Raffle>> getByEndDateBefore(@PathVariable LocalDateTime localDateTime){
        List<Raffle> foundRaffles = raffleService.getByEndDateBefore(localDateTime);
        return ResponseEntity.ok(foundRaffles);
    }  

    // si falla el metodo con la fecha intentar parsearlo con LocalDateTime.parse(date)
    // @GetMapping("/dateBefore")
    // public ResponseEntity<List<Raffle>> getByEndDateBefore(@RequestParam String date)

}