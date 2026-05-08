package com.tinkermode.aggregation.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {

    /**
     * Health check endpoint required by the compose.yml health check and the
     * edge simulator's depends_on condition.
     */
    @GetMapping("/healthz")
    fun healthz(): ResponseEntity<String> = ResponseEntity.ok("ok")


}