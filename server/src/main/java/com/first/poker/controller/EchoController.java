package com.first.poker.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class EchoController {

    @MessageMapping("/echo")
    @SendTo("/topic/echo")
    public String echo(String message) {
        return "Server received: " + message;
    }
}
