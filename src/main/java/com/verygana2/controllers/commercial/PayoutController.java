package com.verygana2.controllers.commercial;

import org.springframework.web.bind.annotation.RestController;

import com.verygana2.services.interfaces.finance.PayoutService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PayoutController {
    
    private final PayoutService payoutService;


    
}
